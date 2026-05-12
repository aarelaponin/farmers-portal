package global.govstack.formcreator.lib;

import global.govstack.formcreator.constants.ApiConstants;
import global.govstack.formcreator.exception.ApiProcessingException;
import global.govstack.formcreator.model.FormCreationRequest;
import global.govstack.formcreator.service.FormCreationService;
import global.govstack.formcreator.util.ErrorResponseUtil;
import global.govstack.formcreator.util.MultipartRequestParser;
import global.govstack.formcreator.util.RequestParserUtil;
import global.govstack.formcreator.util.UserContextUtil;
import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;

/**
 * Form Creator Service Provider - API Plugin for creating Joget forms via REST API.
 *
 * This plugin provides endpoints for:
 * - Creating forms from JSON definitions
 * - Creating API endpoints for forms
 * - Creating CRUD interfaces (datalist + userview)
 *
 * Based on the architecture pattern from the processing-server plugin.
 */
public class FormCreatorServiceProvider extends ApiPluginAbstract implements PropertyEditable {

    private static final String CLASS_NAME = "global.govstack.formcreator.lib.FormCreatorServiceProvider";

    @Override
    public String getName() {
        return "formcreator-api";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "Form Creator API - Create Joget forms, API endpoints, and CRUD interfaces via REST API ("
            + global.govstack.formcreator.Build.STAMP + ")";
    }

    @Override
    public String getTag() {
        return "formcreator";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fa fa-file-code-o\"></i>";
    }

