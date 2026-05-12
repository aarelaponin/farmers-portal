import java.sql.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SimulateDocSubmitter {
    public static void main(String[] args) throws Exception {
        String recordId = "a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78";
        String parentFormId = "farms_registry"; // What we changed to

        Connection conn = DriverManager.getConnection(
            "jdbc:mysql://localhost:3307/jwdb", "root", "at456vkm");

        System.out.println("=== SIMULATING DOCSUMBITTER EXTRACTION ===");
        System.out.println("1. Loading parent row from app_fd_" + parentFormId);

        String query = "SELECT * FROM app_fd_" + parentFormId + " WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(query);
        ps.setString(1, recordId);
        ResultSet rs = ps.executeQuery();

        Map<String, String> extractedData = new HashMap<>();

        if (rs.next()) {
            System.out.println("   Found record!");

            // Simulate extractFieldsFromRow for farmerAgriculture section
            String[] fieldsToExtract = {
                "agriculturalManagementSkills",
                "mainSourceAgriculturalInfo"
            };

            for (String field : fieldsToExtract) {
                String columnName = "c_" + field;
                try {
                    String value = rs.getString(columnName);
                    System.out.println("   " + columnName + " = " + value);
                    if (value != null && !value.trim().isEmpty()) {
                        extractedData.put(field, value);
                    }
                } catch (SQLException e) {
                    System.out.println("   " + columnName + " - COLUMN NOT FOUND");
                }
            }
        } else {
            System.out.println("   NO RECORD FOUND!");
        }

        System.out.println("\n2. Data extracted: " + extractedData);

        // Now simulate GovStackJsonEncoder
        System.out.println("\n3. Creating JSON (GovStackJsonEncoder):");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        // Check services.yml mapping
        System.out.println("   From services.yml:");
        System.out.println("   - agriculturalManagementSkills -> extension.agriculturalActivities.managementSkillLevel");
        System.out.println("   - mainSourceAgriculturalInfo -> extension.agriculturalActivities.mainInfoSource");

        // Create the JSON structure
        if (!extractedData.isEmpty()) {
            ObjectNode extension = mapper.createObjectNode();
            ObjectNode agriculturalActivities = mapper.createObjectNode();

            // Map according to services.yml govstack paths
            if (extractedData.containsKey("agriculturalManagementSkills")) {
                agriculturalActivities.put("managementSkillLevel",
                    extractedData.get("agriculturalManagementSkills"));
            }
            if (extractedData.containsKey("mainSourceAgriculturalInfo")) {
                agriculturalActivities.put("mainInfoSource",
                    extractedData.get("mainSourceAgriculturalInfo"));
            }

            extension.set("agriculturalActivities", agriculturalActivities);
            root.set("extension", extension);
        }

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        System.out.println("\n4. JSON that would be sent to ProcessingAPI:");
        System.out.println(json);

        // Now check if ProcessingAPI can find the values
        System.out.println("\n5. ProcessingAPI would look for:");
        System.out.println("   jsonPath: extension.agriculturalActivities.agriculturalManagementSkills");
        System.out.println("   govstackPath: extension.agriculturalActivities.managementSkillLevel");

        // Check if values exist at those paths
        if (root.at("/extension/agriculturalActivities/agriculturalManagementSkills").isMissingNode()) {
            System.out.println("   ❌ NOT found at jsonPath");
        } else {
            System.out.println("   ✓ Found at jsonPath: " +
                root.at("/extension/agriculturalActivities/agriculturalManagementSkills").asText());
        }

        if (root.at("/extension/agriculturalActivities/managementSkillLevel").isMissingNode()) {
            System.out.println("   ❌ NOT found at govstackPath");
        } else {
            System.out.println("   ✓ Found at govstackPath: " +
                root.at("/extension/agriculturalActivities/managementSkillLevel").asText());
        }

        conn.close();
    }
}