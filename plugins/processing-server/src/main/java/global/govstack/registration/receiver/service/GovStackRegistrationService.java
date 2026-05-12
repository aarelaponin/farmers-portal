package global.govstack.registration.receiver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import global.govstack.registration.receiver.exception.*;
import global.govstack.registration.receiver.exception.ConfigurationException;
import global.govstack.registration.receiver.service.metadata.*;
import global.govstack.registration.receiver.service.validation.ServiceMetadataValidator;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.json.JSONObject;
import org.json.JSONArray;
import javax.sql.DataSource;
import org.joget.apps.app.service.AppUtil;

import java.util.List;
import java.util.Map;

/**
 * GovStack Registration Service that saves to multiple forms
 * Uses configuration-driven mappings for generic service support
 */
public class GovStackRegistrationService implements ApiRequestProcessor {
    private static final String CLASS_NAME = GovStackRegistrationService.class.getName();

    private final String serviceId;
    private final YamlMetadataService metadataService;
    private final ServiceValidator serviceValidator;
    private final GovStackDataMapper dataMapper;
    private final TableDataHandler tableDataHandler;
    private final MultiFormSubmissionManager multiFormManager;

    public GovStackRegistrationService(String serviceId) throws ConfigurationException {
        this.serviceId = serviceId;

        try {
            // Initialize services
            this.metadataService = new YamlMetadataService();
            this.metadataService.loadMetadata(serviceId);

            this.serviceValidator = new ServiceValidator(serviceId);
            DataTransformer dataTransformer = new DataTransformer();
            this.dataMapper = new GovStackDataMapper(metadataService, dataTransformer);
            this.tableDataHandler = new TableDataHandler(metadataService);
            this.multiFormManager = new MultiFormSubmissionManager();

            // Validate services.yml against database schema
            validateMetadataConfiguration();

            LogUtil.info(CLASS_NAME, "GovStackRegistrationService initialized for service: " + serviceId);

        } catch (Exception e) {
            throw new ConfigurationException("Failed to initialize GovStackRegistrationService: " + e.getMessage(), e);
        }
    }

