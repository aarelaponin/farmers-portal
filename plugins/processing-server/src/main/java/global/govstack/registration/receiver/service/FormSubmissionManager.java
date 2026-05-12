package global.govstack.registration.receiver.service;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.model.FormData;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import global.govstack.registration.receiver.exception.FormSubmissionException;
import global.govstack.registration.receiver.util.FormRowBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manager class to handle form submission, retrieval and update operations.
 * Updated to use FormRowBuilder for cleaner code.
 */
public class FormSubmissionManager {

    private final Form form;
    private final FormData formData;
    private final AppService appService;
    private final String formId;
    private final String appId;
    private final String appVersion;
    private static final String DEFAULT_FORM_URL = "#";

    /**
     * Constructor for the FormSubmissionManager
     *
     * @param formId The ID of the form to manage
     * @throws FormSubmissionException if there are issues initializing the form
     */
    public FormSubmissionManager(String formId) throws FormSubmissionException {
        this.formId = formId;
        try {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            this.appId = appDef.getId();
            this.appVersion = appDef.getVersion().toString();
            this.appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
            this.formData = new FormData();
            formData.setPrimaryKeyValue(UuidGenerator.getInstance().getUuid());

            this.form = loadForm(formData);
        } catch (Exception e) {
            throw new FormSubmissionException("Failed to initialize FormSubmissionManager: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to load a form with the specified form data
     *
     * @param data The form data to load into the form
     * @return The loaded form
     */
    private Form loadForm(FormData data) {
        return appService.viewDataForm(
                appId,
                appVersion,
                formId,
                null,  // saveButtonLabel
                null,  // submitButtonLabel
                null,  // cancelButtonLabel
                data,
                DEFAULT_FORM_URL,   // formUrl (required)
                null   // cancelUrl
        );
    }

    /**
     * Helper method to check if a string is null or empty
     *
     * @param str String to check
     * @return true if the string is null or empty
     */
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Validates input fields before saving
     *
     * @param inputFields The input fields to validate
     * @throws FormSubmissionException if validation fails
     */
    private void validateInputFields(Map<String, String> inputFields) throws FormSubmissionException {
        if (inputFields == null || inputFields.isEmpty()) {
            throw new FormSubmissionException("Input fields cannot be empty");
        }

        // Add additional validation rules as needed
        // For example, check required fields:
        // if (!inputFields.containsKey("requiredField") || isEmpty(inputFields.get("requiredField"))) {
        //    throw new FormSubmissionException("Required field 'requiredField' is missing or empty");
        // }
    }

    /**
     * Saves form data
     *
     * @param inputFields Map containing form field name-value pairs
     * @return The ID of the saved form data
     * @throws FormSubmissionException if saving fails
     */
    public String saveData(Map<String, String> inputFields) throws FormSubmissionException {
        try {
            // Validate input fields
            validateInputFields(inputFields);

            String primaryKey = this.formData.getPrimaryKeyValue();
            if (isEmpty(primaryKey)) {
                throw new FormSubmissionException("Primary key not generated");
            }

            // Use FormRowBuilder to create the FormRowSet
            FormRowSet rowSet = new FormRowBuilder(primaryKey)
                    .addFields(inputFields)
                    .buildRowSet();

            FormRowSet storedData = this.appService.storeFormData(
                    form,
                    rowSet,
                    primaryKey
            );

            if (storedData == null || storedData.isEmpty()) {
                throw new FormSubmissionException("Failed to store form data: No data returned");
            }

            LogUtil.info(getClass().getName(), "Successfully saved form data with ID: " + primaryKey);
            return primaryKey;
        } catch (Exception e) {
            if (e instanceof FormSubmissionException) {
                throw (FormSubmissionException) e;
            }
            throw new FormSubmissionException("Error saving form data: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves form data by ID
     *
     * @param id The ID of the form record to retrieve
     * @return Map containing the form data
     * @throws FormSubmissionException if retrieval fails
     */
    public Map<String, Object> getData(String id) throws FormSubmissionException {
        if (isEmpty(id)) {
            throw new FormSubmissionException("ID cannot be empty");
        }

        try {
            // Create a FormData object to load the data
            FormData existingFormData = new FormData();
            existingFormData.setPrimaryKeyValue(id);

            // Load the form with the existing data
            Form loadedForm = loadForm(existingFormData);

            // Use AppService to load the form data
            FormRowSet rowSet = appService.loadFormData(loadedForm, id);

            if (rowSet == null || rowSet.isEmpty()) {
                throw new FormSubmissionException("Form data not found for ID: " + id);
            }

            // Convert FormRow to a regular Map
            FormRow row = rowSet.get(0);
            Map<String, Object> data = new HashMap<>();

            // Extract keys and values from FormRow
            @SuppressWarnings("unchecked")
            Set<Object> keySet = row.keySet();
            for (Object key : keySet) {
                if (key instanceof String) {
                    String keyStr = (String) key;
                    data.put(keyStr, row.get(keyStr));
                }
            }

            return data;
        } catch (Exception e) {
            if (e instanceof FormSubmissionException) {
                throw (FormSubmissionException) e;
            }
            throw new FormSubmissionException("Error retrieving form data: " + e.getMessage(), e);
        }
    }

    /**
     * Updates existing form data
     *
     * @param id The ID of the form record to update
     * @param inputFields The new form data
     * @return true if update was successful
     * @throws FormSubmissionException if update fails
     */
    public boolean updateData(String id, Map<String, String> inputFields) throws FormSubmissionException {
        if (isEmpty(id)) {
            throw new FormSubmissionException("ID cannot be empty");
        }

        // Validate input fields
        validateInputFields(inputFields);

        try {
            // Create a new FormData for loading the existing record
            FormData existingFormData = new FormData();
            existingFormData.setPrimaryKeyValue(id);

            // Load the existing form
            Form existingForm = loadForm(existingFormData);

            // Use FormRowBuilder to create the update FormRowSet
            FormRowSet rowSet = new FormRowBuilder(id)
                    .addFields(inputFields)
                    .buildRowSet();

            // Store the updated form data
            FormRowSet storedData = appService.storeFormData(existingForm, rowSet, id);

            if (storedData == null || storedData.isEmpty()) {
                throw new FormSubmissionException("Failed to update form data: No data returned");
            }

            LogUtil.info(getClass().getName(), "Updated form data with ID: " + id);
            return true;
        } catch (Exception e) {
            if (e instanceof FormSubmissionException) {
                throw new FormSubmissionException("Error updating form data: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    /**
     * Checks if form data exists for a given ID
     *
     * @param id The ID to check
     * @return true if data exists, false otherwise
     */
    public boolean exists(String id) {
        if (isEmpty(id)) {
            return false;
        }

        try {
            FormData existingFormData = new FormData();
            existingFormData.setPrimaryKeyValue(id);
            Form loadedForm = loadForm(existingFormData);
            FormRowSet rowSet = appService.loadFormData(loadedForm, id);
            return rowSet != null && !rowSet.isEmpty();
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error checking if form data exists: " + e.getMessage());
            return false;
        }
    }
}