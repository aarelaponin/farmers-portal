package global.govstack.registration.receiver.service.metadata;

import org.joget.commons.util.LogUtil;
import global.govstack.registration.receiver.exception.InvalidRequestException;

/**
 * Service to validate incoming service requests
 */
public class ServiceValidator {
    private static final String CLASS_NAME = ServiceValidator.class.getName();
    private final String configuredServiceId;

    /**
     * Constructor with the configured service ID
     * @param configuredServiceId The service ID configured for this plugin instance
     */
    public ServiceValidator(String configuredServiceId) {
        if (configuredServiceId == null || configuredServiceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Configured service ID cannot be null or empty");
        }
        this.configuredServiceId = configuredServiceId.trim();
        LogUtil.info(CLASS_NAME, "ServiceValidator initialized for service: " + this.configuredServiceId);
    }

    /**
     * Validate that the incoming service ID matches the configured service ID
     * @param requestServiceId The service ID from the request URL
     * @throws InvalidRequestException if the service IDs don't match
     */
    public void validateServiceId(String requestServiceId) throws InvalidRequestException {
        if (requestServiceId == null || requestServiceId.trim().isEmpty()) {
            throw new InvalidRequestException("Service ID is required in the request path");
        }

        String trimmedRequestId = requestServiceId.trim();

        if (!configuredServiceId.equals(trimmedRequestId)) {
            LogUtil.warn(CLASS_NAME, "Service ID mismatch. Expected: " + configuredServiceId + ", Received: " + trimmedRequestId);
            throw new InvalidRequestException(
                "Service ID mismatch. This plugin is configured for service: " + configuredServiceId +
                ", but received request for service: " + trimmedRequestId
            );
        }

        LogUtil.debug(CLASS_NAME, "Service ID validation successful for: " + trimmedRequestId);
    }

    /**
     * Get the configured service ID
     * @return The configured service ID
     */
    public String getConfiguredServiceId() {
        return configuredServiceId;
    }

    /**
     * Check if a service ID matches the configured service
     * @param serviceId The service ID to check
     * @return true if matches, false otherwise
     */
    public boolean isValidService(String serviceId) {
        return serviceId != null && configuredServiceId.equals(serviceId.trim());
    }
}