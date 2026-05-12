package global.govstack.formcreator.exception;

/**
 * Exception thrown during API request processing.
 * Contains HTTP status code and error type for proper error responses.
 */
public class ApiProcessingException extends RuntimeException {

    private final int statusCode;
    private final String errorType;

    public ApiProcessingException(int statusCode, String errorType, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
    }

    public ApiProcessingException(int statusCode, String errorType, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorType = errorType;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorType() {
        return errorType;
    }
}
