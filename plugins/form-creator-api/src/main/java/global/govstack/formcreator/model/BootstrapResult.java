package global.govstack.formcreator.model;

/**
 * Result object for bootstrap operations (creating formCreator CRUD on first API call).
 * Uses the Result pattern to explicitly handle success/failure without exceptions.
 */
public class BootstrapResult {
    private final boolean success;
    private final String formId;
    private final String datalistId;
    private final String userviewId;
    private final String errorMessage;
    private final boolean wasAlreadyExists;

    private BootstrapResult(boolean success, String formId, String datalistId, String userviewId,
                           String errorMessage, boolean wasAlreadyExists) {
        this.success = success;
        this.formId = formId;
        this.datalistId = datalistId;
        this.userviewId = userviewId;
        this.errorMessage = errorMessage;
        this.wasAlreadyExists = wasAlreadyExists;
    }

    /**
     * Create a success result for newly created CRUD
     *
     * @param formId The ID of the created form
     * @param datalistId The ID of the created datalist
     * @param userviewId The ID of the userview
     * @return A success result
     */
    public static BootstrapResult success(String formId, String datalistId, String userviewId) {
        return new BootstrapResult(true, formId, datalistId, userviewId, null, false);
    }

    /**
     * Create a success result when CRUD already existed
     *
     * @return A success result indicating CRUD was already present
     */
    public static BootstrapResult alreadyExists() {
        return new BootstrapResult(true, null, null, null, null, true);
    }

    /**
     * Create an error result
     *
     * @param errorMessage Detailed error message
     * @return An error result
     */
    public static BootstrapResult error(String errorMessage) {
        return new BootstrapResult(false, null, null, null, errorMessage, false);
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
     * Check if the CRUD already existed (no creation was needed)
     *
     * @return true if CRUD was already present
     */
    public boolean wasAlreadyExists() {
        return wasAlreadyExists;
    }

    /**
     * Get the form ID (only available on success when newly created)
     *
     * @return The form ID, or null if failed or already existed
     */
    public String getFormId() {
        return formId;
    }

    /**
     * Get the datalist ID (only available on success when newly created)
     *
     * @return The datalist ID, or null if failed or already existed
     */
    public String getDatalistId() {
        return datalistId;
    }

    /**
     * Get the userview ID (only available on success when newly created)
     *
     * @return The userview ID, or null if failed or already existed
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
            if (wasAlreadyExists) {
                return "BootstrapResult{success=true, wasAlreadyExists=true}";
            } else {
                return "BootstrapResult{success=true, formId='" + formId +
                       "', datalistId='" + datalistId + "', userviewId='" + userviewId + "'}";
            }
        } else {
            return "BootstrapResult{success=false, errorMessage='" + errorMessage + "'}";
        }
    }
}
