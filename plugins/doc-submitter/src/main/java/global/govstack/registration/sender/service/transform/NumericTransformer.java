package global.govstack.registration.sender.service.transform;

import org.joget.commons.util.LogUtil;

/**
 * Transformer for numeric values between Joget and GovStack formats
 *
 * Handles conversion between string and numeric representations
 */
public class NumericTransformer implements DataTransformer {

    private static final String CLASS_NAME = NumericTransformer.class.getName();

    @Override
    public Object encode(Object jogetValue, String transformType) {
        if (jogetValue == null || jogetValue.toString().trim().isEmpty()) {
            return null;
        }

        String value = jogetValue.toString().trim();

        try {
            // Check if it contains a decimal point
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                // Try to parse as Long first to handle large numbers
                return Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            LogUtil.warn(CLASS_NAME, "Failed to encode numeric value: " + value + " - " + e.getMessage());
            // Return original value if parsing fails
            return value;
        }
    }

    @Override
    public Object decode(Object govstackValue, String transformType) {
        if (govstackValue == null) {
            return "";
        }

        // If already a string, return as is
        if (govstackValue instanceof String) {
            return govstackValue;
        }

        // Convert numeric types to string for Joget
        return govstackValue.toString();
    }

    @Override
    public boolean supports(String transformType) {
        return "numeric".equalsIgnoreCase(transformType) ||
               "number".equalsIgnoreCase(transformType) ||
               "integer".equalsIgnoreCase(transformType) ||
               "decimal".equalsIgnoreCase(transformType) ||
               "double".equalsIgnoreCase(transformType) ||
               "float".equalsIgnoreCase(transformType);
    }
}