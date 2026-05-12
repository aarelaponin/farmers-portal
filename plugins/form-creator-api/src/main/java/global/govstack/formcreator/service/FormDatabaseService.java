package global.govstack.formcreator.service;

import global.govstack.formcreator.constants.ApiConstants;
import global.govstack.formcreator.model.InternalFormCreationResult;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.commons.util.LogUtil;

import java.util.Date;

/**
 * Service class for handling form definition operations using Joget APIs.
 * This service manages form registration using FormDefinitionDao.
 */
public class FormDatabaseService {

    private static final String CLASS_NAME = FormDatabaseService.class.getName();

    /**
     * Register a form using Joget's FormDefinitionDao API.
     * This method handles both INSERT and UPDATE operations.
     *
     * @param appService The application service
     * @param appDef The application definition
     * @param formId The form ID
     * @param formName The form name
     * @param tableName The database table name for form data
     * @param jsonContent The form definition JSON
     * @param formObject The parsed form object
     * @return true if registration was successful, false otherwise
     */
    public boolean registerFormDirectToDatabase(AppService appService, AppDefinition appDef, String formId,
                                                String formName, String tableName, String jsonContent, Form formObject) {
        try {
            LogUtil.info(CLASS_NAME, "Registering form using Joget FormDefinitionDao API");
            LogUtil.info(CLASS_NAME, "Form ID: " + formId + ", App: " + appDef.getAppId() + ", Version: " + appDef.getVersion());

            // Get FormDefinitionDao from Spring context
            FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext()
                .getBean(ApiConstants.BeanNames.FORM_DEFINITION_DAO);

            if (formDefinitionDao == null) {
                LogUtil.error(CLASS_NAME, null, "FormDefinitionDao bean not available");
                return false;
            }

            // Check if form already exists
            FormDefinition existingForm = formDefinitionDao.loadById(formId, appDef);

            FormDefinition formDef;
            if (existingForm != null) {
                // Update existing form
                LogUtil.info(CLASS_NAME, "Form already exists, updating: " + formId);
                formDef = existingForm;
                formDef.setName(formName);
                formDef.setTableName(tableName);
                formDef.setJson(jsonContent);
                formDef.setDateModified(new Date());
            } else {
                // Create new form definition
                LogUtil.info(CLASS_NAME, "Creating new form definition: " + formId);
                formDef = new FormDefinition();
                formDef.setId(formId);
                formDef.setAppDefinition(appDef);
                formDef.setName(formName);
                formDef.setTableName(tableName);
                formDef.setJson(jsonContent);
                formDef.setDateCreated(new Date());
                formDef.setDateModified(new Date());
            }

            // Save using DAO
            boolean saved = formDefinitionDao.add(formDef);

            if (saved) {
                LogUtil.info(CLASS_NAME, "Form definition saved successfully: " + formId);

                // Trigger table creation by loading form data
                forceTableCreation(formId, tableName, appDef, appService);

                LogUtil.info(CLASS_NAME, "SUCCESS: Form registration completed");
                return true;
            } else {
                LogUtil.error(CLASS_NAME, null, "FormDefinitionDao.add() returned false for: " + formId);
                return false;
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Form registration failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Register a form, returning a Result object for explicit error handling.
     */
    public InternalFormCreationResult registerFormDirectToDatabaseWithResult(AppService appService, AppDefinition appDef,
                                                                     String formId, String formName, String tableName,
                                                                     String jsonContent, Form formObject) {
        try {
            LogUtil.info(CLASS_NAME, "Starting form registration with Result pattern for: " + formId);

            boolean success = registerFormDirectToDatabase(appService, appDef, formId, formName, tableName, jsonContent, formObject);

            if (success) {
                LogUtil.info(CLASS_NAME, "Form registration succeeded: " + formId);
                return InternalFormCreationResult.success(formId);
            } else {
                LogUtil.error(CLASS_NAME, null, "Form registration failed for: " + formId);
                return InternalFormCreationResult.error(
                    InternalFormCreationResult.ErrorType.DATABASE_ERROR,
                    "Form registration returned false - check logs for details"
                );
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Exception during form registration: " + e.getMessage());

            String errorType;
            String errorMessage = e.getMessage();

            if (errorMessage != null && errorMessage.toLowerCase().contains("json")) {
                errorType = InternalFormCreationResult.ErrorType.JSON_INVALID;
            } else if (errorMessage != null && errorMessage.toLowerCase().contains("table")) {
                errorType = InternalFormCreationResult.ErrorType.TABLE_CREATION_ERROR;
            } else {
                errorType = InternalFormCreationResult.ErrorType.DATABASE_ERROR;
            }

            return InternalFormCreationResult.error(errorType, errorMessage != null ? errorMessage : "Unknown error occurred");
        }
    }

    /**
     * Force immediate table creation for a form using the official Joget pattern.
     * This triggers Joget to create the app_fd_* table for form data.
     */
    public void forceTableCreation(String formId, String tableName, AppDefinition appDef, AppService appService) {
        try {
            LogUtil.info(CLASS_NAME, "Forcing table creation for form: " + formId + " (table: " + tableName + ")");

            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext()
                .getBean(ApiConstants.BeanNames.FORM_DATA_DAO);

            if (formDataDao != null) {
                // Clear form table cache to ensure fresh schema detection
                try {
                    java.lang.reflect.Method clearCacheMethod = formDataDao.getClass().getMethod("clearFormTableCache", String.class);
                    clearCacheMethod.invoke(formDataDao, formId);
                    LogUtil.info(CLASS_NAME, "Cleared form table cache for: " + formId);
                } catch (Exception cacheEx) {
                    LogUtil.debug(CLASS_NAME, "Could not clear cache (non-critical): " + cacheEx.getMessage());
                }

                // Execute dummy load to trigger table creation
                String dummyKey = "xyz123_trigger_table_creation";
                try {
                    formDataDao.loadWithoutTransaction(formId, tableName, dummyKey);
                    LogUtil.info(CLASS_NAME, "Table creation triggered via loadWithoutTransaction");
                } catch (Exception loadEx) {
                    // This is expected if the table doesn't exist yet - the load will create it
                    LogUtil.debug(CLASS_NAME, "Load triggered table creation: " + loadEx.getMessage());
                }

                // Verify table was created
                try {
                    String actualTable = appService.getFormTableName(appDef, formId);
                    if (actualTable != null && !actualTable.isEmpty()) {
                        LogUtil.info(CLASS_NAME, "SUCCESS: Form table verified: " + actualTable);
                    } else {
                        LogUtil.warn(CLASS_NAME, "Could not verify table creation for: " + formId);
                    }
                } catch (Exception verifyEx) {
                    LogUtil.warn(CLASS_NAME, "Table verification failed: " + verifyEx.getMessage());
                }
            } else {
                LogUtil.warn(CLASS_NAME, "FormDataDao bean not available - cannot force table creation");
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Table creation failed: " + e.getMessage());
        }
    }
}
