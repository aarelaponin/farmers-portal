package global.govstack.registration.sender.service.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import global.govstack.registration.sender.service.transform.TransformationService;
import global.govstack.registration.sender.util.JsonBuilder;
import org.joget.commons.util.LogUtil;

import java.util.List;
import java.util.Map;

/**
 * Encodes Joget form data into GovStack-compliant JSON format using services.yml metadata
 *
 * This is the reverse of the decoding process in ProcessingAPI - it takes Joget form data
 * and converts it to the standardized GovStack JSON format
 */
public class GovStackJsonEncoder {

    private static final String CLASS_NAME = GovStackJsonEncoder.class.getName();

    private final YamlMetadataService metadataService;
    private final TransformationService transformationService;
    private final ObjectMapper mapper;

    public GovStackJsonEncoder() {
        this.metadataService = new YamlMetadataService();
        this.transformationService = new TransformationService();
        this.mapper = new ObjectMapper();
    }

    public GovStackJsonEncoder(YamlMetadataService metadataService) {
        this.metadataService = metadataService;
        this.transformationService = new TransformationService();
        this.mapper = new ObjectMapper();
    }

    /**
     * Encode form data to GovStack JSON format
     *
     * @param formData Data extracted from Joget forms (organized by section)
     * @return GovStack-compliant JSON string
     */
    public String encodeToGovStackJson(Map<String, Object> formData) {
        try {
            LogUtil.info(CLASS_NAME, "Starting encoding of form data to GovStack JSON");

            // Create the JSON structure
            JsonBuilder builder = new JsonBuilder();

            // Get all form mappings from metadata
            Map<String, Object> formMappings = metadataService.getFormMappings();

            if (formMappings == null || formMappings.isEmpty()) {
                LogUtil.error(CLASS_NAME, null, "No form mappings found in metadata");
                return null;
            }

            // Process each form section
            for (Map.Entry<String, Object> entry : formMappings.entrySet()) {
                String sectionName = entry.getKey();
                Map<String, Object> sectionConfig = (Map<String, Object>) entry.getValue();

                if (sectionConfig == null) {
                    continue;
                }

                // Get the section data from form data
                Object sectionData = formData.get(sectionName);

                if (sectionData == null) {
                    LogUtil.info(CLASS_NAME, "No data found for section: " + sectionName);
                    continue;
                }

                String type = (String) sectionConfig.get("type");

                if ("array".equals(type)) {
                    // Process grid/array data
                    processArraySection(builder, sectionName, sectionConfig, sectionData);
                } else {
                    // Process regular form data
                    processFormSection(builder, sectionName, sectionConfig, sectionData);
                }
            }

            // Add metadata
            addMetadata(builder, formData);

            // Convert to JSON string
            String json = builder.toPrettyJsonString();

            LogUtil.info(CLASS_NAME, "Successfully encoded form data to GovStack JSON");

            // DEBUG: Log the actual JSON being sent to verify structure
            LogUtil.info(CLASS_NAME, "=== SENDING JSON TO PROCESSING API ===");
            LogUtil.info(CLASS_NAME, json);
            LogUtil.info(CLASS_NAME, "=== END JSON ===");

            return json;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error encoding to GovStack JSON");
            return null;
        }
    }

    /**
     * Process a regular form section
     */
    private void processFormSection(JsonBuilder builder, String sectionName,
                                   Map<String, Object> sectionConfig, Object sectionData) {

        if (!(sectionData instanceof Map)) {
            LogUtil.warn(CLASS_NAME, "Section data is not a Map for: " + sectionName);
            return;
        }

        Map<String, Object> dataMap = (Map<String, Object>) sectionData;
        List<Map<String, Object>> fields = (List<Map<String, Object>>) sectionConfig.get("fields");

        if (fields == null) {
            return;
        }

        LogUtil.info(CLASS_NAME, "Processing form section: " + sectionName + " with " + fields.size() + " fields");

        for (Map<String, Object> field : fields) {
            processField(builder, field, dataMap);
        }
    }

