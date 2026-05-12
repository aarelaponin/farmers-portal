package global.govstack.formcreator.service;

import global.govstack.formcreator.constants.ApiConstants;
import global.govstack.formcreator.model.ApiCreationResult;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.BuilderDefinition;
import org.joget.apps.app.dao.BuilderDefinitionDao;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;
import java.util.UUID;

/**
 * Service class for creating API endpoints using Joget's Builder API.
 * This service handles both file system and database operations for API definitions.
 */
public class ApiBuilderService {

    private static final String CLASS_NAME = ApiBuilderService.class.getName();
    private final JsonProcessingService jsonProcessingService;

    /**
     * Constructor with JsonProcessingService dependency injection
     *
     * @param jsonProcessingService Service for JSON generation and processing
     */
    public ApiBuilderService(JsonProcessingService jsonProcessingService) {
        this.jsonProcessingService = jsonProcessingService;
    }

    /**
     * Creates an API endpoint for a form using Joget's dual storage pattern.
     * This method creates both the file system and database entries required for the API to be visible in Joget UI.
     *
     * @param formId The form ID to create the API endpoint for
     * @param apiName The display name for the API endpoint
     * @param appDef The application definition containing the target app
     * @return ApiCreationResult indicating success or failure with the API ID
     */
    public ApiCreationResult createApiEndpoint(String formId, String apiName, AppDefinition appDef) {
        String apiId = null;
        try {
            LogUtil.info(CLASS_NAME, "Creating API endpoint for form: " + formId);

            // Generate UUID for API
            String apiUuid = UUID.randomUUID().toString();
            apiId = ApiConstants.IdPrefixes.API + apiUuid;

            // Generate API definition JSON
            String apiJson = jsonProcessingService.generateApiDefinitionJson(formId, apiName, apiUuid);

            if (apiJson == null) {
                LogUtil.error(CLASS_NAME, null, "Failed to generate API JSON for form: " + formId);
                return ApiCreationResult.error("API JSON generation failed - generateApiDefinitionJson returned null");
            }

            // Step 1: Write API definition file to file system
            String jogetDir = System.getProperty("user.dir");
            String apiDir = jogetDir + ApiConstants.Paths.APP_SRC + "/" + appDef.getAppId() + "/" +
                           appDef.getAppId() + "_" + appDef.getVersion() + ApiConstants.Paths.BUILDER_DIR + ApiConstants.Paths.API_DIR;

            // Create directory if it doesn't exist
            File apiDirectory = new File(apiDir);
            if (!apiDirectory.exists()) {
                boolean created = apiDirectory.mkdirs();
                if (!created) {
                    LogUtil.error(CLASS_NAME, null, "Failed to create API directory: " + apiDir);
                    return ApiCreationResult.error("Failed to create API directory: " + apiDir);
                }
                LogUtil.info(CLASS_NAME, "API directory created at: " + apiDir);
            }

            // Write API definition file
            String apiFilePath = apiDir + "/" + apiId + ApiConstants.Paths.JSON_EXTENSION;
            try (FileWriter fileWriter = new FileWriter(apiFilePath)) {
                fileWriter.write(apiJson);
            }
            LogUtil.info(CLASS_NAME, "API file created at: " + apiFilePath);

            // Step 2: Save API definition to database using BuilderDefinitionDao
            BuilderDefinitionDao builderDefDao = (BuilderDefinitionDao)
                AppUtil.getApplicationContext().getBean(ApiConstants.BeanNames.BUILDER_DEFINITION_DAO);

            if (builderDefDao == null) {
                LogUtil.error(CLASS_NAME, null, "BuilderDefinitionDao not available - cannot save API to database");
                return ApiCreationResult.error("BuilderDefinitionDao not available - API file created but not in database");
            }

            BuilderDefinition builderDef = new BuilderDefinition();
            builderDef.setAppId(appDef.getAppId());
            builderDef.setAppVersion(appDef.getVersion());
            builderDef.setId(apiId);
            builderDef.setName(apiName);
            builderDef.setType(ApiConstants.BuilderTypes.API);
            builderDef.setJson(apiJson);
            builderDef.setDateCreated(new Date());
            builderDef.setDateModified(new Date());
            builderDef.setAppDefinition(appDef);

            builderDefDao.add(builderDef);
            LogUtil.info(CLASS_NAME, "API definition saved to database with ID: " + apiId);

            LogUtil.info(CLASS_NAME, "SUCCESS: API endpoint created: " + apiId);
            LogUtil.info(CLASS_NAME, "API endpoint URL: /jw/api/" + appDef.getAppId() + "/" + appDef.getVersion() + "/form/" + formId);

            return ApiCreationResult.success(apiId);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error creating API endpoint: " + e.getMessage());
            return ApiCreationResult.error("Exception during API creation: " + e.getMessage());
        }
    }

