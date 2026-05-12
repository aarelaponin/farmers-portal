package global.govstack.formcreator.service;

import global.govstack.formcreator.constants.ApiConstants;
import global.govstack.formcreator.model.BootstrapResult;
import global.govstack.formcreator.model.CrudCreationResult;
import global.govstack.formcreator.model.InternalFormCreationResult;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.service.FormService;
import org.joget.commons.util.LogUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Service for bootstrapping the formCreator CRUD on first API invocation.
 * This ensures the formCreator form and its associated CRUD interface exist
 * in the target application before processing any form creation requests.
 */
public class FormCreatorBootstrapService {

    private static final String CLASS_NAME = FormCreatorBootstrapService.class.getName();
    private static final String FORM_CREATOR_ID = "formCreator";
    private static final String FORM_CREATOR_TABLE = "form_creator";
    private static final String FORM_CREATOR_NAME = "Form Creator";
    private static final String FORM_CREATOR_JSON_PATH = "/forms/formCreator.json";

    /**
     * Check if formCreator CRUD exists in the given application
     *
     * @param appDef The application definition to check
     * @return true if formCreator form exists, false otherwise
     */
    public boolean isFormCreatorCrudExists(AppDefinition appDef) {
        try {
            LogUtil.info(CLASS_NAME, "Checking if formCreator CRUD exists in app: " + appDef.getAppId());

            // Check if formCreator form exists
            FormDefinitionDao formDefDao = (FormDefinitionDao)
                AppUtil.getApplicationContext().getBean(ApiConstants.BeanNames.FORM_DEFINITION_DAO);

            if (formDefDao != null) {
                FormDefinition formDef = formDefDao.loadById(FORM_CREATOR_ID, appDef);
                if (formDef != null) {
                    LogUtil.info(CLASS_NAME, "formCreator form exists in app: " + appDef.getAppId());
                    return true;
                }
            }

            LogUtil.info(CLASS_NAME, "formCreator form does NOT exist in app: " + appDef.getAppId());
            return false;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error checking formCreator existence: " + e.getMessage());
            return false;
        }
    }

    /**
     * Ensure formCreator CRUD exists - create it if missing
     *
     * @param appDef The application definition
     * @return BootstrapResult indicating success or failure
     */
    public BootstrapResult ensureFormCreatorCrud(AppDefinition appDef) {
        try {
            LogUtil.info(CLASS_NAME, "Ensuring formCreator CRUD exists in app: " + appDef.getAppId());

            // First check if it already exists
            if (isFormCreatorCrudExists(appDef)) {
                LogUtil.info(CLASS_NAME, "formCreator CRUD already exists - skipping bootstrap");
                return BootstrapResult.alreadyExists();
            }

            // Load formCreator.json from resources
            String formCreatorJson = loadFormCreatorJson();
            if (formCreatorJson == null || formCreatorJson.isEmpty()) {
                LogUtil.error(CLASS_NAME, null, "Failed to load formCreator.json from resources");
                return BootstrapResult.error("Failed to load formCreator.json from resources");
            }

            LogUtil.info(CLASS_NAME, "Loaded formCreator.json (" + formCreatorJson.length() + " chars)");

            // Get services
            AppService appService = (AppService) AppUtil.getApplicationContext()
                .getBean(ApiConstants.BeanNames.APP_SERVICE);
            FormService formService = (FormService) AppUtil.getApplicationContext()
                .getBean(ApiConstants.BeanNames.FORM_SERVICE);

            // Step 1: Create the formCreator form
            LogUtil.info(CLASS_NAME, "Creating formCreator form...");

            // Parse form JSON
            Form formObject = (Form) formService.createElementFromJson(formCreatorJson);
            if (formObject == null) {
                LogUtil.error(CLASS_NAME, null, "Failed to parse formCreator JSON");
                return BootstrapResult.error("Failed to parse formCreator JSON");
            }

            // Register form in database
            FormDatabaseService formDbService = new FormDatabaseService();
            InternalFormCreationResult formResult = formDbService.registerFormDirectToDatabaseWithResult(
                appService, appDef, FORM_CREATOR_ID, FORM_CREATOR_NAME, FORM_CREATOR_TABLE, formCreatorJson, formObject
            );

            if (!formResult.isSuccess()) {
                LogUtil.error(CLASS_NAME, null, "Failed to create formCreator form: " + formResult.getErrorMessage());
                return BootstrapResult.error("Form creation failed: " + formResult.getErrorMessage());
            }

            LogUtil.info(CLASS_NAME, "formCreator form created successfully");

            // Step 2: Create CRUD (datalist + userview)
            LogUtil.info(CLASS_NAME, "Creating formCreator CRUD...");

            JsonProcessingService jsonService = new JsonProcessingService();
            DatalistService datalistService = new DatalistService(jsonService);
            UserviewService userviewService = new UserviewService(jsonService);
            CrudService crudService = new CrudService(datalistService, userviewService);

            CrudCreationResult crudResult = crudService.createCrud(
                FORM_CREATOR_ID, FORM_CREATOR_NAME, appDef, formCreatorJson
            );

            if (!crudResult.isSuccess()) {
                LogUtil.warn(CLASS_NAME, "CRUD creation failed (form still created): " + crudResult.getErrorMessage());
                // Return partial success - form exists but CRUD may not
                return BootstrapResult.success(FORM_CREATOR_ID, null, null);
            }

            LogUtil.info(CLASS_NAME, "formCreator CRUD created successfully");
            LogUtil.info(CLASS_NAME, "  - Datalist: " + crudResult.getDatalistId());
            LogUtil.info(CLASS_NAME, "  - Userview: " + crudResult.getUserviewId());

            return BootstrapResult.success(FORM_CREATOR_ID, crudResult.getDatalistId(), crudResult.getUserviewId());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error during formCreator bootstrap: " + e.getMessage());
            return BootstrapResult.error("Bootstrap exception: " + e.getMessage());
        }
    }

    /**
     * Load formCreator.json from classpath resources
     *
     * @return The JSON content as a string, or null if not found
     */
    private String loadFormCreatorJson() {
        try {
            InputStream inputStream = getClass().getResourceAsStream(FORM_CREATOR_JSON_PATH);
            if (inputStream == null) {
                LogUtil.error(CLASS_NAME, null, "Resource not found: " + FORM_CREATOR_JSON_PATH);
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading formCreator.json: " + e.getMessage());
            return null;
        }
    }
}