    /**
     * Process an array/grid section
     */
    private void processArraySection(JsonBuilder builder, String sectionName,
                                    Map<String, Object> sectionConfig, Object sectionData) {

        if (!(sectionData instanceof List)) {
            LogUtil.warn(CLASS_NAME, "Section data is not a List for array section: " + sectionName);
            return;
        }

        List<Map<String, Object>> dataList = (List<Map<String, Object>>) sectionData;
        String govstackPath = (String) sectionConfig.get("govstack");

        if (govstackPath == null) {
            LogUtil.warn(CLASS_NAME, "No govstack path defined for array section: " + sectionName);
            return;
        }

        // Check control field if defined
        String controlField = (String) sectionConfig.get("controlField");
        String controlValue = (String) sectionConfig.get("controlValue");

        if (controlField != null && controlValue != null) {
            // Check if control field matches required value
            // Note: The control field would be in the parent form data
            // For now, we'll process the array anyway
            LogUtil.info(CLASS_NAME, "Control field check for " + sectionName + ": " + controlField + " = " + controlValue);
        }

        LogUtil.info(CLASS_NAME, "Processing array section: " + sectionName + " with " + dataList.size() + " items");

        List<Map<String, Object>> fields = (List<Map<String, Object>>) sectionConfig.get("fields");

        if (fields == null) {
            return;
        }

        // Create array for this section
        ArrayNode arrayNode = mapper.createArrayNode();

        // Process each item in the array
        for (Map<String, Object> itemData : dataList) {
            ObjectNode itemNode = mapper.createObjectNode();

            // Process fields for this item
            for (Map<String, Object> field : fields) {
                // Support both field_id and joget
                String fieldId = (String) field.get("field_id");
                String jogetField = (String) field.get("joget");
                String fieldName = fieldId != null ? fieldId : jogetField;

                // Prioritize jsonPath for compatibility with ProcessingAPI
                String jsonPath = (String) field.get("jsonPath");
                String govstackField = (String) field.get("govstack");
                String targetFieldPath = jsonPath != null ? jsonPath : govstackField;

                if (fieldName == null || targetFieldPath == null) {
                    continue;
                }

                // Get the value from item data
                Object value = itemData.get(fieldName);

                if (value != null) {
                    // Apply transformations
                    value = applyFieldTransformations(field, value);

                    // Add to item node (handle nested paths within the item)
                    setNestedValue(itemNode, targetFieldPath, value);
                }
            }

            arrayNode.add(itemNode);
        }

        // Add array to main JSON
        builder.setValue(govstackPath, arrayNode);
    }

    /**
     * Process a single field
     */
    private void processField(JsonBuilder builder, Map<String, Object> field, Map<String, Object> dataMap) {
        // Support both field_id (from form_structure.yaml) and joget (from services.yml)
        String fieldId = (String) field.get("field_id");
        String jogetField = (String) field.get("joget");
        String fieldName = fieldId != null ? fieldId : jogetField;

        // Prioritize jsonPath for compatibility with ProcessingAPI which tries jsonPath first
        String jsonPath = (String) field.get("jsonPath");
        String govstackPath = (String) field.get("govstack");
        String targetPath = jsonPath != null ? jsonPath : govstackPath;

        if (fieldName == null || targetPath == null) {
            return;
        }

        // Get the value from form data
        Object value = dataMap.get(fieldName);

        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
            // Skip null or empty values unless field is required
            Boolean required = (Boolean) field.get("required");
            if (Boolean.TRUE.equals(required)) {
                LogUtil.warn(CLASS_NAME, "Required field missing: " + fieldName);
            }
            return;
        }

        // Apply transformations
        value = applyFieldTransformations(field, value);

        // Set the value in the JSON structure
        builder.setValue(targetPath, value);