    /**
     * Upsert an API endpoint identified by a stable analyst-facing
     * {@code code}. Used by the {@code POST /formcreator/apis} endpoint
     * to provision API definitions from the {@code mm_api} YAML section.
     *
     * <p>Stable id discipline: the analyst-facing {@code code}
     * (UPPER_SNAKE_CASE, e.g. {@code API_SUBSIDY_APP_2025_CITIZEN}) is used
     * directly as the {@code app_builder.id} suffix, giving a deterministic
     * id of {@code API-<code>}. Re-running the seeder with the same code
     * locates the existing row via {@link BuilderDefinitionDao#loadById}
     * and updates it in place, instead of inserting a parallel row with a
     * fresh UUID. Joget's API Builder validation only checks the
     * {@code "API-"} prefix, not UUID-shape, so this works without
     * touching any platform code.
     *
     * @param code     stable analyst-facing identifier (UPPER_SNAKE_CASE)
     * @param formId   target form id (must already exist as a Joget form)
     * @param apiName  display name for App Composer / API Builder UI
     * @param apiKind  one of {@code crud}, {@code read_only},
     *                 {@code submit_only}; controls the generated
     *                 {@code ENABLED_PATHS} string.
     * @param appDef   target application
     * @return {@link ApiCreationResult#success(String)} with the API id on
     *         success ({@code API-<code>}), or
     *         {@link ApiCreationResult#error(String)} on any failure.
     */
    public ApiCreationResult upsertApiEndpoint(String code, String formId, String apiName,
                                               String apiKind, AppDefinition appDef) {
        return upsertApiEndpoint(code, formId, apiName, apiKind, appDef, null, null);
    }

    /**
     * Upsert variant supporting CUSTOM plugin APIs. When {@code className}
     * is non-null, the generated JSON uses the caller-supplied className +
     * {@code enabledPaths} verbatim instead of the AppFormAPI form-bound
     * shape. {@code formId} can be null in this mode (custom plugins
     * aren't form-bound). Used for plugin-driven APIs like
     * {@code BudgetApi} where path semantics live in the plugin itself.
     */
    public ApiCreationResult upsertApiEndpoint(String code, String formId, String apiName,
                                               String apiKind, AppDefinition appDef,
                                               String customClassName, String customEnabledPaths) {
        try {
            if (code == null || code.isEmpty()) {
                return ApiCreationResult.error("code is required for upsert");
            }
            boolean isCustom = customClassName != null && !customClassName.isEmpty();
            if (!isCustom && (formId == null || formId.isEmpty())) {
                return ApiCreationResult.error("formId is required (or pass customClassName for plugin APIs)");
            }
            String apiId = ApiConstants.IdPrefixes.API + code;
            String apiJson = isCustom
                    ? jsonProcessingService.generateCustomApiDefinitionJson(
                            apiName, code, customClassName, customEnabledPaths)
                    : jsonProcessingService.generateApiDefinitionJson(
                            formId, apiName, code, apiKind);
            if (apiJson == null) {
                return ApiCreationResult.error("API JSON generation failed");
            }

            // File-system side — same dual-storage pattern the existing
            // create path uses. Joget reads from either the row OR the
            // file at startup; keeping them in sync keeps export/import
            // workflows working cleanly.
            String jogetDir = System.getProperty("user.dir");
            String apiDir = jogetDir + ApiConstants.Paths.APP_SRC + "/" + appDef.getAppId() + "/"
                          + appDef.getAppId() + "_" + appDef.getVersion()
                          + ApiConstants.Paths.BUILDER_DIR + ApiConstants.Paths.API_DIR;
            File apiDirectory = new File(apiDir);
            if (!apiDirectory.exists() && !apiDirectory.mkdirs()) {
                return ApiCreationResult.error("Failed to create API directory: " + apiDir);
            }
            String apiFilePath = apiDir + "/" + apiId + ApiConstants.Paths.JSON_EXTENSION;
            try (FileWriter fw = new FileWriter(apiFilePath)) {
                fw.write(apiJson);
            }

            // Database side — locate by id, then add() or update().
            BuilderDefinitionDao dao = (BuilderDefinitionDao)
                    AppUtil.getApplicationContext().getBean(ApiConstants.BeanNames.BUILDER_DEFINITION_DAO);
            if (dao == null) {
                return ApiCreationResult.error("BuilderDefinitionDao bean not available");
            }
            BuilderDefinition existing = dao.loadById(apiId, appDef);
            if (existing != null) {
                existing.setName(apiName);
                existing.setJson(apiJson);
                existing.setDateModified(new Date());
                dao.update(existing);
                LogUtil.info(CLASS_NAME, "API definition UPDATED: " + apiId);
            } else {
                BuilderDefinition fresh = new BuilderDefinition();
                fresh.setAppId(appDef.getAppId());
                fresh.setAppVersion(appDef.getVersion());
                fresh.setId(apiId);
                fresh.setName(apiName);
                fresh.setType(ApiConstants.BuilderTypes.API);
                fresh.setJson(apiJson);
                fresh.setDateCreated(new Date());
                fresh.setDateModified(new Date());
                fresh.setAppDefinition(appDef);
                dao.add(fresh);
                LogUtil.info(CLASS_NAME, "API definition INSERTED: " + apiId);
            }
            LogUtil.info(CLASS_NAME, "API endpoint URL: /jw/api/" + apiId.toLowerCase() + "/...");
            return ApiCreationResult.success(apiId);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error upserting API endpoint for code " + code);
            return ApiCreationResult.error("Exception during API upsert: " + e.getMessage());
        }
    }
}
