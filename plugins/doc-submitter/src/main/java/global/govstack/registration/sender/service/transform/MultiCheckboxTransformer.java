package global.govstack.registration.sender.service.transform;

import org.joget.commons.util.LogUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Transformer for multi-checkbox/multi-select values between Joget and GovStack formats
 *
 * Joget format: Comma-separated string (e.g., "option1,option2,option3")
 * GovStack format: Array of strings ["option1", "option2", "option3"]
 */
public class MultiCheckboxTransformer implements DataTransformer {

    private static final String CLASS_NAME = MultiCheckboxTransformer.class.getName();

    @Override
    public Object encode(Object jogetValue, String transformType) {
        if (jogetValue == null || jogetValue.toString().trim().isEmpty()) {
            return new ArrayList<>();
        }

        String value = jogetValue.toString().trim();

        // Split comma-separated values and convert to list
        // Handle both comma and semicolon as separators
        String[] items;
        if (value.contains(";")) {
            items = value.split(";");
        } else {
            items = value.split(",");
        }

        // Trim each item and filter out empty strings
        List<String> result = Arrays.stream(items)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        LogUtil.info(CLASS_NAME, "Encoded multi-checkbox from '" + value + "' to array with " + result.size() + " items");

        return result;
    }

    @Override
    public Object decode(Object govstackValue, String transformType) {
        if (govstackValue == null) {
            return "";
        }

        // Handle array/list input
        if (govstackValue instanceof List) {
            List<?> list = (List<?>) govstackValue;
            // Join with semicolon separator (Joget's preferred format for multi-values)
            String result = list.stream()
                    .filter(item -> item != null)
                    .map(Object::toString)
                    .collect(Collectors.joining(";"));

            LogUtil.info(CLASS_NAME, "Decoded array with " + list.size() + " items to '" + result + "'");
            return result;
        }

        // Handle array input
        if (govstackValue.getClass().isArray()) {
            Object[] array = (Object[]) govstackValue;
            String result = Arrays.stream(array)
                    .filter(item -> item != null)
                    .map(Object::toString)
                    .collect(Collectors.joining(";"));

            LogUtil.info(CLASS_NAME, "Decoded array to '" + result + "'");
            return result;
        }

        // If already a string, check if it needs conversion
        String value = govstackValue.toString();

        // If it looks like JSON array, parse it
        if (value.startsWith("[") && value.endsWith("]")) {
            // Simple parsing for JSON array string
            value = value.substring(1, value.length() - 1); // Remove brackets
            value = value.replaceAll("\"", ""); // Remove quotes
            // Already comma-separated, convert to semicolon
            value = value.replaceAll(",", ";");
        }

        return value;
    }

    @Override
    public boolean supports(String transformType) {
        return "multiCheckbox".equalsIgnoreCase(transformType) ||
               "multicheckbox".equalsIgnoreCase(transformType) ||
               "multiselect".equalsIgnoreCase(transformType) ||
               "array".equalsIgnoreCase(transformType) ||
               "list".equalsIgnoreCase(transformType);
    }
}