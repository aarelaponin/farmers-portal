package global.govstack.registration.receiver.service.normalization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import global.govstack.registration.receiver.service.metadata.YamlMetadataService;
import org.joget.commons.util.LogUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes various input formats to standard LOV values for storage.
 * Ensures backward compatibility with test-data.json while supporting
 * new formats from DocSubmitter transformations.
 */
public class ValueNormalizer {

    private static final String CLASS_NAME = ValueNormalizer.class.getName();

    private final ValueFormatDetector detector;
    private final Map<String, NormalizationConfig> fieldConfigs;
    private final Set<String> masterdataFields;

    /**
     * Configuration for field-specific normalization
     */
    public static class NormalizationConfig {
        private final String positiveValue;  // LOV value for yes/true/1
        private final String negativeValue;  // LOV value for no/false/2
        private final Map<String, String> customMappings;

        public NormalizationConfig(String positiveValue, String negativeValue) {
            this.positiveValue = positiveValue;
            this.negativeValue = negativeValue;
            this.customMappings = new HashMap<>();
        }

        public NormalizationConfig(String positiveValue, String negativeValue, Map<String, String> customMappings) {
            this.positiveValue = positiveValue;
            this.negativeValue = negativeValue;
            this.customMappings = customMappings != null ? customMappings : new HashMap<>();
        }

        public String getPositiveValue() {
            return positiveValue;
        }

        public String getNegativeValue() {
            return negativeValue;
        }

        public String getCustomMapping(String value) {
            return customMappings.get(value);
        }
    }

    /**
     * Constructor with default configurations (no metadata service)
     * Uses empty masterDataFields set - all fields may be normalized
     */
    public ValueNormalizer() {
        this.detector = new ValueFormatDetector();
        this.fieldConfigs = new HashMap<>();
        this.masterdataFields = new HashSet<>();
        initializeDefaultConfigs(null);  // Pass null, will use empty config
        LogUtil.info(CLASS_NAME, "ValueNormalizer created without metadata service - empty configuration");
    }

    /**
     * Constructor with metadata service for configuration-driven master data fields
     * @param metadataService The YAML metadata service to load master data fields from
     */
    public ValueNormalizer(YamlMetadataService metadataService) {
        this.detector = new ValueFormatDetector();
        this.fieldConfigs = new HashMap<>();
        this.masterdataFields = new HashSet<>();
        initializeDefaultConfigs(metadataService);
        loadMasterdataFieldsFromConfig(metadataService);
    }

    /**
     * Constructor with custom detector
     */
    public ValueNormalizer(ValueFormatDetector detector) {
        this.detector = detector;
        this.fieldConfigs = new HashMap<>();
        this.masterdataFields = new HashSet<>();
        initializeDefaultConfigs(null);  // Pass null, will use empty config
        LogUtil.info(CLASS_NAME, "ValueNormalizer created with custom detector - empty configuration");
    }

    /**
     * Initialize field configurations from YAML metadata
     * Replaces hardcoded field lists with configuration-driven approach
     */
    private void initializeDefaultConfigs(YamlMetadataService metadataService) {
        if (metadataService == null) {
            LogUtil.warn(CLASS_NAME, "No metadata service provided, using empty normalization config");
            return;
        }

        Map<String, List<String>> normConfig = metadataService.getFieldNormalizationConfig();

        // Load yesNo fields (yes/no → yes/no)
        List<String> yesNoFields = normConfig.get("yesNo");
        if (yesNoFields != null) {
            NormalizationConfig yesNoConfig = new NormalizationConfig("yes", "no");
            for (String field : yesNoFields) {
                fieldConfigs.put(field, yesNoConfig);
            }
            LogUtil.info(CLASS_NAME, "Loaded " + yesNoFields.size() + " yesNo normalization fields");
        }

        // Load oneTwo fields (yes/no → 1/2)
        List<String> oneTwoFields = normConfig.get("oneTwo");
        if (oneTwoFields != null) {
            NormalizationConfig oneTwoConfig = new NormalizationConfig("1", "2");
            for (String field : oneTwoFields) {
                fieldConfigs.put(field, oneTwoConfig);
            }
            LogUtil.info(CLASS_NAME, "Loaded " + oneTwoFields.size() + " oneTwo normalization fields");
        }

        LogUtil.info(CLASS_NAME, "Initialized normalization for " + fieldConfigs.size() + " fields from configuration");
    }

    /**
     * Load masterdata fields from configuration
     * Master data fields contain codes synchronized from lookup tables and must pass through unchanged
     * @param metadataService The YAML metadata service
     */
    private void loadMasterdataFieldsFromConfig(YamlMetadataService metadataService) {
        if (metadataService == null) {
            LogUtil.warn(CLASS_NAME, "No metadata service provided, masterdata fields list will be empty");
            return;
        }

        Set<String> configuredFields = metadataService.getMasterDataFields();
        if (configuredFields != null && !configuredFields.isEmpty()) {
            masterdataFields.addAll(configuredFields);
            LogUtil.info(CLASS_NAME, "Loaded " + masterdataFields.size() + " masterdata fields from configuration");
        } else {
            LogUtil.warn(CLASS_NAME, "No masterdata fields configured in services.yml - all fields may be normalized");
        }
    }

    /**
     * Add or update field configuration
     */
    public void addFieldConfig(String fieldName, NormalizationConfig config) {
        fieldConfigs.put(fieldName, config);
        LogUtil.debug(CLASS_NAME, "Added normalization config for field: " + fieldName);
    }

