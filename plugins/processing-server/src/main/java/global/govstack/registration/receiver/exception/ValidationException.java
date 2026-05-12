package global.govstack.registration.receiver.exception;

/**
 * Exception thrown when validation of mandatory fields fails
 */
public class ValidationException extends Exception {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}