package global.govstack.registration.sender.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joget.commons.util.LogUtil;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for building nested JSON structures using dot notation paths
 *
 * This is the complement to JsonPathExtractor - it builds JSON structures
 * from path-value pairs, supporting nested objects and arrays
 */
public class JsonBuilder {

    private static final String CLASS_NAME = JsonBuilder.class.getName();
    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("\\[(\\d+)\\]");

    private final ObjectMapper mapper;
    private final ObjectNode root;

    public JsonBuilder() {
        this.mapper = new ObjectMapper();
        this.root = mapper.createObjectNode();
    }

    /**
     * Set a value at the specified path in the JSON structure
     *
     * @param path The dot notation path (e.g., "name.given[0]", "address[0].city")
     * @param value The value to set
     */
    public void setValue(String path, Object value) {
        if (path == null || path.trim().isEmpty()) {
            LogUtil.warn(CLASS_NAME, "Empty path provided");
            return;
        }

        if (value == null) {
            // Skip null values
            return;
        }

        try {
            String[] parts = path.split("\\.");
            JsonNode current = root;

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean isLastPart = (i == parts.length - 1);

                // Check if this part contains array notation
                Matcher matcher = ARRAY_INDEX_PATTERN.matcher(part);
                if (matcher.find()) {
                    // Handle array access
                    String fieldName = part.substring(0, part.indexOf('['));
                    int index = Integer.parseInt(matcher.group(1));

                    current = ensureArray(current, fieldName, index);

                    if (isLastPart) {
                        // Set the value in the array
                        setArrayValue((ArrayNode) current, index, value);
                    } else {
                        // Navigate into the array element
                        current = ensureArrayElement((ArrayNode) current, index);
                    }
                } else {
                    // Handle object field
                    if (isLastPart) {
                        // Set the field value
                        setFieldValue((ObjectNode) current, part, value);
                    } else {
                        // Navigate or create nested object
                        current = ensureObject(current, part);
                    }
                }
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error setting value at path: " + path);
        }
    }

    /**
     * Add an item to an array at the specified path
     *
     * @param path The path to the array (e.g., "extension.crops")
     * @param item The item to add to the array
     */
    public void addArrayItem(String path, Object item) {
        if (path == null || path.trim().isEmpty() || item == null) {
            return;
        }

        try {
            JsonNode node = navigateToPath(path, true);
            if (node instanceof ArrayNode) {
                ArrayNode array = (ArrayNode) node;
                if (item instanceof ObjectNode) {
                    array.add((ObjectNode) item);
                } else if (item instanceof JsonNode) {
                    array.add((JsonNode) item);
                } else {
                    // Convert to JSON node
                    JsonNode itemNode = mapper.valueToTree(item);
                    array.add(itemNode);
                }
            } else {
                LogUtil.warn(CLASS_NAME, "Path does not point to an array: " + path);
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error adding array item at path: " + path);
        }
    }

    /**
     * Create a new object node that can be added to the structure
     *
     * @return A new ObjectNode
     */
    public ObjectNode createObjectNode() {
        return mapper.createObjectNode();
    }

    /**
     * Create a new array node that can be added to the structure
     *
     * @return A new ArrayNode
     */
    public ArrayNode createArrayNode() {
        return mapper.createArrayNode();
    }

    /**
     * Get the built JSON structure as a JsonNode
     *
     * @return The root JsonNode
     */
    public JsonNode getJsonNode() {
        return root;
    }

    /**
     * Get the built JSON structure as a string
     *
     * @return The JSON string
     */
    public String toJsonString() {
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error converting to JSON string");
            return "{}";
        }
    }

