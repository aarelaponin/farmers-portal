package global.govstack.registration.sender.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.joget.commons.util.LogUtil;

/**
 * Utility class for extracting values from JSON using simple path notation
 */
public class JsonPathExtractor {
    private static final String CLASS_NAME = JsonPathExtractor.class.getName();

    /**
     * Extract a string value from JSON using dot notation path
     * @param node The JSON node to extract from
     * @param path The path (e.g., "name.given[0]", "extension.agriculturalData.crops")
     * @return The extracted value as string, or null if not found
     */
    public static String extractValue(JsonNode node, String path) {
        if (node == null || path == null) {
            return null;
        }

        try {
            JsonNode result = extractNode(node, path);
            if (result != null && !result.isNull()) {
                if (result.isTextual()) {
                    return result.asText();
                } else if (result.isNumber()) {
                    return String.valueOf(result.numberValue());
                } else if (result.isBoolean()) {
                    return String.valueOf(result.booleanValue());
                } else {
                    return result.toString();
                }
            }
        } catch (Exception e) {
            LogUtil.debug(CLASS_NAME, "Error extracting value at path " + path + ": " + e.getMessage());
        }

        return null;
    }

    /**
     * Extract a JSON node from JSON using dot notation path
     * @param node The JSON node to extract from
     * @param path The path (e.g., "name.given[0]", "extension.agriculturalData.crops")
     * @return The extracted node, or null if not found
     */
    public static JsonNode extractNode(JsonNode node, String path) {
        if (node == null || path == null) {
            return null;
        }

        String[] parts = path.split("\\.");
        JsonNode current = node;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            // Handle array notation like "given[0]"
            if (part.contains("[") && part.contains("]")) {
                int bracketIndex = part.indexOf("[");
                String fieldName = part.substring(0, bracketIndex);
                String indexStr = part.substring(bracketIndex + 1, part.length() - 1);

                current = current.get(fieldName);
                if (current != null && current.isArray()) {
                    try {
                        int index = Integer.parseInt(indexStr);
                        current = current.get(index);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                current = current.get(part);
            }
        }

        return current;
    }
}