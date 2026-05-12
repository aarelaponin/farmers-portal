package global.govstack.registration.sender.exception;

public class ConfigurationException extends RegistrationException {
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}