import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

public class TestDocSubmitterJson {
    public static void main(String[] args) throws Exception {
        // Simulate what DocSubmitter does
        ObjectMapper mapper = new ObjectMapper();

        // This simulates the GovStackJsonEncoder.processField method
        ObjectNode root = mapper.createObjectNode();

        // The DocSubmitter uses govstack path from services.yml
        String govstackPath = "extension.agriculturalActivities.managementSkillLevel";
        String value = "college_certificate";

        // Set nested value (like setNestedValue in GovStackJsonEncoder)
        String[] parts = govstackPath.split("\\.");
        ObjectNode current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!current.has(part)) {
                current.set(part, mapper.createObjectNode());
            }
            current = (ObjectNode) current.get(part);
        }

        String lastPart = parts[parts.length - 1];
        current.put(lastPart, value);

        // Print the JSON that DocSubmitter would send
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        System.out.println("DocSubmitter would send this JSON:");
        System.out.println(json);

        // Now check if ProcessingAPI would find it
        System.out.println("\nProcessingAPI would look for:");
        System.out.println("1. jsonPath: extension.agriculturalActivities.agriculturalManagementSkills");
        System.out.println("2. govstackPath: extension.agriculturalActivities.managementSkillLevel");

        // The field IS at managementSkillLevel, so it SHOULD be found
    }
}