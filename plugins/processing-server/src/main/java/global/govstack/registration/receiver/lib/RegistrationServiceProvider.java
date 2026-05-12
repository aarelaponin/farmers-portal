package global.govstack.registration.receiver.lib;

import global.govstack.registration.receiver.lib.base.BaseServiceProvider;
import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import global.govstack.registration.receiver.service.ApiRequestProcessor;
import global.govstack.registration.receiver.service.RegistrationServiceFactory;
import global.govstack.registration.receiver.service.GovStackRegistrationService;
import org.joget.plugin.property.model.PropertyEditable;

/**
 * Registration Service Provider for the GovStack Registration Building Block.
 *
 * This API endpoint allows for creating new registration applications in the Joget system.
 * It extends BaseServiceProvider which provides generic request/response handling,
 * error management, and user context management.
 *
 * Supports multi-service architecture via serviceId in URL path:
 * POST /services/{serviceId}/applications
 *
 * The provider supports two modes:
 * - GovStack mode: Uses configuration-driven YAML metadata for field mappings
 * - Legacy mode: Uses hardcoded field mappings
 */
public class RegistrationServiceProvider extends BaseServiceProvider implements PropertyEditable {
    private static final String CLASS_NAME = "global.govstack.registration.receiver.lib.RegistrationServiceProvider";

    @Override
    public String getName() {
        return "govstack-registration-receiver";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "GovStack Registration Building Block - Receiver API. Supports multiple services via serviceId parameter.";
    }

    @Override
    public String getTag() {
        return "govstack/registration";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fa fa-inbox\"></i>";
    }

    @Override
    public String getLabel() {
        return "GovStack Registration Receiver";
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/RegistrationServiceProvider.json", null, true, null);
    }

    @Operation(
            path = "/services/{serviceId}/applications",
            type = Operation.MethodType.POST,
            summary = "@@RegistrationServiceProvider.createApplication.summary@@",
            description = "@@RegistrationServiceProvider.createApplication.description@@"
    )
    @Responses({
            @Response(responseCode = 200, description = "Success"),
            @Response(responseCode = 400, description = "Bad Request."),
            @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse createApplication(
            @Param(value = "serviceId", required = true) String serviceId,
            @Param(value = "body") String requestBody
    ) {
        // Use the generic request processing with serviceId from URL path
        LogUtil.info(CLASS_NAME, "Processing request for serviceId: " + serviceId);
        return processServiceRequest(serviceId, requestBody);
    }

    /**
     * Creates the request processor for registration services.
     * Implements the abstract method from BaseServiceProvider.
     *
     * Determines which processor to use based on configuration:
     * - GovStack mode: Uses YAML metadata-driven service with serviceId from URL
     * - Legacy mode: Uses factory-created service with hardcoded mappings
     *
     * @param serviceId The service identifier from URL path parameter
     * @param requestBody The request body (used to determine processor type)
     * @return The request processor instance
     */
    @Override
    protected ApiRequestProcessor createRequestProcessor(String serviceId, String requestBody) {
        // Check if GovStack mode is enabled
        String useGovStack = getPropertyString("useGovStack");

        if ("true".equalsIgnoreCase(useGovStack)) {
            // Validate serviceId format
            if (serviceId == null || serviceId.trim().isEmpty()) {
                throw new IllegalArgumentException("ServiceId is required in URL path");
            }

            if (!serviceId.matches("^[a-z0-9_]+$")) {
                throw new IllegalArgumentException("Invalid serviceId format: " + serviceId +
                    ". Use lowercase, numbers, underscore only.");
            }

            try {
                // Use configuration-driven GovStack service (transport layer only)
                // ServiceId comes from URL path parameter, NOT plugin configuration
                LogUtil.info(CLASS_NAME, "Using GovStackRegistrationService for serviceId from URL: " + serviceId);

                GovStackRegistrationService govStackService = new GovStackRegistrationService(serviceId);
                // Service configuration will be loaded from docs-metadata/{serviceId}.yml
                return govStackService;
            } catch (Exception e) {
                LogUtil.error(CLASS_NAME, e, "Error creating GovStack service for serviceId '" + serviceId + "': " + e.getMessage());
                throw new RuntimeException("Failed to initialize GovStack service for '" + serviceId + "': " + e.getMessage(), e);
            }
        } else {
            // Use legacy implementation (ignores serviceId parameter)
            LogUtil.warn(CLASS_NAME, "Using legacy mode - serviceId parameter ignored");
            return RegistrationServiceFactory.createRequestProcessor();
        }
    }
}
