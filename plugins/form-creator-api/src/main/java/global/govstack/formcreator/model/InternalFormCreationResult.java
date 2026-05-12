package global.govstack.formcreator.model;

/**
 * Internal result object for form creation operations.
 * Uses the Result pattern to explicitly handle success/failure without exceptions.
 *
 * Named "Internal" to distinguish from the API-level FormCreationResponse.
 */
public class InternalFormCreationResult {
    private final boolean success;
    private final String formId;
    private final String errorMessage;
    private final String errorType;

    private InternalFormCreationResult(boolean success, String formId, String errorMessage, String errorType) {
        this.success = success;
        this.formId = formId;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }

    /**
     * Create a success result
     *
     * @param formId The ID of the created form
     * @return A success result
     */
    public static InternalFormCreationResult success(String formId) {
        return new InternalFormCreationResult(true, formId, null, null);
    }

    /**
     * Create an error result
     *
     * @param errorType The type/category of error (e.g., "JSON_INVALID", "DATABASE_ERROR")
     * @param errorMessage Detailed error message
     * @return An error result
     */
    public static InternalFormCreationResult error(String errorType, String errorMessage) {
        return new InternalFormCreationResult(false, null, errorMessage, errorType);
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
     * Get the form ID (only available on success)
     *
     * @return The form ID, or null if failed
     */
    public String getFormId() {
        return formId;
    }

    /**
     * Get the error message (only available on failure)
     *
     * @return The error message, or null if successful
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Get the error type (only available on failure)
     *
     * @return The error type, or null if successful
     */
    public String getErrorType() {
        return errorType;
    }

    @Override
    public String toString() {
        if (success) {
            return "InternalFormCreationResult{success=true, formId='" + formId + "'}";
        } else {
            return "InternalFormCreationResult{success=false, errorType='" + errorType +
                   "', errorMessage='" + errorMessage + "'}";
        }
    }

    /**
     * Common error types for form creation
     */
    public static class ErrorType {
        public static final String JSON_INVALID = "JSON_INVALID";
        public static final String JSON_MISSING_FIELD = "JSON_MISSING_FIELD";
        public static final String DATABASE_ERROR = "DATABASE_ERROR";
        public static final String FILESYSTEM_ERROR = "FILESYSTEM_ERROR";
        public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
        public static final String CACHE_SYNC_ERROR = "CACHE_SYNC_ERROR";
        public static final String TABLE_CREATION_ERROR = "TABLE_CREATION_ERROR";
    }
}
