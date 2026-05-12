package global.govstack.processing;

import global.govstack.registration.receiver.service.metadata.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Test utility to verify GovStack data mapping with test-data.json
 * Run this with: mvn test -Dtest=TestGovStackMapping
 */
public class TestGovStackMapping {

    public static void main(String[] args) {
        try {
            System.out.println("=== Testing GovStack Data Mapping ===\n");

            // Load test data
            String testDataPath = "src/main/resources/docs-metadata/test-data.json";
            String testJson = new String(Files.readAllBytes(Paths.get(testDataPath)));
            System.out.println("Loaded test data from: " + testDataPath);

            // Initialize services
            YamlMetadataService metadataService = new YamlMetadataService();
            metadataService.loadMetadata("farmers_registry");
            System.out.println("Loaded metadata for service: farmers_registry");

            DataTransformer dataTransformer = new DataTransformer();
            GovStackDataMapper mapper = new GovStackDataMapper(metadataService, dataTransformer);

            // Perform mapping
            System.out.println("\n=== Mapping Data ===");
            Map<String, Object> result = mapper.mapGovStackToJoget(testJson);

            // Display results
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

            // Main form data
            Map<String, String> mainForm = (Map<String, String>) result.get("mainForm");
            System.out.println("\n=== Main Form Data (" + mainForm.size() + " fields) ===");
            for (Map.Entry<String, String> entry : mainForm.entrySet()) {
                System.out.println("  " + entry.getKey() + " = " + entry.getValue());
            }

            // Array data
            java.util.List<Map<String, Object>> arrayData = (java.util.List<Map<String, Object>>) result.get("arrayData");
            System.out.println("\n=== Array Data (" + arrayData.size() + " grids) ===");
            for (Map<String, Object> grid : arrayData) {
                String gridName = (String) grid.get("gridName");
                java.util.List<Map<String, String>> rows = (java.util.List<Map<String, String>>) grid.get("rows");
                System.out.println("\n  Grid: " + gridName + " (" + rows.size() + " rows)");

                int rowNum = 1;
                for (Map<String, String> row : rows) {
                    System.out.println("    Row " + rowNum++ + ":");
                    for (Map.Entry<String, String> field : row.entrySet()) {
                        System.out.println("      " + field.getKey() + " = " + field.getValue());
                    }
                }
            }

            System.out.println("\n=== Test Completed Successfully ===");

        } catch (Exception e) {
            System.err.println("Error during test: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}