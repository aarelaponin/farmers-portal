package global.govstack.formcreator.model;

/**
 * Result object for API endpoint creation operations.
 * Uses the Result pattern to explicitly handle success/failure without exceptions.
 */
public class ApiCreationResult {
    private final boolean success;
    private final String apiId;
    private final String errorMessage;

    private ApiCreationResult(boolean success, String apiId, String errorMessage) {
        this.success = success;
        this.apiId = apiId;
        this.errorMessage = errorMessage;
    }

    /**
     * Create a success result
     *
     * @param apiId The ID of the created API endpoint (e.g., "API-uuid")
     * @return A success result
     */
    public static ApiCreationResult success(String apiId) {
        return new ApiCreationResult(true, apiId, null);
    }

    /**
     * Create an error result
     *
     * @param errorMessage Detailed error message
     * @return An error result
     */
    public static ApiCreationResult error(String errorMessage) {
        return new ApiCreationResult(false, null, errorMessage);
    }

    /**
     * Check if the operation was successful
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Get the API ID (only available on success)
     *
     * @return The API ID, or null if failed
     */
    public String getApiId() {
        return apiId;
    }

    /**
     * Get the error message (only available on failure)
     *
     * @return The error message, or null if successful
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return "ApiCreationResult{success=true, apiId='" + apiId + "'}";
        } else {
            return "ApiCreationResult{success=false, errorMessage='" + errorMessage + "'}";
        }
    }
}
