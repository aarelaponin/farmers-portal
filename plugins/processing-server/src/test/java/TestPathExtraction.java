import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import global.govstack.registration.receiver.util.JsonPathExtractor;

public class TestPathExtraction {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Simulate DocSubmitter JSON structure
        String docSubmitterJson = "{" +
            "\"extension\": {" +
                "\"agriculturalActivities\": {" +
                    "\"managementSkillLevel\": \"college_certificate\"" +
                "}" +
            "}" +
        "}";

        // Simulate test data JSON structure
        String testDataJson = "{" +
            "\"extension\": {" +
                "\"agriculturalActivities\": {" +
                    "\"agriculturalManagementSkills\": \"college_diploma\"" +
                "}" +
            "}" +
        "}";

        System.out.println("Testing path extraction:");
        System.out.println("========================");

        // Test DocSubmitter format
        JsonNode docNode = mapper.readTree(docSubmitterJson);
        String govstackPath = "extension.agriculturalActivities.managementSkillLevel";
        String jsonPath = "extension.agriculturalActivities.agriculturalManagementSkills";

        System.out.println("\n1. DocSubmitter format:");
        JsonNode value1 = JsonPathExtractor.extractNode(docNode, jsonPath);
        System.out.println("   jsonPath result: " + (value1 != null ? value1.asText() : "null"));

        JsonNode value2 = JsonPathExtractor.extractNode(docNode, govstackPath);
        System.out.println("   govstackPath result: " + (value2 != null ? value2.asText() : "null"));

        // Test data format
        JsonNode testNode = mapper.readTree(testDataJson);
        System.out.println("\n2. Test data format:");
        JsonNode value3 = JsonPathExtractor.extractNode(testNode, jsonPath);
        System.out.println("   jsonPath result: " + (value3 != null ? value3.asText() : "null"));

        JsonNode value4 = JsonPathExtractor.extractNode(testNode, govstackPath);
        System.out.println("   govstackPath result: " + (value4 != null ? value4.asText() : "null"));

        // Simulate the dual-path logic
        System.out.println("\n3. Dual-path extraction logic:");
        testBothPaths(docNode, "DocSubmitter data", jsonPath, govstackPath);
        testBothPaths(testNode, "Test data", jsonPath, govstackPath);
    }

    private static void testBothPaths(JsonNode node, String label, String jsonPath, String govstackPath) {
        System.out.println("\n   Testing " + label + ":");

        JsonNode valueNode = null;
        String extractPath = null;

        // Try jsonPath first
        if (jsonPath != null) {
            valueNode = JsonPathExtractor.extractNode(node, jsonPath);
            if (valueNode != null && !valueNode.isNull()) {
                extractPath = jsonPath;
            }
        }

        // If not found, try govstackPath
        if ((valueNode == null || valueNode.isNull()) && govstackPath != null) {
            valueNode = JsonPathExtractor.extractNode(node, govstackPath);
            if (valueNode != null && !valueNode.isNull()) {
                extractPath = govstackPath;
            }
        }

        System.out.println("   Path used: " + extractPath);
        System.out.println("   Value found: " + (valueNode != null ? valueNode.asText() : "null"));
    }
}