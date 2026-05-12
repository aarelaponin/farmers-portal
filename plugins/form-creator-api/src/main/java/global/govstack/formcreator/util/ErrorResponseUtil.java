package global.govstack.formcreator.util;

import global.govstack.formcreator.constants.ApiConstants;
import global.govstack.formcreator.model.FormCreationResponse;

/**
 * Utility class for creating standardized error responses.
 */
public class ErrorResponseUtil {

    /**
     * Create an error response JSON string
     *
     * @param errorType Type of error
     * @param errorMessage Error message
     * @return JSON string with error details
     */
    public static String createErrorResponse(String errorType, String errorMessage) {
        FormCreationResponse response = FormCreationResponse.error(errorType, errorMessage);
        return response.toString();
    }

    /**
     * Create a validation error response
     *
     * @param errorMessage Error message
     * @return JSON string with validation error
     */
    public static String createValidationError(String errorMessage) {
        return createErrorResponse(ApiConstants.ErrorTypes.VALIDATION_ERROR, errorMessage);
    }

    /**
     * Create an internal server error response
     *
     * @param errorMessage Error message
     * @return JSON string with server error
     */
    public static String createInternalServerError(String errorMessage) {
        return createErrorResponse(ApiConstants.ErrorTypes.INTERNAL_SERVER_ERROR, errorMessage);
    }

    /**
     * Create a form creation error response
     *
     * @param errorMessage Error message
     * @return JSON string with form creation error
     */
    public static String createFormCreationError(String errorMessage) {
        return createErrorResponse(ApiConstants.ErrorTypes.FORM_CREATION_ERROR, errorMessage);
    }

    /**
     * Create an application not found error response
     *
     * @param appId Application ID that was not found
     * @return JSON string with app not found error
     */
    public static String createAppNotFoundError(String appId) {
        String message = "Application not found: " + appId;
        return createErrorResponse(ApiConstants.ErrorTypes.APP_NOT_FOUND, message);
    }

    /**
     * Create an invalid JSON error response
     *
     * @param errorMessage Error message
     * @return JSON string with invalid JSON error
     */
    public static String createInvalidJsonError(String errorMessage) {
        return createErrorResponse(ApiConstants.ErrorTypes.INVALID_JSON, errorMessage);
    }
}
