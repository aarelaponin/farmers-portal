package global.govstack.formcreator.service;

import global.govstack.formcreator.constants.ApiConstants;
import global.govstack.formcreator.exception.FormCreationException;
import global.govstack.formcreator.exception.ValidationException;
import global.govstack.formcreator.model.*;
import global.govstack.formcreator.util.RequestParserUtil;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.service.FormService;
import org.joget.commons.util.LogUtil;
import org.json.JSONObject;

/**
 * Service class that handles form creation business logic.
 * Orchestrates the creation of forms, API endpoints, and CRUD interfaces.
 *
 * Integrates with:
 * - FormDatabaseService for form registration
 * - ApiBuilderService for API endpoint creation
 * - CrudService for CRUD interface creation
 * - FormCreatorBootstrapService for initial setup
 */
public class FormCreationService {

    private static final String CLASS_NAME = FormCreationService.class.getName();

    // Services (lazily initialized)
    private FormDatabaseService formDatabaseService;
    private ApiBuilderService apiBuilderService;
    private CrudService crudService;
    private FormCreatorBootstrapService bootstrapService;
    private JsonProcessingService jsonProcessingService;

    /**
     * Process form creation request (accepts pre-parsed FormCreationRequest)
     *
     * @param appId Target application ID (optional)
     * @param appVersion Target application version (optional)
     * @param request Pre-parsed FormCreationRequest
     * @return JSON response with creation results
     */
    public JSONObject processFormCreationRequest(String appId, String appVersion, FormCreationRequest request) {
        try {
            LogUtil.info(CLASS_NAME, "Processing form creation request");

            // 1. Validate request
            validateRequest(request);

            // 2. Get or determine target application
            AppDefinition targetAppDef = getTargetApplication(appId, appVersion, request);

            // 3. Bootstrap check - ensure formCreator CRUD exists on first call
            ensureFormCreatorBootstrapped(targetAppDef);

            // 4. Create form and optional components
            FormCreationResponse response = createForm(request, targetAppDef);

            // 5. Return success response
            return response.toJSON();

        } catch (ValidationException e) {
            LogUtil.warn(CLASS_NAME, "Validation error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error processing form creation request");
            throw new FormCreationException("Form creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ensure formCreator CRUD is bootstrapped in target application
     */
    private void ensureFormCreatorBootstrapped(AppDefinition appDef) {
        try {
            FormCreatorBootstrapService bootstrap = getBootstrapService();

            if (!bootstrap.isFormCreatorCrudExists(appDef)) {
                LogUtil.info(CLASS_NAME, "formCreator CRUD not found, bootstrapping...");
                BootstrapResult result = bootstrap.ensureFormCreatorCrud(appDef);

                if (!result.isSuccess()) {
                    LogUtil.error(CLASS_NAME, null, "Bootstrap failed: " + result.getErrorMessage());
                    throw new FormCreationException("Failed to bootstrap formCreator: " + result.getErrorMessage());
                }

                if (result.wasAlreadyExists()) {
                    LogUtil.info(CLASS_NAME, "formCreator CRUD already existed");
                } else {
                    LogUtil.info(CLASS_NAME, "formCreator CRUD bootstrapped successfully");
                }
            } else {
                LogUtil.debug(CLASS_NAME, "formCreator CRUD already exists, skipping bootstrap");
            }
        } catch (FormCreationException e) {
            throw e;
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error during bootstrap check");
            throw new FormCreationException("Bootstrap check failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate request parameters
     *
     * @param request The request to validate
     * @throws ValidationException if validation fails
     */
    private void validateRequest(FormCreationRequest request) {
        LogUtil.debug(CLASS_NAME, "Validating request: " + request);

        // Validate required fields
        if (request.getFormId() == null || request.getFormId().trim().isEmpty()) {
            throw new ValidationException(ApiConstants.ValidationMessages.FORM_ID_REQUIRED);
        }

        if (request.getFormName() == null || request.getFormName().trim().isEmpty()) {
            throw new ValidationException(ApiConstants.ValidationMessages.FORM_NAME_REQUIRED);
        }

        if (request.getTableName() == null || request.getTableName().trim().isEmpty()) {
            throw new ValidationException(ApiConstants.ValidationMessages.TABLE_NAME_REQUIRED);
        }

        // Validate form definition is present
        RequestParserUtil.validateFormDefinitionPresent(request);

        // Validate form definition is valid JSON
        validateFormDefinitionJson(request.getFormDefinitionJson());

        LogUtil.info(CLASS_NAME, "Request validation passed");
    }

    /**
     * Validate form definition JSON
     *
     * @param formDefinitionJson The JSON to validate
     * @throws ValidationException if JSON is invalid
     */
    private void validateFormDefinitionJson(String formDefinitionJson) {
        if (formDefinitionJson == null || formDefinitionJson.trim().isEmpty()) {
            throw new ValidationException(ApiConstants.ValidationMessages.FORM_DEFINITION_REQUIRED);
        }

        try {
            // Validate it's valid JSON
            new JSONObject(formDefinitionJson);

            LogUtil.debug(CLASS_NAME, "Form definition JSON is valid");

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Invalid form definition JSON");
            throw new ValidationException(
                ApiConstants.ValidationMessages.INVALID_FORM_DEFINITION_JSON + ": " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Get target application definition
     *
     * @param appId Application ID from URL parameter
     * @param appVersion Application version from URL parameter
     * @param request The request containing optional target app fields
     * @return Target AppDefinition
     * @throws ValidationException if application not found
     */
    private AppDefinition getTargetApplication(String appId, String appVersion, FormCreationRequest request) {
        AppService appService = (AppService) AppUtil.getApplicationContext()
            .getBean(ApiConstants.BeanNames.APP_SERVICE);

        // Determine target app ID (priority: request field > URL param > current app)
        String targetAppId = request.getTargetAppId();
        if (targetAppId == null || targetAppId.trim().isEmpty()) {
            targetAppId = appId;
        }

        // Determine target app version (priority: request field > URL param > latest)
        String targetAppVersion = request.getTargetAppVersion();
        if (targetAppVersion == null || targetAppVersion.trim().isEmpty()) {
            targetAppVersion = appVersion;
        }

        try {
            AppDefinition appDef;

            if (targetAppId != null && !targetAppId.trim().isEmpty()) {
                // Load specific application
                appDef = appService.getAppDefinition(targetAppId, targetAppVersion);

                if (appDef == null) {
                    String errorMsg = "Target application not found: " + targetAppId;
                    if (targetAppVersion != null) {
                        errorMsg += " (version: " + targetAppVersion + ")";
                    }
                    throw new ValidationException(errorMsg);
                }

                LogUtil.info(CLASS_NAME, "Using target application: " + appDef.getAppId() +
                           " (version: " + appDef.getVersion() + ")");
            } else {
                // Use current application
                appDef = AppUtil.getCurrentAppDefinition();

                if (appDef == null) {
                    throw new ValidationException(
                        "No application context available. Please specify targetAppId."
                    );
                }

                LogUtil.info(CLASS_NAME, "Using current application: " + appDef.getAppId() +
                           " (version: " + appDef.getVersion() + ")");
            }

            return appDef;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading target application");
            throw new ValidationException("Failed to load target application: " + e.getMessage(), e);
        }
    }

    /**
     * Create form and optional components
     *
     * @param request The form creation request
     * @param appDef The target application definition
     * @return FormCreationResponse with results
     */
    private FormCreationResponse createForm(FormCreationRequest request, AppDefinition appDef) {
        LogUtil.info(CLASS_NAME, "Creating form: " + request.getFormId());

        FormCreationResponse response = FormCreationResponse.success(
            request.getFormId(),
            "Form creation initiated"
        );

        try {
            // STEP 1: Create form definition
            LogUtil.info(CLASS_NAME, "Step 1: Creating form definition...");
            InternalFormCreationResult formResult = createFormDefinition(request, appDef);

            if (!formResult.isSuccess()) {
                throw new FormCreationException("Failed to create form definition: " + formResult.getErrorMessage());
            }

            response.setFormId(request.getFormId());
            response.setMessage("Form created successfully");

            LogUtil.info(CLASS_NAME, "Form created successfully: " + request.getFormId());

            // STEP 2: Create API endpoint (if requested)
            if (request.isCreateApiEndpoint()) {
                LogUtil.info(CLASS_NAME, "Step 2: Creating API endpoint...");
                ApiCreationResult apiResult = createApiEndpoint(request, appDef);
                if (apiResult.isSuccess()) {
                    response.setApiId(apiResult.getApiId());
                    LogUtil.info(CLASS_NAME, "API endpoint created: " + apiResult.getApiId());
                } else {
                    LogUtil.warn(CLASS_NAME, "API endpoint creation failed: " + apiResult.getErrorMessage());
                }
            }

            // STEP 3: Create CRUD interface (if requested)
            if (request.isCreateCrud()) {
                LogUtil.info(CLASS_NAME, "Step 3: Creating CRUD interface...");
                CrudCreationResult crudResult = createCrudInterface(request, appDef);
                if (crudResult.isSuccess()) {
                    response.setDatalistId(crudResult.getDatalistId());
                    response.setUserviewId(crudResult.getUserviewId());
                    LogUtil.info(CLASS_NAME, "CRUD interface created: datalist=" + crudResult.getDatalistId() +
                               ", userview=" + crudResult.getUserviewId());
                } else {
                    LogUtil.warn(CLASS_NAME, "CRUD interface creation failed: " + crudResult.getErrorMessage());
                }
            }

            // Update final message
            StringBuilder messageBuilder = new StringBuilder("Form created successfully");
            if (response.getApiId() != null) {
                messageBuilder.append(" with API endpoint");
            }
            if (response.getDatalistId() != null) {
                messageBuilder.append(" and CRUD interface");
            }
            response.setMessage(messageBuilder.toString());

            return response;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error creating form components");
            throw new FormCreationException("Form creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create form definition in Joget using FormDatabaseService
     *
     * @param request The form creation request
     * @param appDef The target application
     * @return InternalFormCreationResult with success/failure
     */
    private InternalFormCreationResult createFormDefinition(FormCreationRequest request, AppDefinition appDef) {
        LogUtil.info(CLASS_NAME, "Creating form definition: " + request.getFormId());

        try {
            // Get services
            AppService appService = (AppService) AppUtil.getApplicationContext()
                .getBean(ApiConstants.BeanNames.APP_SERVICE);
            FormService formService = (FormService) AppUtil.getApplicationContext()
                .getBean(ApiConstants.BeanNames.FORM_SERVICE);

            // Parse form JSON
            Form formObject = parseFormJson(request.getFormDefinitionJson(), formService);

            // Register form in database using FormDatabaseService
            FormDatabaseService dbService = getFormDatabaseService();
            return dbService.registerFormDirectToDatabaseWithResult(
                appService,
                appDef,
                request.getFormId(),
                request.getFormName(),
                request.getTableName(),
                request.getFormDefinitionJson(),
                formObject
            );

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error creating form definition");
            throw new FormCreationException("Failed to create form definition: " + e.getMessage(), e);
        }
    }

    /**
     * Parse form JSON into Form object
     *
     * @param formJson Form definition JSON
     * @param formService Form service
     * @return Form object
     */
    private Form parseFormJson(String formJson, FormService formService) {
        try {
            // Parse JSON into Form object using FormService
            Form form = (Form) formService.createElementFromJson(formJson);

            if (form == null) {
                throw new FormCreationException("Failed to parse form JSON");
            }

            return form;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error parsing form JSON");
            throw new FormCreationException("Invalid form JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Create API endpoint for the form using ApiBuilderService
     *
     * @param request The form creation request
     * @param appDef The target application
     * @return ApiCreationResult with success/failure and API ID
     */
    private ApiCreationResult createApiEndpoint(FormCreationRequest request, AppDefinition appDef) {
        LogUtil.info(CLASS_NAME, "Creating API endpoint for form: " + request.getFormId());

        try {
            // Determine API name
            String apiName = request.getApiName();
            if (apiName == null || apiName.trim().isEmpty()) {
                apiName = request.getFormName() + ApiConstants.Defaults.DEFAULT_API_NAME_SUFFIX;
            }

            // Create API endpoint using ApiBuilderService
            ApiBuilderService apiService = getApiBuilderService();
            return apiService.createApiEndpoint(request.getFormId(), apiName, appDef);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error creating API endpoint");
            return ApiCreationResult.error("Exception during API creation: " + e.getMessage());
        }
    }

    /**
     * Create CRUD interface (datalist + userview) for the form using CrudService
     *
     * @param request The form creation request
     * @param appDef The target application
     * @return CrudCreationResult with success/failure and IDs
     */
    private CrudCreationResult createCrudInterface(FormCreationRequest request, AppDefinition appDef) {
        LogUtil.info(CLASS_NAME, "Creating CRUD interface for form: " + request.getFormId());

        try {
            // Create CRUD using CrudService
            CrudService crudSvc = getCrudService();
            return crudSvc.createCrud(
                request.getFormId(),
                request.getFormName(),
                appDef,
                request.getFormDefinitionJson()
            );

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error creating CRUD interface");
            return CrudCreationResult.error("Exception during CRUD creation: " + e.getMessage());
        }
    }

    // ===== Service Getters (lazy initialization) =====

    private FormDatabaseService getFormDatabaseService() {
        if (formDatabaseService == null) {
            formDatabaseService = new FormDatabaseService();
        }
        return formDatabaseService;
    }

    private ApiBuilderService getApiBuilderService() {
        if (apiBuilderService == null) {
            apiBuilderService = new ApiBuilderService(getJsonProcessingService());
        }
        return apiBuilderService;
    }

    private CrudService getCrudService() {
        if (crudService == null) {
            JsonProcessingService jsonSvc = getJsonProcessingService();
            DatalistService datalistSvc = new DatalistService(jsonSvc);
            UserviewService userviewSvc = new UserviewService(jsonSvc);
            crudService = new CrudService(datalistSvc, userviewSvc);
        }
        return crudService;
    }

    private FormCreatorBootstrapService getBootstrapService() {
        if (bootstrapService == null) {
            bootstrapService = new FormCreatorBootstrapService();
        }
        return bootstrapService;
    }

    private JsonProcessingService getJsonProcessingService() {
        if (jsonProcessingService == null) {
            jsonProcessingService = new JsonProcessingService();
        }
        return jsonProcessingService;
    }
}