    /**
     * Validate services.yml configuration against database schema
     */
    private void validateMetadataConfiguration() {
        try {
            // Get DataSource from Joget
            DataSource dataSource = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");

            ServiceMetadataValidator validator = new ServiceMetadataValidator(dataSource);
            ServiceMetadataValidator.ValidationResult result = validator.validate();

            if (!result.valid) {
                LogUtil.error(CLASS_NAME, null, "Services.yml validation failed:\n" + result.getReport());
                // Log errors but don't fail startup to allow fixing configuration
                for (ServiceMetadataValidator.ValidationError error : result.errors) {
                    LogUtil.error(CLASS_NAME, null, "Configuration Error: " + error);
                }
            } else {
                LogUtil.info(CLASS_NAME, "Services.yml validation passed");
            }

            // Log warnings
            for (ServiceMetadataValidator.ValidationWarning warning : result.warnings) {
                LogUtil.warn(CLASS_NAME, "Configuration Warning: " + warning);
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error validating metadata configuration: " + e.getMessage());
            // Don't fail startup, just log the error
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public JSONObject processRequest(String requestBody) throws ApiProcessingException {
        try {
            LogUtil.info(CLASS_NAME, "Processing GovStack registration request for service: " + serviceId);

            // Validate request
            validateRequest(requestBody);

            // Check metadata version compatibility
            checkMetadataVersionCompatibility(requestBody);

            // Map GovStack data to multiple Joget forms
            Map<String, Object> mappedData = dataMapper.mapToMultipleForms(requestBody);

            // Extract components
            Map<String, Map<String, String>> formData = (Map<String, Map<String, String>>) mappedData.get("formData");
            List<Map<String, Object>> arrayData = (List<Map<String, Object>>) mappedData.get("arrayData");
            String primaryKey = (String) mappedData.get("primaryKey");

            if (primaryKey == null || primaryKey.trim().isEmpty()) {
                primaryKey = UuidGenerator.getInstance().getUuid();
            }

            LogUtil.info(CLASS_NAME, "Using primary key: " + primaryKey);

            // First, create parent record in main form - get from configuration
            String parentFormId = metadataService.getParentFormId(); // Gets from config or defaults to "farmerRegistrationForm"
            java.util.List<String> parentReferenceFields = metadataService.getParentReferenceFields(); // Gets from YAML config

            try {
                boolean parentCreated = multiFormManager.createParentRecord(parentFormId, primaryKey, parentReferenceFields);
                if (parentCreated) {
                    LogUtil.info(CLASS_NAME, "✓ Created parent record in form: " + parentFormId);
                } else {
                    LogUtil.warn(CLASS_NAME, "Failed to create parent record in form: " + parentFormId);
                }
            } catch (Exception e) {
                LogUtil.error(CLASS_NAME, e, "Error creating parent record: " + e.getMessage());
                // Continue anyway - sub-forms might still work
            }

            // Then save to multiple sub-forms
            if (formData != null && !formData.isEmpty()) {
                Map<String, Boolean> saveResults = multiFormManager.saveToMultipleForms(formData, primaryKey);

                // Log results
                for (Map.Entry<String, Boolean> entry : saveResults.entrySet()) {
                    if (entry.getValue()) {
                        LogUtil.info(CLASS_NAME, "✓ Saved to form: " + entry.getKey());
                    } else {
                        LogUtil.warn(CLASS_NAME, "✗ Failed to save to form: " + entry.getKey());
                    }
                }

                // Check if at least one form was saved successfully
                boolean anySuccess = saveResults.values().stream().anyMatch(Boolean::booleanValue);
                if (!anySuccess) {
                    throw new FormSubmissionException("Failed to save to any forms");
                }
            }

            // Save array data (grids)
            if (arrayData != null && !arrayData.isEmpty()) {
                try {
                    tableDataHandler.saveArrayData(arrayData, primaryKey);
                    LogUtil.info(CLASS_NAME, "Saved array data for " + arrayData.size() + " grids");
                } catch (Exception e) {
                    LogUtil.error(CLASS_NAME, e, "Error saving array data: " + e.getMessage());
                }
            }

            // Build success response
            return buildSuccessResponse(primaryKey);

        } catch (FormSubmissionException e) {
            throw ApiProcessingException.formSubmissionError(e.getMessage());
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Unexpected error processing request");
            throw ApiProcessingException.serverError("Error processing registration: " + e.getMessage());
        }
    }

    public void validateServiceId(String requestServiceId) throws ValidationException {
        try {
            serviceValidator.validateServiceId(requestServiceId);
        } catch (InvalidRequestException e) {
            throw new ValidationException("Invalid service ID: " + e.getMessage());
        }
    }

    private void validateRequest(String requestBody) throws FormSubmissionException {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            throw new FormSubmissionException("Request body cannot be empty");
        }

        if (!requestBody.trim().startsWith("{")) {
            throw new FormSubmissionException("Invalid JSON format: Must be a JSON object");
        }
    }

    private JSONObject buildSuccessResponse(String applicationId) {
        JSONObject response = new JSONObject();

        response.put("success", true);
        response.put("applicationId", applicationId);
        response.put("status", "submitted");
        response.put("timestamp", System.currentTimeMillis());

        JSONObject serviceInfo = new JSONObject();
        serviceInfo.put("serviceId", serviceId);
        serviceInfo.put("version", "2.0");
        response.put("service", serviceInfo);

        LogUtil.info(CLASS_NAME, "Built success response for application: " + applicationId);

        return response;
    }

    /**
     * Check metadata version compatibility between client and server
     * Logs a warning if versions don't match, but doesn't fail the request
     */
    @SuppressWarnings("unchecked")
    private void checkMetadataVersionCompatibility(String requestBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> request = mapper.readValue(requestBody, Map.class);

            String clientMetadataVersion = (String) request.get("metadataVersion");
            if (clientMetadataVersion != null) {
                String serverMetadataVersion = metadataService.getMetadataVersion();

                if (!isCompatible(clientMetadataVersion, serverMetadataVersion)) {
                    String warning = String.format(
                        "Metadata version mismatch detected. Client: %s, Server: %s. " +
                        "This may cause processing issues. Please sync configuration.",
                        clientMetadataVersion, serverMetadataVersion
                    );
                    LogUtil.warn(CLASS_NAME, warning);
                    // Don't fail - log warning and continue (backward compatibility)
                } else {
                    LogUtil.info(CLASS_NAME, String.format(
                        "Metadata version check passed. Client: %s, Server: %s",
                        clientMetadataVersion, serverMetadataVersion
                    ));
                }
            } else {
                LogUtil.info(CLASS_NAME, "No metadataVersion in request - skipping version check (legacy client)");
            }
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "Failed to check metadata version compatibility: " + e.getMessage());
            // Don't fail the request due to version check issues
        }
    }

    /**
     * Check if two metadata versions are compatible
     * Uses semantic versioning: major.minor.patch
     * Compatible if major.minor match (patch differences allowed)
     */
    private boolean isCompatible(String clientVersion, String serverVersion) {
        if (clientVersion == null || serverVersion == null) {
            return false;
        }

        if (clientVersion.equals(serverVersion)) {
            return true;
        }

        try {
            // Extract major.minor (ignore patch)
            String clientMajorMinor = getMajorMinor(clientVersion);
            String serverMajorMinor = getMajorMinor(serverVersion);

            return clientMajorMinor.equals(serverMajorMinor);
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "Failed to parse version strings for compatibility check: " + e.getMessage());
            return false;
        }
    }

    /**
     * Extract major.minor from semantic version
     */
    private String getMajorMinor(String version) {
        int lastDot = version.lastIndexOf('.');
        if (lastDot > 0) {
            return version.substring(0, lastDot);
        }
        return version;
    }
}