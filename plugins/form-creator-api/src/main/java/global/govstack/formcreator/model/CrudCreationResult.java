package global.govstack.formcreator.model;

/**
 * Result object for CRUD (Datalist + Userview) creation operations.
 * Uses the Result pattern to explicitly handle success/failure without exceptions.
 */
public class CrudCreationResult {
    private final boolean success;
    private final String datalistId;
    private final String userviewId;
    private final String errorMessage;

    private CrudCreationResult(boolean success, String datalistId, String userviewId, String errorMessage) {
        this.success = success;
        this.datalistId = datalistId;
        this.userviewId = userviewId;
        this.errorMessage = errorMessage;
    }

    /**
     * Create a success result
     *
     * @param datalistId The ID of the created datalist
     * @param userviewId The ID of the created userview
     * @return A success result
     */
    public static CrudCreationResult success(String datalistId, String userviewId) {
        return new CrudCreationResult(true, datalistId, userviewId, null);
    }

    /**
     * Create an error result
     *
     * @param errorMessage Detailed error message
     * @return An error result
     */
    public static CrudCreationResult error(String errorMessage) {
        return new CrudCreationResult(false, null, null, errorMessage);
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
     * Get the datalist ID (only available on success)
     *
     * @return The datalist ID, or null if failed
     */
    public String getDatalistId() {
        return datalistId;
    }

    /**
     * Get the userview ID (only available on success)
     *
     * @return The userview ID, or null if failed
     */
    public String getUserviewId() {
        return userviewId;
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
            return "CrudCreationResult{success=true, datalistId='" + datalistId +
                   "', userviewId='" + userviewId + "'}";
        } else {
            return "CrudCreationResult{success=false, errorMessage='" + errorMessage + "'}";
        }
    }
}
