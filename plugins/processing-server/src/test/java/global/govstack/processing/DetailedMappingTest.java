package global.govstack.processing;

import global.govstack.registration.receiver.service.metadata.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Detailed test to understand why only 24 fields are being mapped
 */
public class DetailedMappingTest {

    public static void main(String[] args) {
        try {
            System.out.println("=" + "=".repeat(79));
            System.out.println("DETAILED MAPPING ANALYSIS");
            System.out.println("=" + "=".repeat(79));

            // Load test data
            String testJson = new String(Files.readAllBytes(
                Paths.get("src/main/resources/docs-metadata/test-data.json")));

            // Initialize services
            YamlMetadataService metadataService = new YamlMetadataService();
            metadataService.loadMetadata("farmers_registry");

            DataTransformer dataTransformer = new DataTransformer();
            GovStackDataMapper mapper = new GovStackDataMapper(metadataService, dataTransformer);

            // Parse JSON to check structure
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(testJson);

            System.out.println("\n1. TEST DATA STRUCTURE");
            System.out.println("-" + "-".repeat(40));

            // Check what's at the root level
            Iterator<String> fieldNames = rootNode.fieldNames();
            System.out.println("Root level fields:");
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode node = rootNode.get(fieldName);
                String type = node.isObject() ? "object" :
                             node.isArray() ? "array" :
                             node.isTextual() ? "text" :
                             node.isNumber() ? "number" : "other";
                System.out.println("  - " + fieldName + " (" + type + ")");
            }

            // Perform mapping
            System.out.println("\n2. MAPPING RESULTS");
            System.out.println("-" + "-".repeat(40));

            Map<String, Object> result = mapper.mapGovStackToJoget(testJson);
            Map<String, String> mainForm = (Map<String, String>) result.get("mainForm");
            List<Map<String, Object>> arrayData = (List<Map<String, Object>>) result.get("arrayData");

            System.out.println("Main form fields mapped: " + mainForm.size());
            System.out.println("Array sections mapped: " + arrayData.size());

            // Analyze by section
            System.out.println("\n3. SECTION-BY-SECTION ANALYSIS");
            System.out.println("-" + "-".repeat(40));

            Map<String, Object> allMappings = metadataService.getAllFormMappings();

            for (Map.Entry<String, Object> entry : allMappings.entrySet()) {
                String sectionName = entry.getKey();
                Map<String, Object> section = (Map<String, Object>) entry.getValue();

                System.out.println("\nSection: " + sectionName);

                if ("array".equals(section.get("type"))) {
                    String govstackPath = (String) section.get("govstack");
                    System.out.println("  Type: Array");
                    System.out.println("  Path: " + govstackPath);

                    // Check if path exists in test data
                    JsonNode arrayNode = getNodeAtPath(rootNode, govstackPath);
                    if (arrayNode != null && arrayNode.isArray()) {
                        System.out.println("  ✓ Found in test data: " + arrayNode.size() + " items");
                    } else if (arrayNode != null) {
                        System.out.println("  ⚠ Found but not array: " + arrayNode.getNodeType());
                    } else {
                        System.out.println("  ✗ NOT found in test data");
                    }
                } else {
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) section.get("fields");
                    if (fields != null) {
                        int found = 0;
                        int notFound = 0;
                        List<String> missingFields = new ArrayList<>();

                        for (Map<String, Object> field : fields) {
                            String jogetField = (String) field.get("joget");
                            String govstackPath = (String) field.get("govstack");

                            if (mainForm.containsKey(jogetField)) {
                                found++;
                            } else {
                                notFound++;
                                missingFields.add(jogetField + " (" + govstackPath + ")");
                            }
                        }

                        System.out.println("  Type: Regular");
                        System.out.println("  Fields defined: " + fields.size());
                        System.out.println("  Fields mapped: " + found);
                        System.out.println("  Fields missing: " + notFound);

                        if (!missingFields.isEmpty() && missingFields.size() <= 5) {
                            System.out.println("  Missing fields:");
                            for (String missing : missingFields) {
                                System.out.println("    - " + missing);
                            }
                        }
                    }
                }
            }

            // Show sample of mapped data
            System.out.println("\n4. SAMPLE MAPPED DATA");
            System.out.println("-" + "-".repeat(40));

            int count = 0;
            for (Map.Entry<String, String> field : mainForm.entrySet()) {
                if (count++ < 10) {
                    System.out.println("  " + field.getKey() + " = " +
                        (field.getValue().length() > 30 ?
                         field.getValue().substring(0, 30) + "..." : field.getValue()));
                }
            }
            if (mainForm.size() > 10) {
                System.out.println("  ... and " + (mainForm.size() - 10) + " more fields");
            }

            System.out.println("\n" + "=" + "=".repeat(39));
            System.out.println("END OF ANALYSIS");
            System.out.println("=" + "=".repeat(39));

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static JsonNode getNodeAtPath(JsonNode node, String path) {
        if (path == null || path.isEmpty()) return null;

        String[] parts = path.split("\\.");
        JsonNode current = node;

        for (String part : parts) {
            if (current == null) return null;

            if (part.contains("[") && part.contains("]")) {
                int idx = part.indexOf("[");
                String field = part.substring(0, idx);
                String indexStr = part.substring(idx + 1, part.length() - 1);

                current = current.get(field);
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