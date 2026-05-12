package global.govstack.registration.receiver.util;

import org.json.JSONObject;

/**
 * Utility class for creating standardized error responses
 */
public class ErrorResponseUtil {

    /**
     * Create a standardized error response JSON
     *
     * @param errorType The type of error
     * @param errorMessage The error message
     * @return JSON string containing the error response
     */
    public static String createErrorResponse(String errorType, String errorMessage) {
        JSONObject error = new JSONObject();
        error.put("error", errorType);
        error.put("message", errorMessage);
        return error.toString();
    }

    /**
     * Create a detailed error response with additional information
     *
     * @param errorType The type of error
     * @param errorMessage The error message
     * @param details Additional details about the error
     * @return JSON string containing the detailed error response
     */
    public static String createDetailedErrorResponse(String errorType, String errorMessage, JSONObject details) {
        JSONObject error = new JSONObject();
        error.put("error", errorType);
        error.put("message", errorMessage);

        if (details != null) {
            error.put("details", details);
        }

        return error.toString();
    }
}