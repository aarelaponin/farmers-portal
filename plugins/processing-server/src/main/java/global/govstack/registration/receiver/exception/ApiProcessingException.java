package global.govstack.registration.receiver.exception;

/**
 * Base exception for API processing errors
 */
public class ApiProcessingException extends Exception {
    private final int statusCode;
    private final String errorType;

    public ApiProcessingException(String message, String errorType, int statusCode) {
        super(message);
        this.errorType = errorType;
        this.statusCode = statusCode;
    }

    public ApiProcessingException(String message, String errorType, int statusCode, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorType() {
        return errorType;
    }

    /**
     * Create an exception for invalid requests (400)
     */
    public static ApiProcessingException invalidRequest(String message) {
        return new ApiProcessingException(message, "Invalid request", 400);
    }

    /**
     * Create an exception for form submission errors (400)
     */
    public static ApiProcessingException formSubmissionError(String message) {
        return new ApiProcessingException(message, "Form submission error", 400);
    }

    /**
     * Create an exception for workflow processing errors (500)
     */
    public static ApiProcessingException workflowError(String message) {
        return new ApiProcessingException(message, "Workflow processing error", 500);
    }

    /**
     * Create an exception for configuration errors (500)
     */
    public static ApiProcessingException configError(String message) {
        return new ApiProcessingException(message, "Configuration error", 500);
    }

    /**
     * Create an exception for internal server errors (500)
     */
    public static ApiProcessingException serverError(String message) {
        return new ApiProcessingException(message, "Internal server error", 500);
    }

    public static ApiProcessingException validationError(String message) {
        return new ApiProcessingException(message, "Validation Error", 400);
    }
}