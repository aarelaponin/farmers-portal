package global.govstack.registration.receiver.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import global.govstack.registration.receiver.exception.ConfigurationException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ConfigLoader {

    /**
     * Load JSON configuration file from classpath
     *
     * @param filename The name of the configuration file
     * @return JsonObject containing the configuration
     */
    public JsonObject loadJsonFile(String filename) throws ConfigurationException {
        try {
            // Get resource as stream from classpath
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filename);

            if (inputStream == null) {
                throw new ConfigurationException("Could not find configuration file: " + filename);
            }
            Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            Gson gson = new Gson();

            return gson.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            if (e instanceof ConfigurationException) {
                throw (ConfigurationException) e;
            }
            throw new ConfigurationException("Error loading configuration file: " + filename, e);
        }
    }

    /**
     * Extract common configuration values into a map for easier access
     *
     * @param config JsonObject containing the configuration
     * @return Map of configuration values
     */
    public Map<String, String> extractConfigValues(JsonObject config) {
        Map<String, String> configValues = new HashMap<>();

        // Extract form ID
        configValues.put(Constants.FORM_ID,
                config.get(Constants.FORM_ID).getAsString());

        // Extract process definition ID
        configValues.put(Constants.PROCESS_DEF_ID,
                config.get(Constants.PROCESS_DEF_ID).getAsString());

        // Extract role usernames
        JsonObject rolesJson = config.getAsJsonObject(Constants.ROLES);
        configValues.put(Constants.ADMIN_USERNAME,
                rolesJson.get(Constants.ADMIN_USERNAME).getAsString());
        configValues.put(Constants.REGISTRANT_USERNAME,
                rolesJson.get(Constants.REGISTRANT_USERNAME).getAsString());
        configValues.put(Constants.CHIEF_REVIEWER_USERNAME,
                rolesJson.get(Constants.CHIEF_REVIEWER_USERNAME).getAsString());

        // Extract activity IDs
        JsonObject activitiesJson = config.getAsJsonObject(Constants.ACTIVITIES);
        configValues.put(Constants.SUBMIT_ACTIVITY_ID,
                activitiesJson.get(Constants.SUBMIT_ACTIVITY_ID).getAsString());
        configValues.put(Constants.REVIEW_ACTIVITY_ID,
                activitiesJson.get(Constants.REVIEW_ACTIVITY_ID).getAsString());

        return configValues;
    }
}