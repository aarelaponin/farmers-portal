package global.govstack.formcreator.util;

import global.govstack.formcreator.constants.ApiConstants;
import global.govstack.formcreator.exception.ValidationException;
import global.govstack.formcreator.model.FormCreationRequest;
import org.joget.commons.util.LogUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Utility class for parsing API request bodies into FormCreationRequest objects.
 * Supports both JSON and multipart form data formats.
 */
public class RequestParserUtil {

    private static final String CLASS_NAME = RequestParserUtil.class.getName();

    /**
     * Parse JSON request body into FormCreationRequest
     *
     * @param requestBody JSON request body as string
     * @return FormCreationRequest object
     * @throws ValidationException if JSON is invalid
     */
    public static FormCreationRequest parseJsonRequest(String requestBody) {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            throw new ValidationException("Request body is empty");
        }

        try {
            JSONObject json = new JSONObject(requestBody);
            FormCreationRequest request = new FormCreationRequest();

            // Parse required fields
            request.setFormId(getStringField(json, ApiConstants.RequestFields.FORM_ID));
            request.setFormName(getStringField(json, ApiConstants.RequestFields.FORM_NAME));
            request.setTableName(getStringField(json, ApiConstants.RequestFields.TABLE_NAME));
            request.setFormDefinitionJson(getStringField(json, ApiConstants.RequestFields.FORM_DEFINITION));

            // Parse optional target application fields
            if (json.has(ApiConstants.RequestFields.TARGET_APP_ID)) {
                request.setTargetAppId(getStringField(json, ApiConstants.RequestFields.TARGET_APP_ID));
            }

            if (json.has(ApiConstants.RequestFields.TARGET_APP_VERSION)) {
                request.setTargetAppVersion(getStringField(json, ApiConstants.RequestFields.TARGET_APP_VERSION));
            }

            // Parse optional API endpoint fields
            if (json.has(ApiConstants.RequestFields.CREATE_API_ENDPOINT)) {
                request.setCreateApiEndpoint(getBooleanField(json, ApiConstants.RequestFields.CREATE_API_ENDPOINT));
            }

            if (json.has(ApiConstants.RequestFields.API_NAME)) {
                request.setApiName(getStringField(json, ApiConstants.RequestFields.API_NAME));
            }

            // Parse optional CRUD fields
            if (json.has(ApiConstants.RequestFields.CREATE_CRUD)) {
                request.setCreateCrud(getBooleanField(json, ApiConstants.RequestFields.CREATE_CRUD));
            }

            if (json.has(ApiConstants.RequestFields.DATALIST_NAME)) {
                request.setDatalistName(getStringField(json, ApiConstants.RequestFields.DATALIST_NAME));
            }

            if (json.has(ApiConstants.RequestFields.USERVIEW_NAME)) {
                request.setUserviewName(getStringField(json, ApiConstants.RequestFields.USERVIEW_NAME));
            }

            LogUtil.debug(CLASS_NAME, "Parsed JSON request: " + request);
            return request;

        } catch (JSONException e) {
            LogUtil.error(CLASS_NAME, e, "Failed to parse JSON request");
            throw new ValidationException("Invalid JSON format: " + e.getMessage(), e);
        }
    }

    /**
     * Parse form definition file content into request
     * Used when form definition is uploaded as a file
     *
     * @param request The request to update
     * @param fileContent File content as bytes
     * @param fileName File name
     */
    public static void parseFormDefinitionFile(FormCreationRequest request, byte[] fileContent, String fileName) {
        if (fileContent == null || fileContent.length == 0) {
            throw new ValidationException("Form definition file is empty");
        }

        try {
            // Convert file content to string
            String jsonContent = new String(fileContent, StandardCharsets.UTF_8);

            // Validate it's valid JSON
            new JSONObject(jsonContent);

            // Set in request
            request.setFormDefinitionJson(jsonContent);
            request.setFormDefinitionFile(fileContent);
            request.setFormDefinitionFileName(fileName);

            LogUtil.debug(CLASS_NAME, "Parsed form definition file: " + fileName);

        } catch (JSONException e) {
            LogUtil.error(CLASS_NAME, e, "Invalid JSON in form definition file");
            throw new ValidationException("Form definition file contains invalid JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Get string field from JSON object
     */
    private static String getStringField(JSONObject json, String fieldName) {
        if (!json.has(fieldName)) {
            return null;
        }

        Object value = json.get(fieldName);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }

        return value.toString();
    }

    /**
     * Get boolean field from JSON object
     * Handles multiple formats: true/false, "true"/"false", 1/0, "yes"/"no"
     */
    private static boolean getBooleanField(JSONObject json, String fieldName) {
        if (!json.has(fieldName)) {
            return false;
        }

        Object value = json.get(fieldName);
        if (value == null || value == JSONObject.NULL) {
            return false;
        }

        // Handle boolean directly
        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        // Handle string representations
        String strValue = value.toString().trim().toLowerCase();
        return strValue.equals(ApiConstants.CheckboxValues.TRUE) ||
               strValue.equals(ApiConstants.CheckboxValues.ONE) ||
               strValue.equals(ApiConstants.CheckboxValues.YES) ||
               strValue.equals(ApiConstants.CheckboxValues.ON) ||
               strValue.equals(ApiConstants.CheckboxValues.CHECKED);
    }

    /**
     * Validate that the request has either formDefinition or formDefinitionFile
     *
     * @param request The request to validate
     * @throws ValidationException if neither is present
     */
    public static void validateFormDefinitionPresent(FormCreationRequest request) {
        boolean hasJsonDefinition = request.getFormDefinitionJson() != null &&
                                   !request.getFormDefinitionJson().trim().isEmpty();

        boolean hasFileDefinition = request.getFormDefinitionFile() != null &&
                                   request.getFormDefinitionFile().length > 0;

        if (!hasJsonDefinition && !hasFileDefinition) {
            throw new ValidationException(ApiConstants.ValidationMessages.FORM_DEFINITION_REQUIRED);
        }
    }

    /**
     * Parse multipart request data into FormCreationRequest
     *
     * @param multipartData Parsed multipart data
     * @return FormCreationRequest object
     * @throws ValidationException if parsing fails
     */
    public static FormCreationRequest parseMultipartRequest(MultipartRequestParser.MultipartData multipartData) {
        if (multipartData == null) {
            throw new ValidationException("Multipart data is null");
        }

        try {
            Map<String, String> fields = multipartData.getFields();
            Map<String, MultipartRequestParser.FileUpload> files = multipartData.getFiles();

            FormCreationRequest request = new FormCreationRequest();

            // Parse required fields
            request.setFormId(fields.get(ApiConstants.RequestFields.FORM_ID));
            request.setFormName(fields.get(ApiConstants.RequestFields.FORM_NAME));
            request.setTableName(fields.get(ApiConstants.RequestFields.TABLE_NAME));

            // Parse optional target application fields
            if (fields.containsKey(ApiConstants.RequestFields.TARGET_APP_ID)) {
                request.setTargetAppId(fields.get(ApiConstants.RequestFields.TARGET_APP_ID));
            }

            if (fields.containsKey(ApiConstants.RequestFields.TARGET_APP_VERSION)) {
                request.setTargetAppVersion(fields.get(ApiConstants.RequestFields.TARGET_APP_VERSION));
            }

            // Parse optional API endpoint fields
            if (fields.containsKey(ApiConstants.RequestFields.CREATE_API_ENDPOINT)) {
                request.setCreateApiEndpoint(parseBooleanField(fields.get(ApiConstants.RequestFields.CREATE_API_ENDPOINT)));
            }

            if (fields.containsKey(ApiConstants.RequestFields.API_NAME)) {
                request.setApiName(fields.get(ApiConstants.RequestFields.API_NAME));
            }

            // Parse optional CRUD fields
            if (fields.containsKey(ApiConstants.RequestFields.CREATE_CRUD)) {
                request.setCreateCrud(parseBooleanField(fields.get(ApiConstants.RequestFields.CREATE_CRUD)));
            }

            if (fields.containsKey(ApiConstants.RequestFields.DATALIST_NAME)) {
                request.setDatalistName(fields.get(ApiConstants.RequestFields.DATALIST_NAME));
            }

            if (fields.containsKey(ApiConstants.RequestFields.USERVIEW_NAME)) {
                request.setUserviewName(fields.get(ApiConstants.RequestFields.USERVIEW_NAME));
            }

            // Handle file upload
            if (files.containsKey(ApiConstants.RequestFields.FORM_DEFINITION_FILE)) {
                MultipartRequestParser.FileUpload fileUpload = files.get(ApiConstants.RequestFields.FORM_DEFINITION_FILE);

                // Validate it's valid JSON
                String jsonContent = fileUpload.getContentAsString();
                new JSONObject(jsonContent);

                // Set in request
                request.setFormDefinitionJson(jsonContent);
                request.setFormDefinitionFile(fileUpload.getContent());
                request.setFormDefinitionFileName(fileUpload.getFilename());

                LogUtil.debug(CLASS_NAME, "Parsed form definition file: " + fileUpload.getFilename());
            }

            LogUtil.debug(CLASS_NAME, "Parsed multipart request: " + request);
            return request;

        } catch (JSONException e) {
            LogUtil.error(CLASS_NAME, e, "Invalid JSON in form definition file");
            throw new ValidationException("Form definition file contains invalid JSON: " + e.getMessage(), e);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Failed to parse multipart request");
            throw new ValidationException("Invalid multipart request: " + e.getMessage(), e);
        }
    }

    /**
     * Parse boolean field from string value
     * Handles multiple formats: true/false, "true"/"false", 1/0, "yes"/"no"
     */
    private static boolean parseBooleanField(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        String strValue = value.trim().toLowerCase();
        return strValue.equals(ApiConstants.CheckboxValues.TRUE) ||
               strValue.equals(ApiConstants.CheckboxValues.ONE) ||
               strValue.equals(ApiConstants.CheckboxValues.YES) ||
               strValue.equals(ApiConstants.CheckboxValues.ON) ||
               strValue.equals(ApiConstants.CheckboxValues.CHECKED);
    }
}
