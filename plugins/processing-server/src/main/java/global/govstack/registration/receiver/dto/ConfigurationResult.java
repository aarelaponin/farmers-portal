package global.govstack.registration.receiver.dto;

import com.google.gson.JsonObject;
import java.util.Map;

/**
 * DTO for configuration results
 */
public class ConfigurationResult {
    private final Map<String, String> configValues;
    private final JsonObject configJson;

    public ConfigurationResult(Map<String, String> configValues, JsonObject configJson) {
        this.configValues = configValues;
        this.configJson = configJson;
    }

    public Map<String, String> getConfigValues() {
        return configValues;
    }

    public JsonObject getConfigJson() {
        return configJson;
    }
}

