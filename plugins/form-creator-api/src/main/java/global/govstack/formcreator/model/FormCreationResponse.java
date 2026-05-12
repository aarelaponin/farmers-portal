package global.govstack.formcreator.model;

import global.govstack.formcreator.constants.ApiConstants;
import org.json.JSONObject;

import java.time.Instant;

/**
 * Model class representing a form creation response.
 * Contains status, created component IDs, and error information.
 */
public class FormCreationResponse {

    private String status;          // "success" or "error"
    private String formId;
    private String apiId;           // null if not created
    private String datalistId;      // null if not created
    private String userviewId;      // null if not created
    private String message;
    private String errorType;       // null if success
    private String errorMessage;    // null if success
    private String timestamp;

    // Constructors
    public FormCreationResponse() {
        this.timestamp = Instant.now().toString();
    }

    // Static factory methods
    public static FormCreationResponse success(String formId, String message) {
        FormCreationResponse response = new FormCreationResponse();
        response.setStatus(ApiConstants.StatusValues.SUCCESS);
        response.setFormId(formId);
        response.setMessage(message);
        return response;
    }

    public static FormCreationResponse error(String errorType, String errorMessage) {
        FormCreationResponse response = new FormCreationResponse();
        response.setStatus(ApiConstants.StatusValues.ERROR);
        response.setErrorType(errorType);
        response.setErrorMessage(errorMessage);
        return response;
    }

    // Convert to JSON
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put(ApiConstants.ResponseFields.STATUS, status);
        json.put(ApiConstants.ResponseFields.TIMESTAMP, timestamp);

        if (formId != null) {
            json.put(ApiConstants.ResponseFields.FORM_ID, formId);
        }

        if (apiId != null) {
            json.put(ApiConstants.ResponseFields.API_ID, apiId);
        }

        if (datalistId != null) {
            json.put(ApiConstants.ResponseFields.DATALIST_ID, datalistId);
        }

        if (userviewId != null) {
            json.put(ApiConstants.ResponseFields.USERVIEW_ID, userviewId);
        }

        if (message != null) {
            json.put(ApiConstants.ResponseFields.MESSAGE, message);
        }

        if (errorType != null) {
            json.put(ApiConstants.ResponseFields.ERROR_TYPE, errorType);
        }

        if (errorMessage != null) {
            json.put(ApiConstants.ResponseFields.ERROR_MESSAGE, errorMessage);
        }

        return json;
    }

    @Override
    public String toString() {
        return toJSON().toString();
    }

    // Getters and setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFormId() {
        return formId;
    }

    public void setFormId(String formId) {
        this.formId = formId;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getDatalistId() {
        return datalistId;
    }

    public void setDatalistId(String datalistId) {
        this.datalistId = datalistId;
    }

    public String getUserviewId() {
        return userviewId;
    }

    public void setUserviewId(String userviewId) {
        this.userviewId = userviewId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
