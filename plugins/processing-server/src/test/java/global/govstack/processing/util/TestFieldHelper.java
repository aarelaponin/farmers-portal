package global.govstack.processing.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import global.govstack.registration.receiver.service.metadata.YamlMetadataService;
import global.govstack.registration.receiver.util.JsonPathExtractor;
import org.junit.Assert;

import java.io.InputStream;
import java.util.*;

/**
 * Helper class for configuration-driven testing.
 * Removes hardcoded field names from tests by reading from YAML configuration.
 */
public class TestFieldHelper {
    private final YamlMetadataService metadataService;
    private final Map<String, Map<String, Object>> fieldMappings = new HashMap<>();
    private final JsonNode testData;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TestFieldHelper(YamlMetadataService metadataService) throws Exception {
        this.metadataService = metadataService;
        loadFieldMappings();
        this.testData = loadTestData();
    }

    /**
     * Load all field mappings from YAML metadata
     */
    private void loadFieldMappings() {
        Map<String, Object> allMappings = metadataService.getAllFormMappings();

        for (Map.Entry<String, Object> entry : allMappings.entrySet()) {
            String sectionName = entry.getKey();
            Map<String, Object> section = (Map<String, Object>) entry.getValue();

            if (!"array".equals(section.get("type"))) {
                List<Map<String, Object>> fields = (List<Map<String, Object>>) section.get("fields");
                if (fields != null) {
                    for (Map<String, Object> field : fields) {
                        String jogetField = (String) field.get("joget");
                        String govstackPath = (String) field.get("govstack");
                        if (jogetField != null && govstackPath != null) {
                            Map<String, Object> fieldInfo = new HashMap<>();
                            fieldInfo.put("jogetField", jogetField);
                            fieldInfo.put("govstackPath", govstackPath);
                            fieldInfo.put("section", sectionName);
                            fieldInfo.putAll(field);
                            fieldMappings.put(govstackPath, fieldInfo);
                        }
                    }
                }
            }
        }
    }

    /**
     * Load test data from JSON file
     */
    private JsonNode loadTestData() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("docs-metadata/test-data.json");
        if (is == null) {
            throw new RuntimeException("Test data file not found");
        }
        JsonNode root = objectMapper.readTree(is);
        if (root.has("testData") && root.get("testData").isArray()) {
            return root.get("testData").get(0);
        }
        return root;
    }

    /**
     * Get the Joget field name for a given GovStack path
     */
    public String getJogetFieldName(String govstackPath) {
        Map<String, Object> fieldInfo = fieldMappings.get(govstackPath);
        if (fieldInfo != null) {
            return (String) fieldInfo.get("jogetField");
        }
        return null;
    }

    /**
     * Get expected value from test data for a given GovStack path
     */
    public String getExpectedValue(String govstackPath) {
        return JsonPathExtractor.extractValue(testData, govstackPath);
    }

    /**
     * Get expected value after transformation
     */
    public String getExpectedTransformedValue(String govstackPath) {
        Map<String, Object> fieldInfo = fieldMappings.get(govstackPath);
        if (fieldInfo == null) {
            return getExpectedValue(govstackPath);
        }

        String value = getExpectedValue(govstackPath);
        String transformation = (String) fieldInfo.get("transformation");

        // Apply transformation based on type
        if ("yesNoBoolean".equals(transformation)) {
            return "true".equals(value) || "1".equals(value) ? "yes" : "no";
        } else if ("numeric".equals(transformation)) {
            // Return numeric value as string
            return value;
        }

        // Check for value mapping
        Map<String, String> valueMapping = (Map<String, String>) fieldInfo.get("valueMapping");
        if (valueMapping != null && valueMapping.containsKey(value)) {
            return valueMapping.get(value);
        }

        return value;
    }

    /**
     * Validate a field mapping
     */
    public void validateFieldMapping(String govstackPath, Map<String, String> actualData) {
        String jogetField = getJogetFieldName(govstackPath);
        if (jogetField == null) {
            // Field not mapped, skip validation
            return;
        }

        String expectedValue = getExpectedTransformedValue(govstackPath);
        String actualValue = actualData.get(jogetField);

        Assert.assertEquals(
            String.format("Field %s (path: %s) mismatch", jogetField, govstackPath),
            expectedValue,
            actualValue
        );
    }

    /**
     * Validate all mapped fields in a section
     */
    public void validateSection(String sectionName, Map<String, String> actualData) {
        int validatedFields = 0;
        for (Map.Entry<String, Map<String, Object>> entry : fieldMappings.entrySet()) {
            Map<String, Object> fieldInfo = entry.getValue();
            if (sectionName.equals(fieldInfo.get("section"))) {
                String govstackPath = entry.getKey();
                validateFieldMapping(govstackPath, actualData);
                validatedFields++;
            }
        }
        System.out.println("Validated " + validatedFields + " fields in section: " + sectionName);
    }

    /**
     * Get all field mappings for testing
     */
    public Map<String, Map<String, Object>> getAllFieldMappings() {
        return fieldMappings;
    }

    /**
     * Check if a field is mapped
     */
    public boolean isFieldMapped(String govstackPath) {
        return fieldMappings.containsKey(govstackPath);
    }
}