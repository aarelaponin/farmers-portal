package global.govstack.registration.receiver.service;

import com.google.gson.JsonObject;
import global.govstack.registration.receiver.config.ConfigLoader;
import global.govstack.registration.receiver.config.Constants;
import global.govstack.registration.receiver.exception.ConfigurationException;
import global.govstack.registration.receiver.exception.InvalidRequestException;

import java.util.Map;

/**
 * Service for handling configuration loading and validation
 */
public class ConfigurationService {
    private final ConfigLoader configLoader;
    private JsonObject configJson;

    public ConfigurationService(ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    /**
     * Validates the request and loads configuration
     *
     * @param requestBody The request JSON as string
     * @return Map of configuration values
     * @throws InvalidRequestException If the request is invalid
     * @throws ConfigurationException If there's an error loading configuration
     */
    public Map<String, String> validateAndLoadConfig(String requestBody)
            throws InvalidRequestException, ConfigurationException {
        // Validate request
        if (requestBody == null || !requestBody.trim().startsWith("{")) {
            throw new InvalidRequestException("Invalid JSON format: Must start with '{'");
        }

        // Load configuration
        try {
            configJson = configLoader.loadJsonFile(Constants.CONFIG_FILE);
            return configLoader.extractConfigValues(configJson);
        } catch (Exception e) {
            throw new ConfigurationException("Failed to load configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the loaded config JSON object
     *
     * @return JsonObject containing the configuration
     * @throws ConfigurationException If configuration wasn't loaded
     */
    public JsonObject getConfigJson() throws ConfigurationException {
        if (configJson == null) {
            throw new ConfigurationException("Configuration not loaded. Call validateAndLoadConfig first.");
        }
        return configJson;
    }
}