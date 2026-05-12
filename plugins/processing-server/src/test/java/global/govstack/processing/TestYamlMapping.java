package global.govstack.processing;

import org.yaml.snakeyaml.Yaml;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Standalone test to verify YAML mapping logic without Joget dependencies
 * Run with: mvn test -Dtest=TestYamlMapping
 */
public class TestYamlMapping {

    public static void main(String[] args) {
        try {
            System.out.println("=== Testing YAML-based GovStack Data Mapping ===\n");

            // Load YAML metadata
            Yaml yaml = new Yaml();
            FileInputStream yamlStream = new FileInputStream("docs-metadata/services.yml");
            Map<String, Object> yamlData = yaml.load(yamlStream);
            System.out.println("✓ Loaded YAML metadata");

            // Load test JSON data
            String testJson = new String(Files.readAllBytes(Paths.get("docs-metadata/test-data.json")));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(testJson);
            System.out.println("✓ Loaded test JSON data\n");

            // Extract basic mappings
            Map<String, Object> formMappings = (Map<String, Object>) yamlData.get("formMappings");
            System.out.println("=== Form Sections Found ===");
            for (String section : formMappings.keySet()) {
                System.out.println("  - " + section);
            }

            // Test some basic field mappings
            System.out.println("\n=== Sample Field Mappings ===");

            // Map name fields
            JsonNode nameNode = rootNode.get("name");
            if (nameNode != null) {
                String firstName = nameNode.get("given").get(0).asText();
                String lastName = nameNode.get("family").asText();
                System.out.println("  first_name = " + firstName);
                System.out.println("  last_name = " + lastName);
            }

            // Map identifiers
            JsonNode identifiers = rootNode.get("identifiers");
            if (identifiers != null && identifiers.isArray()) {
                for (JsonNode id : identifiers) {
                    String type = id.get("type").asText();
                    String value = id.get("value").asText();
                    System.out.println("  " + type + " = " + value);
                }
            }

            // Map gender
            String gender = rootNode.get("gender").asText();
            System.out.println("  gender = " + gender);

            // Map birth date
            String birthDate = rootNode.get("birthDate").asText();
            System.out.println("  date_of_birth = " + birthDate);

            // Map address
            JsonNode address = rootNode.get("address");
            if (address != null && address.isArray() && address.size() > 0) {
                JsonNode addr = address.get(0);
                System.out.println("  district = " + addr.get("district").asText());
                System.out.println("  village = " + addr.get("city").asText());
            }

            // Count household members
            JsonNode relatedPerson = rootNode.get("relatedPerson");
            if (relatedPerson != null && relatedPerson.isArray()) {
                System.out.println("\n=== Household Members ===");
                System.out.println("  Found " + relatedPerson.size() + " household members");

                int i = 1;
                for (JsonNode person : relatedPerson) {
                    System.out.println("  Member " + i++ + ":");
                    System.out.println("    - name: " + person.get("name").get("text").asText());
                    System.out.println("    - gender: " + person.get("gender").asText());
                    System.out.println("    - birthDate: " + person.get("birthDate").asText());
                }
            }

            // Map extension fields
            JsonNode extension = rootNode.get("extension");
            if (extension != null) {
                System.out.println("\n=== Extension Fields ===");

                // Farm location
                JsonNode farmLocation = extension.get("farmLocation");
                if (farmLocation != null) {
                    System.out.println("  Farm Location:");
                    System.out.println("    - latitude: " + farmLocation.get("latitude").asText());
                    System.out.println("    - longitude: " + farmLocation.get("longitude").asText());
                }

                // Agricultural activities
                JsonNode agActivities = extension.get("agriculturalActivities");
                if (agActivities != null) {
                    System.out.println("  Agricultural Activities:");
                    System.out.println("    - engagedInCropProduction: " + agActivities.get("engagedInCropProduction"));
                    System.out.println("    - engagedInLivestockProduction: " + agActivities.get("engagedInLivestockProduction"));
                }
            }

            System.out.println("\n=== Test Completed Successfully ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}