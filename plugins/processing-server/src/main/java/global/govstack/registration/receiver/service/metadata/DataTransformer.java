package global.govstack.registration.receiver.service.metadata;

import org.joget.commons.util.LogUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Service to transform data based on metadata rules
 */
public class DataTransformer {
    private static final String CLASS_NAME = DataTransformer.class.getName();

    // Date format patterns
    private static final String ISO_8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String ISO_DATE_PATTERN = "yyyy-MM-dd";
    private static final String JOGET_DATE_PATTERN = "yyyy-MM-dd";

    /**
     * Transform a value based on the transformation type
     * @param value The original value
     * @param transformationType The type of transformation to apply
     * @return The transformed value
     */
    public String transformValue(String value, String transformationType) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }

        if (transformationType == null || transformationType.trim().isEmpty()) {
            return value;
        }

        try {
            switch (transformationType.toLowerCase()) {
                case "date_iso8601":
                    return transformDate(value);

                case "yesnoboolean":
                    return transformBooleanToYesNo(value);

                case "numeric":
                    return transformToNumeric(value);

                case "multicheckbox":
                    return transformMultiCheckbox(value);

                default:
                    LogUtil.debug(CLASS_NAME, "Unknown transformation type: " + transformationType);
                    return value;
            }
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "Error transforming value: " + value + " with transformation: " + transformationType + ". Error: " + e.getMessage());
            return value;
        }
    }

    /**
     * Apply value mapping to a field
     * @param value The original value
     * @param valueMapping The mapping configuration
     * @return The mapped value
     */
    public String applyValueMapping(String value, Map<String, String> valueMapping) {
        if (value == null || valueMapping == null || valueMapping.isEmpty()) {
            return value;
        }

        // Direct mapping
        if (valueMapping.containsKey(value)) {
            String mappedValue = valueMapping.get(value);
            LogUtil.debug(CLASS_NAME, "Mapped value: " + value + " -> " + mappedValue);
            return mappedValue;
        }

        // Try boolean to string mapping
        if ("true".equalsIgnoreCase(value) && valueMapping.containsKey("true")) {
            return valueMapping.get("true");
        }
        if ("false".equalsIgnoreCase(value) && valueMapping.containsKey("false")) {
            return valueMapping.get("false");
        }

        // Try numeric string mapping (e.g., "1" -> "rural")
        String trimmedValue = value.trim();
        if (valueMapping.containsKey(trimmedValue)) {
            return valueMapping.get(trimmedValue);
        }

        // Return original value if no mapping found
        LogUtil.debug(CLASS_NAME, "No mapping found for value: " + value);
        return value;
    }

    /**
     * Transform date from ISO 8601 to Joget format
     * @param dateValue The date value to transform
     * @return The transformed date string
     */
    private String transformDate(String dateValue) {
        if (dateValue == null || dateValue.trim().isEmpty()) {
            return dateValue;
        }

        try {
            Date date = null;

            // Try parsing as full ISO 8601 with time
            if (dateValue.contains("T")) {
                SimpleDateFormat isoFormat = new SimpleDateFormat(ISO_8601_PATTERN);
                date = isoFormat.parse(dateValue);
            } else {
                // Try parsing as simple date format
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(ISO_DATE_PATTERN);
                date = simpleDateFormat.parse(dateValue);
            }

            // Format to Joget format
            SimpleDateFormat jogetFormat = new SimpleDateFormat(JOGET_DATE_PATTERN);
            return jogetFormat.format(date);

        } catch (ParseException e) {
            LogUtil.warn(CLASS_NAME, "Could not parse date: " + dateValue + ". Returning original value.");
            return dateValue;
        }
    }

    /**
     * Transform boolean value to yes/no
     * @param booleanValue The boolean value
     * @return "yes" or "no"
     */
    private String transformBooleanToYesNo(String booleanValue) {
        if (booleanValue == null) {
            return "no";
        }

        String lowerValue = booleanValue.toLowerCase().trim();

        if ("true".equals(lowerValue) || "1".equals(lowerValue) || "yes".equals(lowerValue)) {
            return "yes";
        }

        if ("false".equals(lowerValue) || "0".equals(lowerValue) || "no".equals(lowerValue)) {
            return "no";
        }

        return booleanValue;
    }

    /**
     * Transform value to numeric string
     * @param value The value to transform
     * @return The numeric string
     */
    private String transformToNumeric(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "0";
        }

        // Remove non-numeric characters except decimal point and minus sign
        String cleaned = value.replaceAll("[^0-9.-]", "");

        // Validate the cleaned string
        try {
            Double.parseDouble(cleaned);
            return cleaned;
        } catch (NumberFormatException e) {
            LogUtil.warn(CLASS_NAME, "Could not parse numeric value: " + value);
            return "0";
        }
    }

    /**
     * Transform multi-checkbox values
     * @param value The value to transform (could be array or comma-separated)
     * @return The transformed value suitable for Joget multi-checkbox
     */
    private String transformMultiCheckbox(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        // If it's already a JSON array string, parse it
        if (value.trim().startsWith("[") && value.trim().endsWith("]")) {
            try {
                // Simple JSON array parsing
                String trimmed = value.trim();
                trimmed = trimmed.substring(1, trimmed.length() - 1); // Remove [ and ]
                String[] values = trimmed.split(",");
                List<String> cleanedValues = new ArrayList<>();

                for (String v : values) {
                    String cleaned = v.trim().replaceAll("\"", "");
                    if (!cleaned.isEmpty()) {
                        cleanedValues.add(cleaned);
                    }
                }

                // Joget expects semicolon-separated values for multi-checkbox
                return String.join(";", cleanedValues);
            } catch (Exception e) {
                LogUtil.warn(CLASS_NAME, "Error parsing JSON array: " + value);
                return value;
            }
        }

        // If it's comma-separated, convert to semicolon-separated
        if (value.contains(",")) {
            String[] values = value.split(",");
            List<String> cleanedValues = new ArrayList<>();

            for (String v : values) {
                String cleaned = v.trim();
                if (!cleaned.isEmpty()) {
                    cleanedValues.add(cleaned);
                }
            }

            return String.join(";", cleanedValues);
        }

        // Return as-is if it's a single value
        return value;
    }

    /**
     * Transform an object value to string
     * @param value The object value
     * @param transformationType The transformation type
     * @return The string representation
     */
    public String transformObjectToString(Object value, String transformationType) {
        if (value == null) {
            return "";
        }

        // Handle boolean type
        if (value instanceof Boolean) {
            String boolStr = value.toString();
            if ("yesnoboolean".equalsIgnoreCase(transformationType)) {
                return transformBooleanToYesNo(boolStr);
            }
            return boolStr;
        }

        // Handle numeric types
        if (value instanceof Number) {
            return value.toString();
        }

        // Handle arrays/lists
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> stringList = new ArrayList<>();
            for (Object item : list) {
                stringList.add(String.valueOf(item));
            }

            if ("multicheckbox".equalsIgnoreCase(transformationType)) {
                return String.join(";", stringList);
            }
            return String.join(",", stringList);
        }

        // Default to string representation
        String stringValue = value.toString();

        // Apply transformation if specified
        if (transformationType != null && !transformationType.isEmpty()) {
            return transformValue(stringValue, transformationType);
        }

        return stringValue;
    }
}