    /**
     * Normalize any input format to LOV value for storage
     *
     * @param value     The JSON value to normalize
     * @param fieldName The field name (for field-specific rules)
     * @return The normalized LOV value as string
     */
    public String normalizeToLOV(JsonNode value, String fieldName) {
        if (value == null || value.isNull()) {
            LogUtil.debug(CLASS_NAME, "Null value for field " + fieldName + ", returning null");
            return null;
        }

        // Check if field is masterdata and should not be normalized
        if (masterdataFields.contains(fieldName)) {
            String result = value.asText();
            LogUtil.info(CLASS_NAME, "Field '" + fieldName + "' is masterdata, passing through unchanged: " + result);
            return result;
        }

        ValueFormatDetector.Format format = detector.detectFormat(value);
        NormalizationConfig config = fieldConfigs.get(fieldName);

        // If no specific config, use default 1/2 mapping
        if (config == null) {
            config = new NormalizationConfig("1", "2");
            LogUtil.debug(CLASS_NAME, "No specific config for field " + fieldName + ", using default 1/2 mapping");
        }

        String result = null;

        switch (format) {
            case LOV_NUMERIC:
                // Already in LOV numeric format (1 or 2)
                result = value.isNumber() ? String.valueOf(value.asInt()) : value.asText();
                LogUtil.debug(CLASS_NAME, "Field " + fieldName + " already in LOV_NUMERIC format: " + result);
                break;

            case LOV_TEXT:
                // Convert yes/no to configured values
                String textValue = value.asText().toLowerCase().trim();
                if ("yes".equals(textValue)) {
                    result = config.getPositiveValue();
                } else if ("no".equals(textValue)) {
                    result = config.getNegativeValue();
                } else {
                    result = textValue; // Keep as-is if not yes/no
                }
                LogUtil.debug(CLASS_NAME, "Field " + fieldName + " converted from LOV_TEXT '" + textValue + "' to '" + result + "'");
                break;

            case BOOLEAN:
                // Convert boolean to configured values
                result = value.asBoolean() ? config.getPositiveValue() : config.getNegativeValue();
                LogUtil.debug(CLASS_NAME, "Field " + fieldName + " converted from BOOLEAN " + value.asBoolean() + " to '" + result + "'");
                break;

            case BOOLEAN_STRING:
                // Convert "true"/"false" strings to configured values
                boolean boolValue = "true".equalsIgnoreCase(value.asText());
                result = boolValue ? config.getPositiveValue() : config.getNegativeValue();
                LogUtil.debug(CLASS_NAME, "Field " + fieldName + " converted from BOOLEAN_STRING '" + value.asText() + "' to '" + result + "'");
                break;

            case CUSTOM:
                // Check custom mappings first
                String customValue = value.asText();
                String mapped = config.getCustomMapping(customValue);
                if (mapped != null) {
                    result = mapped;
                    LogUtil.debug(CLASS_NAME, "Field " + fieldName + " custom mapping: '" + customValue + "' to '" + result + "'");
                } else {
                    // Keep custom value as-is if no mapping found
                    result = customValue;
                    LogUtil.debug(CLASS_NAME, "Field " + fieldName + " keeping custom value as-is: " + result);
                }
                break;

            case NULL:
                result = null;
                break;

            default:
                result = value.asText();
                LogUtil.warn(CLASS_NAME, "Unknown format for field " + fieldName + ", using as-is: " + result);
        }

        LogUtil.info(CLASS_NAME, "Normalized field '" + fieldName + "' from " + format + " to LOV value: " + result);
        return result;
    }

    /**
     * Normalize a value using default configuration (1/2 for boolean fields)
     *
     * @param value The JSON value to normalize
     * @return The normalized LOV value
     */
    public String normalizeToLOV(JsonNode value) {
        return normalizeToLOV(value, "__default__");
    }

    /**
     * Check if a field has a specific normalization configuration
     *
     * @param fieldName The field name
     * @return true if field has custom configuration
     */
    public boolean hasFieldConfig(String fieldName) {
        return fieldConfigs.containsKey(fieldName);
    }

    /**
     * Get the configuration for a field
     *
     * @param fieldName The field name
     * @return The normalization configuration, or null if not found
     */
    public NormalizationConfig getFieldConfig(String fieldName) {
        return fieldConfigs.get(fieldName);
    }

    /**
     * Create a normalizer with custom field configurations from services.yml
     *
     * @param fieldMappings Map of field configurations from services.yml
     * @return Configured normalizer
     */
    public static ValueNormalizer createFromServiceConfig(Map<String, Map<String, Object>> fieldMappings) {
        ValueNormalizer normalizer = new ValueNormalizer();

        for (Map.Entry<String, Map<String, Object>> entry : fieldMappings.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Object> fieldConfig = entry.getValue();

            // Check if field has normalization configuration
            Map<String, Object> normalization = (Map<String, Object>) fieldConfig.get("normalization");
            if (normalization != null) {
                String positiveValue = (String) normalization.get("positive");
                String negativeValue = (String) normalization.get("negative");
                Map<String, String> customMappings = (Map<String, String>) normalization.get("custom");

                if (positiveValue != null && negativeValue != null) {
                    NormalizationConfig config = new NormalizationConfig(positiveValue, negativeValue, customMappings);
                    normalizer.addFieldConfig(fieldName, config);
                }
            }
        }

        LogUtil.info(CLASS_NAME, "Created normalizer from service config with " + normalizer.fieldConfigs.size() + " field configurations");
        return normalizer;
    }
}