    @Override
    public String getLabel() {
        return "Form Creator API";
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
            getClass().getName(),
            "/properties/FormCreatorServiceProvider.json",
            null,
            true,
            null
        );
    }

    /**
     * Create a new form from JSON definition or multipart file upload
     *
     * Endpoint: POST /jw/api/formcreator/formcreator/forms
     *
     * Supports two content types:
     * 1. application/json - JSON body with formDefinition as string
     * 2. multipart/form-data - Form fields + file upload
     *
     * @param appId Target application ID (optional, uses current app if not specified)
     * @param appVersion Target application version (optional, uses latest if not specified)
     * @param request HttpServletRequest for accessing multipart data
     * @param requestBody JSON request body (for non-multipart requests)
     * @return ApiResponse with form creation result
     */
    @Operation(
        path = "/formcreator/forms",
        type = Operation.MethodType.POST,
        summary = "Create a new form from JSON definition or file upload",
        description = "Creates a Joget form based on provided JSON definition and metadata. " +
                      "Supports both JSON (application/json) and file upload (multipart/form-data). " +
                      "Optionally creates API endpoint and CRUD interface. " +
                      "Requires formId, formName, tableName, and formDefinition (or formDefinitionFile)."
    )
    @Responses({
        @Response(responseCode = 200, description = "Form created successfully"),
        @Response(responseCode = 400, description = "Invalid request - validation failed"),
        @Response(responseCode = 500, description = "Server error during form creation")
    })
    public ApiResponse createForm(
        @Param(value = "appId", required = false) String appId,
        @Param(value = "appVersion", required = false) String appVersion,
        @Param(value = "request", required = false) HttpServletRequest request,
        @Param(value = "body", required = false) String requestBody
    ) {
        LogUtil.info(CLASS_NAME, "=== Form Creation Request Received ===");
        LogUtil.info(CLASS_NAME, "Target App ID: " + (appId != null ? appId : "current"));
        LogUtil.info(CLASS_NAME, "Target App Version: " + (appVersion != null ? appVersion : "latest"));

        // Detect request type
        if (request != null && MultipartRequestParser.isMultipartRequest(request)) {
            LogUtil.info(CLASS_NAME, "Detected multipart/form-data request");
            return processMultipartRequest(appId, appVersion, request);
        } else {
            LogUtil.info(CLASS_NAME, "Detected application/json request");
            return processJsonRequest(appId, appVersion, requestBody);
        }
    }

    /**
     * Process JSON request (application/json)
     *
     * @param appId Target application ID
     * @param appVersion Target application version
     * @param requestBody JSON request body
     * @return ApiResponse with status code and response body
     */
    private ApiResponse processJsonRequest(String appId, String appVersion, String requestBody) {
        WorkflowUserManager workflowUserManager = getWorkflowUserManager();

        return UserContextUtil.executeAsSystemUser(workflowUserManager, () -> {
            try {
                // Log request details
                LogUtil.debug(CLASS_NAME, "Request body length: " +
                            (requestBody != null ? requestBody.length() : 0));

                // Parse JSON request
                FormCreationRequest request = RequestParserUtil.parseJsonRequest(requestBody);

                // Get FormCreationService
                FormCreationService creationService = new FormCreationService();

                // Process the request
                JSONObject response = creationService.processFormCreationRequest(appId, appVersion, request);

                LogUtil.info(CLASS_NAME, "=== Form Creation Successful ===");
                LogUtil.info(CLASS_NAME, "Response: " + response.toString());

                return new ApiResponse(ApiConstants.HttpStatus.OK, response.toString());

            } catch (ApiProcessingException e) {
                // Handle known processing exceptions with specific status codes
                return handleError(e.getStatusCode(), e.getErrorType(), e);

            } catch (Exception e) {
                // Handle unexpected exceptions
                return handleError(
                    ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiConstants.ErrorTypes.INTERNAL_SERVER_ERROR,
                    e
                );
            }
        });
    }

    /**
     * Process multipart request (multipart/form-data)
     *
     * @param appId Target application ID
     * @param appVersion Target application version
     * @param httpRequest HttpServletRequest
     * @return ApiResponse with status code and response body
     */
    private ApiResponse processMultipartRequest(String appId, String appVersion, HttpServletRequest httpRequest) {
        WorkflowUserManager workflowUserManager = getWorkflowUserManager();

        return UserContextUtil.executeAsSystemUser(workflowUserManager, () -> {
            try {
                // Parse multipart request
                LogUtil.info(CLASS_NAME, ">>> Parsing multipart request...");
                LogUtil.info(CLASS_NAME, "Content-Type: " + httpRequest.getContentType());
                LogUtil.info(CLASS_NAME, "Content-Length: " + httpRequest.getContentLength());

                MultipartRequestParser.MultipartData multipartData =
                    MultipartRequestParser.parseMultipartRequest(httpRequest);

                // Convert to FormCreationRequest
                FormCreationRequest request = RequestParserUtil.parseMultipartRequest(multipartData);

                LogUtil.info(CLASS_NAME, ">>> Multipart request parsed successfully!");
                LogUtil.info(CLASS_NAME, ">>> Request Details:");
                LogUtil.info(CLASS_NAME, "  - formId: " + request.getFormId());
                LogUtil.info(CLASS_NAME, "  - formName: " + request.getFormName());
                LogUtil.info(CLASS_NAME, "  - tableName: " + request.getTableName());
                LogUtil.info(CLASS_NAME, "  - targetAppId: " + request.getTargetAppId());
                LogUtil.info(CLASS_NAME, "  - targetAppVersion: " + request.getTargetAppVersion());
                LogUtil.info(CLASS_NAME, "  - createApiEndpoint: " + request.isCreateApiEndpoint());
                LogUtil.info(CLASS_NAME, "  - createCrud: " + request.isCreateCrud());

                if (request.getFormDefinitionFile() != null) {
                    LogUtil.info(CLASS_NAME, ">>> File Upload Detected:");
                    LogUtil.info(CLASS_NAME, "  - fileName: " + request.getFormDefinitionFileName());
                    LogUtil.info(CLASS_NAME, "  - fileSize: " + request.getFormDefinitionFile().length + " bytes");
                } else {
                    LogUtil.info(CLASS_NAME, ">>> No file uploaded (using inline JSON)");
                }

                // Get FormCreationService and process the request
                FormCreationService creationService = new FormCreationService();
                JSONObject response = creationService.processFormCreationRequest(appId, appVersion, request);

                LogUtil.info(CLASS_NAME, "=== Form Creation Successful ===");
                LogUtil.info(CLASS_NAME, "Response: " + response.toString());

                return new ApiResponse(ApiConstants.HttpStatus.OK, response.toString());

            } catch (ApiProcessingException e) {
                // Handle known processing exceptions with specific status codes
                return handleError(e.getStatusCode(), e.getErrorType(), e);

            } catch (Exception e) {
                // Handle unexpected exceptions
                LogUtil.error(CLASS_NAME, e, ">>> ERROR in processMultipartRequest");
                return handleError(
                    ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiConstants.ErrorTypes.INTERNAL_SERVER_ERROR,
                    e
                );
            }
        });
    }

    /**
     * Get the workflow user manager from application context
     *
     * @return WorkflowUserManager instance
     */
    protected WorkflowUserManager getWorkflowUserManager() {
        return (WorkflowUserManager) AppUtil.getApplicationContext()
            .getBean(ApiConstants.BeanNames.WORKFLOW_USER_MANAGER);
    }

    /**
     * Create a standardized error response
     *
     * @param statusCode HTTP status code
     * @param errorType Error type description
     * @param e The exception
     * @return ApiResponse with error details
     */
    protected ApiResponse handleError(int statusCode, String errorType, Exception e) {
        String rawMessage = e.getMessage();
        String logMessage = errorType + ": " + rawMessage;

        // Log based on severity
        logError(statusCode, e, logMessage);

        // Honesty over secrecy: surface the underlying exception's class +
        // message in the API response. The previous behaviour swallowed the
        // cause behind "An unexpected error occurred during form creation",
        // which forced callers to grep server logs to debug their own bad
        // requests. Stack traces are kept server-side; only class + message
        // (which the developer wrote anyway) leak out.
        String errorMessage;
        if (e instanceof ApiProcessingException) {
            errorMessage = rawMessage;  // deliberate, structured message
        } else {
            errorMessage = e.getClass().getSimpleName()
                         + (rawMessage != null && !rawMessage.isEmpty()
                                ? ": " + rawMessage
                                : "");
        }

        LogUtil.info(CLASS_NAME, "=== Form Creation Failed ===");
        LogUtil.info(CLASS_NAME, "Status Code: " + statusCode);
        LogUtil.info(CLASS_NAME, "Error Type: " + errorType);
        LogUtil.info(CLASS_NAME, "Error Message: " + errorMessage);

        return new ApiResponse(
            statusCode,
            ErrorResponseUtil.createErrorResponse(errorType, errorMessage)
        );
    }

    /**
     * Log messages based on status code severity
     *
     * Server errors (5xx) are logged as errors with stack traces.
     * Client errors (4xx) are logged as warnings.
     *
     * @param statusCode The HTTP status code
     * @param e The exception
     * @param message The message to log
     */
    protected void logError(int statusCode, Exception e, String message) {
        if (statusCode >= 500) {
            LogUtil.error(getClassName(), e, message);
        } else {
            LogUtil.warn(getClassName(), message);
        }
    }

    // -------------------------------------------------------------------
    //  Test-fixture seeding endpoints — bulk upsert + clear by business key
    //  See FixtureSeedService for semantics. HARD-RULE compliant: all DB
    //  access goes through Joget's FormDataDao (no raw SQL on app_fd_*).
    // -------------------------------------------------------------------

    /**
     * Bulk-upsert test fixture rows. Idempotent — each row is matched against
     * existing data by its business key (default {@code code}); if found,
     * updated in place (preserving Joget's UUID id); if not, inserted with a
     * new UUID. The fixture is JSON of the shape:
     *
     * <pre>
     * { "appId": "farmersPortal",
     *   "fixtures": [
     *     { "formId": "mm_service", "businessKey": "code",
     *       "rows": [ {"code":"INPUT_SUBSIDY_001","name":"...","entityInstitutionId":"MIN_AGRO"}, ... ] }
     *   ] }
     * </pre>
     */
    @Operation(
        path = "/formcreator/seed",
        type = Operation.MethodType.POST,
        summary = "Bulk-upsert test fixture rows by business key",
        description = "Upserts each row keyed by its business code; preserves Joget UUID id on update."
    )
    @Responses({
        @Response(responseCode = 200, description = "Seed results returned"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error during seeding")
    })
    public ApiResponse seedFixtures(
        @Param(value = "body", required = false) String requestBody
    ) {
        LogUtil.info(CLASS_NAME, "=== Fixture Seed Request Received ===");
        return UserContextUtil.executeAsSystemUser(getWorkflowUserManager(), () -> {
            try {
                if (requestBody == null || requestBody.isEmpty()) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("Request body is empty"));
                }
                org.json.JSONObject req = new org.json.JSONObject(requestBody);
                String appId = req.optString("appId", "");
                org.json.JSONArray fixtures = req.optJSONArray("fixtures");
                if (fixtures == null) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("Missing 'fixtures' array"));
                }

                global.govstack.formcreator.service.FixtureSeedService svc =
                    new global.govstack.formcreator.service.FixtureSeedService();
                org.json.JSONArray results = svc.seed(appId, fixtures);

                org.json.JSONObject resp = new org.json.JSONObject();
                resp.put("status",  "success");
                resp.put("results", results);
                LogUtil.info(CLASS_NAME, "Fixture seed completed: " + resp.toString());
                return new ApiResponse(ApiConstants.HttpStatus.OK, resp.toString());
            } catch (Exception e) {
                return handleError(
                    ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiConstants.ErrorTypes.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    /**
     * Wipe all rows from the listed forms in the order given. Caller is
     * responsible for ordering child tables (mm_field) before parents
     * (mm_screen, mm_service, ...) to avoid leaving orphan FKs mid-clear.
     *
     * <pre>{ "appId": "farmersPortal", "formIds": ["mm_field","mm_screen", ...] }</pre>
     */
    @Operation(
        path = "/formcreator/clear",
        type = Operation.MethodType.POST,
        summary = "Bulk-delete all rows from the listed forms",
        description = "Drops every row in each formId given, in caller-provided order."
    )
    @Responses({
        @Response(responseCode = 200, description = "Clear results returned"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error during clear")
    })
    public ApiResponse clearFixtures(
        @Param(value = "body", required = false) String requestBody
    ) {
        LogUtil.info(CLASS_NAME, "=== Fixture Clear Request Received ===");
        return UserContextUtil.executeAsSystemUser(getWorkflowUserManager(), () -> {
            try {
                if (requestBody == null || requestBody.isEmpty()) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("Request body is empty"));
                }
                org.json.JSONObject req = new org.json.JSONObject(requestBody);
                String appId = req.optString("appId", "");
                org.json.JSONArray formIds = req.optJSONArray("formIds");
                if (formIds == null) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("Missing 'formIds' array"));
                }

                global.govstack.formcreator.service.FixtureSeedService svc =
                    new global.govstack.formcreator.service.FixtureSeedService();
                org.json.JSONArray results = svc.clear(appId, formIds);

                org.json.JSONObject resp = new org.json.JSONObject();
                resp.put("status",  "success");
                resp.put("results", results);
                LogUtil.info(CLASS_NAME, "Fixture clear completed: " + resp.toString());
                return new ApiResponse(ApiConstants.HttpStatus.OK, resp.toString());
            } catch (Exception e) {
                return handleError(
                    ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiConstants.ErrorTypes.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    /**
     * Upsert a datalist definition by id. The canonical CI path for applying
     * versioned {@code _datalists/*.json} files via tooling rather than
     * App Composer paste. Same auth model as {@code /forms}.
     *
     * <pre>{
     *   "appId":        "farmersPortal",
     *   "datalistId":   "list_mm_determinant",
     *   "datalistName": "List: MM - Determinant",
     *   "json":         "{ ...full datalist definition JSON... }"
     * }</pre>
     */
    @Operation(
        path = "/formcreator/datalists",
        type = Operation.MethodType.POST,
        summary = "Upsert a datalist definition by id",
        description = "Inserts or updates a datalist's JSON via DatalistDefinitionDao. Idempotent."
    )
    @Responses({
        @Response(responseCode = 200, description = "Upsert result returned"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error during upsert")
    })
    public ApiResponse upsertDatalist(
        @Param(value = "body", required = false) String requestBody
    ) {
        LogUtil.info(CLASS_NAME, "=== Datalist Upsert Request Received ===");
        return UserContextUtil.executeAsSystemUser(getWorkflowUserManager(), () -> {
            try {
                if (requestBody == null || requestBody.isEmpty()) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("Request body is empty"));
                }
                org.json.JSONObject req = new org.json.JSONObject(requestBody);
                String appId        = req.optString("appId", "");
                String datalistId   = req.optString("datalistId", "");
                String datalistName = req.optString("datalistName", "");
                String json         = req.optString("json", "");
                if (appId.isEmpty() || datalistId.isEmpty() || json.isEmpty()) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("appId, datalistId, and json are required"));
                }

                org.joget.apps.app.service.AppService appService =
                    (org.joget.apps.app.service.AppService) org.joget.apps.app.service.AppUtil
                        .getApplicationContext().getBean(ApiConstants.BeanNames.APP_SERVICE);
                org.joget.apps.app.model.AppDefinition appDef = appService.getAppDefinition(appId, null);
                if (appDef == null) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("Application not found: " + appId));
                }

                global.govstack.formcreator.service.DatalistService svc =
                    new global.govstack.formcreator.service.DatalistService(
                        new global.govstack.formcreator.service.JsonProcessingService());
                String op = svc.upsertDatalist(appDef, datalistId, datalistName, json);

                org.json.JSONObject resp = new org.json.JSONObject();
                resp.put("status",     "success");
                resp.put("datalistId", datalistId);
                resp.put("operation",  op);
                LogUtil.info(CLASS_NAME, "Datalist upsert completed: " + resp.toString());
                return new ApiResponse(ApiConstants.HttpStatus.OK, resp.toString());
            } catch (Exception e) {
                return handleError(
                    ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiConstants.ErrorTypes.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    /**
     * Upsert a userview definition by id. Same shape and contract as
     * {@code /datalists}.
     *
     * <pre>{
     *   "appId":        "farmersPortal",
     *   "userviewId":   "v",
     *   "userviewName": "Farmers Registration",
     *   "json":         "{ ...full userview definition JSON... }"
     * }</pre>
     */
    @Operation(
        path = "/formcreator/userviews",
        type = Operation.MethodType.POST,
        summary = "Upsert a userview definition by id",
        description = "Inserts or updates a userview's JSON via UserviewDefinitionDao. Idempotent."
    )
    @Responses({
        @Response(responseCode = 200, description = "Upsert result returned"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error during upsert")
    })
    public ApiResponse upsertUserview(
        @Param(value = "body", required = false) String requestBody
    ) {
        LogUtil.info(CLASS_NAME, "=== Userview Upsert Request Received ===");
        return UserContextUtil.executeAsSystemUser(getWorkflowUserManager(), () -> {
            try {
                if (requestBody == null || requestBody.isEmpty()) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("Request body is empty"));
                }
                org.json.JSONObject req = new org.json.JSONObject(requestBody);
                String appId        = req.optString("appId", "");
                String userviewId   = req.optString("userviewId", "");
                String userviewName = req.optString("userviewName", "");
                String json         = req.optString("json", "");
                if (appId.isEmpty() || userviewId.isEmpty() || json.isEmpty()) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("appId, userviewId, and json are required"));
                }

                org.joget.apps.app.service.AppService appService =
                    (org.joget.apps.app.service.AppService) org.joget.apps.app.service.AppUtil
                        .getApplicationContext().getBean(ApiConstants.BeanNames.APP_SERVICE);
                org.joget.apps.app.model.AppDefinition appDef = appService.getAppDefinition(appId, null);
                if (appDef == null) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("Application not found: " + appId));
                }

                global.govstack.formcreator.service.UserviewService svc =
                    new global.govstack.formcreator.service.UserviewService(
                        new global.govstack.formcreator.service.JsonProcessingService());
                String op = svc.upsertUserview(appDef, userviewId, userviewName, json);

                org.json.JSONObject resp = new org.json.JSONObject();
                resp.put("status",     "success");
                resp.put("userviewId", userviewId);
                resp.put("operation",  op);
                LogUtil.info(CLASS_NAME, "Userview upsert completed: " + resp.toString());
                return new ApiResponse(ApiConstants.HttpStatus.OK, resp.toString());
            } catch (Exception e) {
                return handleError(
                    ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiConstants.ErrorTypes.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    /**
     * Upsert an API Builder API definition by stable analyst-facing
     * {@code code}. Provisions or updates the {@code app_builder} row that
     * exposes a Joget form via REST, equivalent to what App Composer's
     * "API Builder → New API" UI does — but driven from the {@code mm_api}
     * spec section so analysts can declare API endpoints alongside the
     * forms they generate.
     *
     * <p>Body shape:
     * <pre>{
     *   "appId":   "farmersPortal",
     *   "code":    "API_SUBSIDY_APP_2025_CITIZEN",
     *   "name":    "2025 Subsidy Application — Citizen Submit API",
     *   "formId":  "subsidyApplication2025",
     *   "apiKind": "crud"
     * }</pre>
     *
     * <p>Returns {@code apiId = "API-" + code} so the caller (typically
     * {@code _tooling/seed.py}) can record it in a generated endpoints
     * map for downstream test utilities.
     */
    @Operation(
        path = "/formcreator/apis",
        type = Operation.MethodType.POST,
        summary = "Upsert an API Builder API definition for an existing form",
        description = "Inserts or updates an app_builder API row via BuilderDefinitionDao. "
                    + "Idempotent: re-running with the same code updates the existing row "
                    + "rather than creating a duplicate. The form must already exist."
    )
    @Responses({
        @Response(responseCode = 200, description = "Upsert result returned"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error during upsert")
    })
    public ApiResponse upsertApi(
        @Param(value = "body", required = false) String requestBody
    ) {
        LogUtil.info(CLASS_NAME, "=== API Builder Upsert Request Received ===");
        return UserContextUtil.executeAsSystemUser(getWorkflowUserManager(), () -> {
            try {
                if (requestBody == null || requestBody.isEmpty()) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("Request body is empty"));
                }
                JSONObject req = new JSONObject(requestBody);
                String appId         = req.optString("appId", "");
                String code          = req.optString("code", "");
                String name          = req.optString("name", "");
                String formId        = req.optString("formId", "");
                String apiKind       = req.optString("apiKind", "crud");
                String customClass   = req.optString("className", "");
                String customPaths   = req.optString("enabledPaths", "");
                boolean isCustom     = !customClass.isEmpty();
                if (appId.isEmpty() || code.isEmpty()) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("appId and code are required"));
                }
                if (!isCustom && formId.isEmpty()) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("formId is required (or pass className+enabledPaths for plugin APIs)"));
                }
                if (isCustom && customPaths.isEmpty()) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("enabledPaths is required when className is set"));
                }
                if (name.isEmpty()) name = code; // fallback display name

                org.joget.apps.app.service.AppService appService =
                    (org.joget.apps.app.service.AppService) org.joget.apps.app.service.AppUtil
                        .getApplicationContext().getBean(ApiConstants.BeanNames.APP_SERVICE);
                org.joget.apps.app.model.AppDefinition appDef = appService.getAppDefinition(appId, null);
                if (appDef == null) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("Application not found: " + appId));
                }

                global.govstack.formcreator.service.ApiBuilderService svc =
                    new global.govstack.formcreator.service.ApiBuilderService(
                        new global.govstack.formcreator.service.JsonProcessingService());
                global.govstack.formcreator.model.ApiCreationResult result = isCustom
                    ? svc.upsertApiEndpoint(code, null, name, apiKind, appDef,
                                            customClass, customPaths)
                    : svc.upsertApiEndpoint(code, formId, name, apiKind, appDef);

                JSONObject resp = new JSONObject();
                if (result.isSuccess()) {
                    resp.put("status",  "success");
                    resp.put("apiId",   result.getApiId());
                    resp.put("code",    code);
                    resp.put("formId",  formId);
                    resp.put("apiKind", apiKind);
                    LogUtil.info(CLASS_NAME, "API upsert completed: " + resp.toString());
                    return new ApiResponse(ApiConstants.HttpStatus.OK, resp.toString());
                } else {
                    resp.put("status",       "error");
                    resp.put("errorMessage", result.getErrorMessage());
                    return new ApiResponse(ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR, resp.toString());
                }
            } catch (Exception e) {
                return handleError(
                    ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiConstants.ErrorTypes.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    /**
     * Bulk-delete API Builder API definitions by their app_builder.id (UUID).
     *
     * Endpoint: POST /jw/api/formcreator/formcreator/apis/delete
     *
     * Body: {"appId": "...", "apiIds": ["API-uuid-1", "API-uuid-2", ...]}
     *
     * Each id is passed to {@code BuilderDefinitionDao.delete(id, appDef)} —
     * the same DAO call the App Composer admin UI uses for "Delete API"
     * (see {@code CustomBuilderWebController.consoleBuilderDelete} in
     * jw-community/wflow-consoleweb). No raw SQL on app_builder; CLAUDE.md
     * HARD RULE preserved.
     *
     * Use case: cleaning up duplicates left by historical multi-run seeders
     * (the May 2026 IM Phase 3 audit found 56 stale APIs from three batched
     * runs of mm_api seeding, before /apis became upsert-by-code idempotent).
     *
     * Returns {"deleted": N, "failed": [{"id": "...", "error": "..."}], "status": "success"}.
     */
    @Operation(
        path = "/formcreator/apis/delete",
        type = Operation.MethodType.POST,
        summary = "Bulk-delete API Builder API definitions by id",
        description = "Calls BuilderDefinitionDao.delete() per id. Idempotent: ids that "
                    + "do not exist or have already been deleted are reported in 'skipped'."
    )
    @Responses({
        @Response(responseCode = 200, description = "Deletion result"),
        @Response(responseCode = 400, description = "Invalid request"),
        @Response(responseCode = 500, description = "Server error during delete")
    })
    public ApiResponse deleteApis(
        @Param(value = "body", required = false) String requestBody
    ) {
        LogUtil.info(CLASS_NAME, "=== API Builder Bulk Delete Request Received ===");
        return UserContextUtil.executeAsSystemUser(getWorkflowUserManager(), () -> {
            try {
                if (requestBody == null || requestBody.isEmpty()) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("Request body is empty"));
                }
                JSONObject req = new JSONObject(requestBody);
                String appId = req.optString("appId", "");
                org.json.JSONArray idsArr = req.optJSONArray("apiIds");
                if (appId.isEmpty() || idsArr == null || idsArr.length() == 0) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("appId and non-empty apiIds[] are required"));
                }

                org.joget.apps.app.service.AppService appService =
                    (org.joget.apps.app.service.AppService) org.joget.apps.app.service.AppUtil
                        .getApplicationContext().getBean(ApiConstants.BeanNames.APP_SERVICE);
                org.joget.apps.app.model.AppDefinition appDef = appService.getAppDefinition(appId, null);
                if (appDef == null) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("Application not found: " + appId));
                }

                org.joget.apps.app.dao.BuilderDefinitionDao dao =
                    (org.joget.apps.app.dao.BuilderDefinitionDao) org.joget.apps.app.service.AppUtil
                        .getApplicationContext().getBean("builderDefinitionDao");

                int deleted = 0;
                org.json.JSONArray failed = new org.json.JSONArray();
                for (int i = 0; i < idsArr.length(); i++) {
                    String id = idsArr.optString(i, "");
                    if (id == null || id.isEmpty()) continue;
                    try {
                        boolean ok = dao.delete(id, appDef);
                        if (ok) {
                            deleted++;
                        } else {
                            org.json.JSONObject f = new org.json.JSONObject();
                            f.put("id", id);
                            f.put("error", "delete returned false (row not found or in use)");
                            failed.put(f);
                        }
                    } catch (Exception ex) {
                        org.json.JSONObject f = new org.json.JSONObject();
                        f.put("id", id);
                        f.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
                        failed.put(f);
                    }
                }

                JSONObject resp = new JSONObject();
                resp.put("status", "success");
                resp.put("deleted", deleted);
                resp.put("failed", failed);
                LogUtil.info(CLASS_NAME, "API bulk-delete completed: deleted=" + deleted
                        + " failed=" + failed.length());
                return new ApiResponse(ApiConstants.HttpStatus.OK, resp.toString());
            } catch (Exception e) {
                return handleError(
                    ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiConstants.ErrorTypes.INTERNAL_SERVER_ERROR, e);
            }
        });
    }

    // /regbb/eval and /regbb/submit endpoints used to live here. Removed
    // because the cross-bundle reflection into reg-bb-engine's
    // RoutingEvaluator hit OSGi classloader-identity issues — different
    // ClassLoaders gave us different Class<?> objects for the same EvalContext
    // type, so getMethod(...) raised NoSuchMethodException. The proper home
    // for those endpoints is reg-bb-engine itself (RegBbEvalApi class +
    // dedicated API Builder definition), where the evaluator can be called
    // directly without reflection. See CLAUDE.md "Cross-bundle reflection".

    /**
     * Read a Joget datalist's rows as JSON, gated by API-Builder credentials
     * (so accessible to ROLE_USER, not just ROLE_ADMIN/ROLE_APPADMIN).
     *
     * Endpoint: GET /jw/api/formcreator/formcreator/data/list?appId=...&listId=...
     *
     * <p>Why this exists. Joget ships {@code FormListDataJsonController.formDataList}
     * at {@code /jw/web/json/data/list/{appId}/{listId}}. That URL is gated by
     * Spring Security's {@code <intercept-url pattern="/web/json/**"
     * access="ROLE_ADMIN, ROLE_APPADMIN" />} rule (jw-community
     * applicationContext.xml line 124). Non-admin users (district supervisors,
     * finance officers, etc.) get HTTP 403, so userview HtmlPages that fetch
     * dashboard data via that path break for everyone except admin.
     *
     * <p>The {@code /api/**} URL pattern is gated to {@code ROLE_USER,
     * ROLE_ANONYMOUS, ROLE_ADMIN, ROLE_APPADMIN} (line 77 of the same file).
     * API-Builder's own auth (api_id + api_key headers) takes over from there.
     * Routing dashboard XHRs through {@code /jw/api/formcreator/.../data/list}
     * therefore lets non-admin operators fetch the same data without granting
     * them admin escalation via {@code ROLE_ADMIN_GROUP}.
     *
     * <p>Response shape matches {@code FormListDataJsonController.formDataList}
     * verbatim — {@code {"total":N,"data":[{"col":"val",...},...]}} — so
     * client-side dashboard parsers don't need to change.
     *
     * <p>Implementation mirrors {@code FormListDataJsonController.formDataList}
     * (lines 232-272 of the jw-community source): load published app, load
     * datalist definition by id, deserialize via {@code DataListService.fromJson},
     * iterate columns × rows applying {@code DataListDecorator.formatColumn}.
     *
     * <p>Permission note. This endpoint does NOT enforce per-datalist
     * permissions. Stock Joget DX 8.1 doesn't either — {@code
     * FormListDataJsonController} has no row-level or list-level permission
     * check. It relies on Spring Security at the URL level. Authenticated
     * users with valid API credentials can read any datalist in the app, just
     * as admin can today. Per-list authorization is a separate concern,
     * tracked elsewhere.
     */
    @Operation(
        path = "/formcreator/data/list",
        type = Operation.MethodType.GET,
        summary = "Fetch a Joget datalist's rows as JSON (ROLE_USER-accessible)",
        description = "Companion to /jw/web/json/data/list but gated by API-Builder "
                    + "credentials, so userview HtmlPages can call it from non-admin "
                    + "user sessions. Returns the same {total, data[]} shape."
    )
    @Responses({
        @Response(responseCode = 200, description = "Datalist rows"),
        @Response(responseCode = 400, description = "Missing appId or listId"),
        @Response(responseCode = 404, description = "App or datalist not found"),
        @Response(responseCode = 500, description = "Server error during list load")
    })
    public ApiResponse readDatalist(
        @Param(value = "appId",  required = true)  String appId,
        @Param(value = "listId", required = true)  String listId,
        @Param(value = "start",  required = false) String startStr,
        @Param(value = "rows",   required = false) String rowsStr
    ) {
        WorkflowUserManager workflowUserManager = getWorkflowUserManager();
        return UserContextUtil.executeAsSystemUser(workflowUserManager, () -> {
            try {
                if (appId == null || appId.isEmpty() || listId == null || listId.isEmpty()) {
                    return handleError(ApiConstants.HttpStatus.BAD_REQUEST,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalArgumentException("appId and listId are required"));
                }
                String safeListId = org.joget.commons.util.SecurityUtil.validateStringInput(listId);

                org.joget.apps.app.service.AppService appService =
                    (org.joget.apps.app.service.AppService) AppUtil.getApplicationContext()
                        .getBean(ApiConstants.BeanNames.APP_SERVICE);
                org.joget.apps.app.model.AppDefinition appDef =
                    appService.getPublishedAppDefinition(appId);
                if (appDef == null) {
                    return handleError(ApiConstants.HttpStatus.NOT_FOUND,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalStateException("App not published: " + appId));
                }

                org.joget.apps.app.dao.DatalistDefinitionDao datalistDao =
                    (org.joget.apps.app.dao.DatalistDefinitionDao) AppUtil.getApplicationContext()
                        .getBean("datalistDefinitionDao");
                org.joget.apps.app.model.DatalistDefinition def =
                    datalistDao.loadById(safeListId, appDef);
                if (def == null) {
                    return handleError(ApiConstants.HttpStatus.NOT_FOUND,
                        ApiConstants.ErrorTypes.VALIDATION_ERROR,
                        new IllegalStateException("Datalist not found: " + safeListId));
                }

                org.joget.apps.datalist.service.DataListService dataListService =
                    (org.joget.apps.datalist.service.DataListService) AppUtil.getApplicationContext()
                        .getBean("dataListService");
                org.joget.apps.datalist.model.DataList dataList =
                    dataListService.fromJson(def.getJson());

                Integer start = null;
                Integer rows  = null;
                try { if (startStr != null && !startStr.isEmpty()) start = Integer.valueOf(startStr); } catch (NumberFormatException ignore) {}
                try { if (rowsStr  != null && !rowsStr.isEmpty())  rows  = Integer.valueOf(rowsStr);  } catch (NumberFormatException ignore) {}

                int total = dataList.getTotal();
                org.joget.apps.datalist.service.DataListDecorator decorator =
                    new org.joget.apps.datalist.service.DataListDecorator(dataList);
                org.joget.apps.datalist.model.DataListColumn[] columns = dataList.getColumns();
                org.joget.apps.datalist.model.DataListCollection results = dataList.getRows(rows, start);

                JSONObject out = new JSONObject();
                out.put("total", total);
                org.json.JSONArray data = new org.json.JSONArray();
                for (java.util.Iterator i = results.iterator(); i.hasNext(); ) {
                    java.util.Map row = (java.util.Map) i.next();
                    JSONObject rowObj = new JSONObject();
                    for (org.joget.apps.datalist.model.DataListColumn col : columns) {
                        String name = col.getName();
                        Object val = row.get(name);
                        String formatted = decorator.formatColumn(col, row, val);
                        rowObj.put(name, formatted);
                    }
                    data.put(rowObj);
                }
                out.put("data", data);
                return new ApiResponse(ApiConstants.HttpStatus.OK, out.toString());
            } catch (Exception e) {
                return handleError(
                    ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiConstants.ErrorTypes.INTERNAL_SERVER_ERROR, e);
            }
        });
    }
}
