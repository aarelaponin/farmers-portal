package global.govstack.registration.receiver.service;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.model.FormData;
import org.joget.commons.util.LogUtil;
import global.govstack.registration.receiver.exception.FormSubmissionException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manager to handle multi-form submissions for wizard-style forms
 * Each section saves to its own form/table
 */
public class MultiFormSubmissionManager {
    private static final String CLASS_NAME = MultiFormSubmissionManager.class.getName();
    private final AppService appService;
    private final String appId;
    private final String appVersion;

    public MultiFormSubmissionManager() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        this.appId = appDef.getId();
        this.appVersion = appDef.getVersion().toString();
        this.appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
    }

    /**
     * Save data to a specific form
     * @param formId The form ID (e.g., "farmerBasicInfo")
     * @param data The data to save
     * @param primaryKey The primary key (shared across all forms)
     * @return true if successful
     */
    public boolean saveToForm(String formId, Map<String, String> data, String primaryKey) throws FormSubmissionException {
        try {
            LogUtil.info(CLASS_NAME, "Saving to form: " + formId + " with primary key: " + primaryKey);

            // Create form data
            FormData formData = new FormData();
            formData.setPrimaryKeyValue(primaryKey);

            // Load the form
            Form form = appService.viewDataForm(
                appId,
                appVersion,
                formId,
                null, null, null,
                formData,
                "#",
                null
            );

            if (form == null) {
                throw new FormSubmissionException("Form not found: " + formId);
            }

            // Create FormRowSet with data
            FormRowSet rowSet = new FormRowSet();
            FormRow row = new FormRow();
            row.setId(primaryKey);

            // Add all fields
            for (Map.Entry<String, String> entry : data.entrySet()) {
                row.setProperty(entry.getKey(), entry.getValue());
            }

            rowSet.add(row);

            // Save the data
            FormRowSet result = appService.storeFormData(form, rowSet, primaryKey);

            if (result != null && !result.isEmpty()) {
                LogUtil.info(CLASS_NAME, "Successfully saved " + data.size() + " fields to form: " + formId);
                return true;
            } else {
                LogUtil.warn(CLASS_NAME, "No data returned after saving to form: " + formId);
                return false;
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error saving to form " + formId + ": " + e.getMessage());
            throw new FormSubmissionException("Failed to save to form " + formId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Create parent form record (main form that links all sub-forms)
     * @param parentFormId The parent form ID (e.g., "farmers")
     * @param primaryKey The primary key
     * @param parentReferenceFields List of field names that link to sub-forms (from YAML configuration)
     * @return true if successful
     */
    public boolean createParentRecord(String parentFormId, String primaryKey, java.util.List<String> parentReferenceFields) throws FormSubmissionException {
        try {
            LogUtil.info(CLASS_NAME, "Creating parent record in form: " + parentFormId + " with primary key: " + primaryKey);

            // Create parent record with reference fields that link to sub-forms
            Map<String, String> parentData = new HashMap<>();

            // Populate reference fields from YAML configuration
            // These fields should contain the primary key value to reference the sub-form records
            if (parentReferenceFields != null && !parentReferenceFields.isEmpty()) {
                for (String fieldName : parentReferenceFields) {
                    parentData.put(fieldName, primaryKey);
                    LogUtil.info(CLASS_NAME, "Added parent reference field: " + fieldName + " = " + primaryKey);
                }
            } else {
                LogUtil.warn(CLASS_NAME, "No parent reference fields configured - parent record will have no links to sub-forms");
            }

            LogUtil.info(CLASS_NAME, "Populating parent record with " + parentData.size() + " reference fields pointing to: " + primaryKey);

            return saveToForm(parentFormId, parentData, primaryKey);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error creating parent record: " + e.getMessage());
            throw new FormSubmissionException("Failed to create parent record: " + e.getMessage(), e);
        }
    }

    /**
     * Save data to multiple forms with the same primary key
     * @param formsData Map of formId to data
     * @param primaryKey The shared primary key
     * @return Map of formId to success status
     */
    public Map<String, Boolean> saveToMultipleForms(Map<String, Map<String, String>> formsData, String primaryKey) {
        Map<String, Boolean> results = new HashMap<>();

        for (Map.Entry<String, Map<String, String>> entry : formsData.entrySet()) {
            String formId = entry.getKey();
            Map<String, String> data = entry.getValue();

            try {
                boolean success = saveToForm(formId, data, primaryKey);
                results.put(formId, success);
            } catch (Exception e) {
                LogUtil.error(CLASS_NAME, e, "Failed to save to form " + formId);
                results.put(formId, false);
            }
        }

        return results;
    }
}