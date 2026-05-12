package global.govstack.registration.receiver.service.normalization;

import com.fasterxml.jackson.databind.JsonNode;
import org.joget.commons.util.LogUtil;

/**
 * Detects the format of incoming values to enable proper normalization.
 * Supports multiple formats for backward compatibility with test-data.json
 * and forward compatibility with DocSubmitter transformations.
 */
public class ValueFormatDetector {

    private static final String CLASS_NAME = ValueFormatDetector.class.getName();

    /**
     * Supported value formats
     */
    public enum Format {
        LOV_NUMERIC,     // "1", "2" - Joget LOV numeric values
        LOV_TEXT,        // "yes", "no" - Joget LOV text values
        BOOLEAN,         // true, false - Boolean JSON values
        BOOLEAN_STRING,  // "true", "false" - Boolean as strings
        CUSTOM,          // Other string values
        NULL             // Null or missing values
    }

    /**
     * Detect the format of a JSON value
     *
     * @param value The JSON node to analyze
     * @return The detected format
     */
    public Format detectFormat(JsonNode value) {
        if (value == null || value.isNull()) {
            return Format.NULL;
        }

        if (value.isBoolean()) {
            LogUtil.debug(CLASS_NAME, "Detected BOOLEAN format: " + value);
            return Format.BOOLEAN;
        }

        if (value.isTextual()) {
            String text = value.asText().toLowerCase().trim();

            // Check for boolean strings
            if ("true".equals(text) || "false".equals(text)) {
                LogUtil.debug(CLASS_NAME, "Detected BOOLEAN_STRING format: " + text);
                return Format.BOOLEAN_STRING;
            }

            // Check for numeric LOV values
            if ("1".equals(text) || "2".equals(text)) {
                LogUtil.debug(CLASS_NAME, "Detected LOV_NUMERIC format: " + text);
                return Format.LOV_NUMERIC;
            }

            // Check for text LOV values
            if ("yes".equals(text) || "no".equals(text)) {
                LogUtil.debug(CLASS_NAME, "Detected LOV_TEXT format: " + text);
                return Format.LOV_TEXT;
            }

            // Other string values
            LogUtil.debug(CLASS_NAME, "Detected CUSTOM format: " + text);
            return Format.CUSTOM;
        }

        // Handle numeric values (might be 1 or 2 as numbers)
        if (value.isNumber()) {
            int numValue = value.asInt();
            if (numValue == 1 || numValue == 2) {
                LogUtil.debug(CLASS_NAME, "Detected LOV_NUMERIC format (from number): " + numValue);
                return Format.LOV_NUMERIC;
            }
        }

        LogUtil.debug(CLASS_NAME, "Unknown format for value: " + value);
        return Format.CUSTOM;
    }

    /**
     * Check if a value represents a "yes/true/1" value in any format
     *
     * @param value The JSON value to check
     * @return true if the value represents yes/true/1
     */
    public boolean isPositiveValue(JsonNode value) {
        Format format = detectFormat(value);

        switch (format) {
            case BOOLEAN:
                return value.asBoolean();

            case BOOLEAN_STRING:
                return "true".equalsIgnoreCase(value.asText());

            case LOV_NUMERIC:
                String numStr = value.isNumber() ? String.valueOf(value.asInt()) : value.asText();
                return "1".equals(numStr);

            case LOV_TEXT:
                return "yes".equalsIgnoreCase(value.asText());

            default:
                return false;
        }
    }

    /**
     * Check if a value represents a "no/false/2" value in any format
     *
     * @param value The JSON value to check
     * @return true if the value represents no/false/2
     */
    public boolean isNegativeValue(JsonNode value) {
        Format format = detectFormat(value);

        switch (format) {
            case BOOLEAN:
                return !value.asBoolean();

            case BOOLEAN_STRING:
                return "false".equalsIgnoreCase(value.asText());

            case LOV_NUMERIC:
                String numStr = value.isNumber() ? String.valueOf(value.asInt()) : value.asText();
                return "2".equals(numStr);

            case LOV_TEXT:
                return "no".equalsIgnoreCase(value.asText());

            default:
                return false;
        }
    }

    /**
     * Get a string representation of the format
     *
     * @param format The format to describe
     * @return Human-readable description
     */
    public String getFormatDescription(Format format) {
        switch (format) {
            case LOV_NUMERIC:
                return "Numeric LOV (1/2)";
            case LOV_TEXT:
                return "Text LOV (yes/no)";
            case BOOLEAN:
                return "Boolean (true/false)";
            case BOOLEAN_STRING:
                return "Boolean string (\"true\"/\"false\")";
            case CUSTOM:
                return "Custom string value";
            case NULL:
                return "Null/missing value";
            default:
                return "Unknown format";
        }
    }
}