    /**
     * Get the built JSON structure as a pretty-printed string
     *
     * @return The pretty-printed JSON string
     */
    public String toPrettyJsonString() {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error converting to pretty JSON string");
            return "{}";
        }
    }

    // Helper methods

    private JsonNode navigateToPath(String path, boolean createArray) {
        String[] parts = path.split("\\.");
        JsonNode current = root;

        for (String part : parts) {
            if (part.contains("[")) {
                // Array notation - not handling here for simplicity
                continue;
            }

            if (current instanceof ObjectNode) {
                ObjectNode obj = (ObjectNode) current;
                if (!obj.has(part)) {
                    if (createArray) {
                        obj.set(part, mapper.createArrayNode());
                    } else {
                        obj.set(part, mapper.createObjectNode());
                    }
                }
                current = obj.get(part);
            }
        }

        return current;
    }

    private JsonNode ensureObject(JsonNode parent, String fieldName) {
        if (parent instanceof ObjectNode) {
            ObjectNode objParent = (ObjectNode) parent;
            if (!objParent.has(fieldName)) {
                objParent.set(fieldName, mapper.createObjectNode());
            }
            JsonNode child = objParent.get(fieldName);
            if (!(child instanceof ObjectNode)) {
                // Replace with object if it's not already
                ObjectNode newObj = mapper.createObjectNode();
                objParent.set(fieldName, newObj);
                return newObj;
            }
            return child;
        }
        return parent;
    }

    private JsonNode ensureArray(JsonNode parent, String fieldName, int index) {
        if (parent instanceof ObjectNode) {
            ObjectNode objParent = (ObjectNode) parent;
            if (!objParent.has(fieldName)) {
                objParent.set(fieldName, mapper.createArrayNode());
            }
            JsonNode child = objParent.get(fieldName);
            if (!(child instanceof ArrayNode)) {
                // Replace with array if it's not already
                ArrayNode newArray = mapper.createArrayNode();
                objParent.set(fieldName, newArray);
                return newArray;
            }
            return child;
        }
        return parent;
    }

    private JsonNode ensureArrayElement(ArrayNode array, int index) {
        // Ensure the array has enough elements
        while (array.size() <= index) {
            array.add(mapper.createObjectNode());
        }
        return array.get(index);
    }

    private void setFieldValue(ObjectNode obj, String fieldName, Object value) {
        if (value == null) {
            obj.putNull(fieldName);
        } else if (value instanceof String) {
            obj.put(fieldName, (String) value);
        } else if (value instanceof Boolean) {
            obj.put(fieldName, (Boolean) value);
        } else if (value instanceof Integer) {
            obj.put(fieldName, (Integer) value);
        } else if (value instanceof Long) {
            obj.put(fieldName, (Long) value);
        } else if (value instanceof Double) {
            obj.put(fieldName, (Double) value);
        } else if (value instanceof Float) {
            obj.put(fieldName, (Float) value);
        } else if (value instanceof List) {
            ArrayNode array = mapper.createArrayNode();
            for (Object item : (List<?>) value) {
                if (item instanceof String) {
                    array.add((String) item);
                } else {
                    array.add(mapper.valueToTree(item));
                }
            }
            obj.set(fieldName, array);
        } else if (value instanceof JsonNode) {
            obj.set(fieldName, (JsonNode) value);
        } else {
            // Convert complex objects to JsonNode
            obj.set(fieldName, mapper.valueToTree(value));
        }
    }

    private void setArrayValue(ArrayNode array, int index, Object value) {
        // Ensure array has enough elements
        while (array.size() <= index) {
            array.addNull();
        }

        // Remove existing element at index
        array.remove(index);

        // Insert new value at index
        if (value == null) {
            array.insertNull(index);
        } else if (value instanceof String) {
            array.insert(index, (String) value);
        } else if (value instanceof Boolean) {
            array.insert(index, (Boolean) value);
        } else if (value instanceof Integer) {
            array.insert(index, (Integer) value);
        } else if (value instanceof Long) {
            array.insert(index, (Long) value);
        } else if (value instanceof Double) {
            array.insert(index, (Double) value);
        } else if (value instanceof JsonNode) {
            array.insert(index, (JsonNode) value);
        } else {
            array.insert(index, mapper.valueToTree(value));
        }
    }
}