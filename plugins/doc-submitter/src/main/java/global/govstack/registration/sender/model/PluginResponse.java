package global.govstack.registration.sender.model;

/**
 * Standardized response object for Registry Client operations
 */
public class PluginResponse {
    private final boolean success;
    private final String message;
    private final String data;
    private final int statusCode;

    private PluginResponse(boolean success, String message, String data, int statusCode) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.statusCode = statusCode;
    }

    /**
     * Create a success response
     *
     * @param data The response data
     * @return A success response
     */
    public static PluginResponse success(String data) {
        return new PluginResponse(true, "Success", data, 200);
    }

    /**
     * Create an error response
     *
     * @param message The error message
     * @return An error response with status code 500
     */
    public static PluginResponse error(String message) {
        return new PluginResponse(false, message, null, 500);
    }

    /**
     * Create an error response with a specific status code
     *
     * @param message The error message
     * @param statusCode The HTTP status code
     * @return An error response with the specified status code
     */
    public static PluginResponse error(String message, int statusCode) {
        return new PluginResponse(false, message, null, statusCode);
    }

    /**
     * Get whether the response was successful
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Get the response message
     *
     * @return The response message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Get the response data
     *
     * @return The response data
     */
    public String getData() {
        return data;
    }

    /**
     * Get the status code
     *
     * @return The HTTP status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Convert the response to a string
     *
     * @return The string representation of the response
     */
    @Override
    public String toString() {
        if (success) {
            return data;
        } else {
            return "Error: " + message;
        }
    }
}