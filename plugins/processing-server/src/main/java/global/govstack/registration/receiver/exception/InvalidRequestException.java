package global.govstack.registration.receiver.exception;

public class InvalidRequestException extends RegistrationException {
    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}