        // Handle additional type fields (e.g., for identifiers)
        String govstackType = (String) field.get("govstackType");
        String typeValue = (String) field.get("typeValue");

        if (govstackType != null && typeValue != null) {
            builder.setValue(govstackType, typeValue);
        }

        LogUtil.info(CLASS_NAME, "Encoded field " + fieldName + " -> " + targetPath + " = " + value);
    }

    /**
     * Apply transformations to a field value
     */
    private Object applyFieldTransformations(Map<String, Object> field, Object value) {
        // Apply transformation if defined
        String transform = (String) field.get("transform");
        if (transform != null) {
            value = transformationService.encode(value, transform);
        }

        // Apply value mapping if defined
        Map<String, Object> valueMapping = (Map<String, Object>) field.get("valueMapping");
        if (valueMapping != null) {
            value = transformationService.applyValueMapping(value, valueMapping, "encode");
        }

        return value;
    }

    /**
     * Set a nested value in an ObjectNode
     */
    private void setNestedValue(ObjectNode node, String path, Object value) {
        String[] parts = path.split("\\.");

        ObjectNode current = node;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];

            if (!current.has(part)) {
                current.set(part, mapper.createObjectNode());
            }
            JsonNode child = current.get(part);
            if (child instanceof ObjectNode) {
                current = (ObjectNode) child;
            }
        }

        String lastPart = parts[parts.length - 1];

        // Set the value
        if (value == null) {
            current.putNull(lastPart);
        } else if (value instanceof String) {
            current.put(lastPart, (String) value);
        } else if (value instanceof Boolean) {
            current.put(lastPart, (Boolean) value);
        } else if (value instanceof Number) {
            if (value instanceof Integer) {
                current.put(lastPart, (Integer) value);
            } else if (value instanceof Long) {
                current.put(lastPart, (Long) value);
            } else if (value instanceof Double) {
                current.put(lastPart, (Double) value);
            } else {
                current.put(lastPart, value.toString());
            }
        } else if (value instanceof List) {
            ArrayNode array = mapper.createArrayNode();
            for (Object item : (List<?>) value) {
                if (item instanceof String) {
                    array.add((String) item);
                } else {
                    array.add(mapper.valueToTree(item));
                }
            }
            current.set(lastPart, array);
        } else {
            current.set(lastPart, mapper.valueToTree(value));
        }
    }

    /**
     * Add metadata fields to the JSON
     */
    private void addMetadata(JsonBuilder builder, Map<String, Object> formData) {
        // Add timestamp
        builder.setValue("timestamp", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .format(new java.util.Date()));

        // Add record ID if available
        Object id = formData.get("id");
        if (id != null) {
            builder.setValue("id", id.toString());
        }

        // Add service metadata
        Map<String, Object> serviceMetadata = metadataService.getServiceMetadata();
        if (serviceMetadata != null) {
            String serviceId = (String) serviceMetadata.get("id");
            String serviceVersion = (String) serviceMetadata.get("version");
            String metadataVersion = (String) serviceMetadata.get("metadataVersion");

            if (serviceId != null) {
                builder.setValue("serviceId", serviceId);
            }
            if (serviceVersion != null) {
                builder.setValue("serviceVersion", serviceVersion);
            }
            if (metadataVersion != null) {
                builder.setValue("metadataVersion", metadataVersion);
                LogUtil.info(CLASS_NAME, "Added metadataVersion to payload: " + metadataVersion);
            }
        }
    }

    /**
     * Wrap the JSON in a test data format if needed (for compatibility with ProcessingAPI)
     */
    public String wrapInTestDataFormat(String json) {
        try {
            ObjectNode wrapper = mapper.createObjectNode();
            ArrayNode testData = mapper.createArrayNode();

            JsonNode dataNode = mapper.readTree(json);
            testData.add(dataNode);

            wrapper.set("testData", testData);

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error wrapping in test data format");
            return json;
        }
    }
}