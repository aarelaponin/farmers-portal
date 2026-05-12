package global.govstack.processing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import global.govstack.registration.receiver.service.metadata.*;
import global.govstack.registration.receiver.service.normalization.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test backward compatibility with original test-data.json file
 * Ensures that the new ValueNormalizer doesn't break existing data processing
 */
public class BackwardCompatibilityTest {

    private ObjectMapper mapper;
    private ValueNormalizer normalizer;
    private GovStackDataMapper dataMapper;
    private YamlMetadataService metadataService;
    private DataTransformer dataTransformer;

    @Before
    public void setUp() {
        mapper = new ObjectMapper();
        normalizer = new ValueNormalizer();
        metadataService = new YamlMetadataService();
        dataTransformer = new DataTransformer();
        dataMapper = new GovStackDataMapper(metadataService, dataTransformer);
    }

    @Test
    public void testOriginalTestDataJsonFormat() throws Exception {
        // Load test-data.json from resources
        String testDataPath = "/docs-metadata/test-data.json";
        InputStream is = getClass().getResourceAsStream(testDataPath);

        assertNotNull("test-data.json should exist in resources", is);

        // Parse JSON
        JsonNode rootNode = mapper.readTree(is);
        assertNotNull("Should parse JSON successfully", rootNode);

        // Extract testData array
        JsonNode testDataArray = rootNode.get("testData");
        assertNotNull("testData array should exist", testDataArray);
        assertTrue("testData should be an array", testDataArray.isArray());
        assertTrue("testData should have at least one record", testDataArray.size() > 0);

        JsonNode firstFarmer = testDataArray.get(0);

        // Test specific fields that use LOV values

        // Look for extension fields which contains agriculturalActivities
        JsonNode extension = firstFarmer.get("extension");
        assertNotNull("extension field should exist", extension);

        // Test agriculturalActivities section
        JsonNode agActivities = extension.get("agriculturalActivities");
        assertNotNull("agriculturalActivities should exist", agActivities);

        // Test livestock production field (uses "1" in test-data.json)
        JsonNode livestockNode = agActivities.get("engagedInLivestockProduction");
        assertNotNull("engagedInLivestockProduction should exist", livestockNode);

        // Normalize the value
        String normalizedLivestock = normalizer.normalizeToLOV(livestockNode, "livestockProduction");
        assertEquals("Should preserve '1' for livestockProduction", "1", normalizedLivestock);

        // Test crop production field (uses "yes" in test-data.json)
        JsonNode cropNode = agActivities.get("engagedInCropProduction");
        assertNotNull("engagedInCropProduction should exist", cropNode);

        String normalizedCrop = normalizer.normalizeToLOV(cropNode, "cropProduction");
        assertEquals("Should preserve 'yes' for cropProduction", "yes", normalizedCrop);

        // Test canReadWrite field which is in agriculturalActivities in this test data
        JsonNode canReadWriteNode = agActivities.get("canReadWrite");
        if (canReadWriteNode != null) {
            String normalizedReadWrite = normalizer.normalizeToLOV(canReadWriteNode, "canReadWrite");
            assertEquals("Should preserve 'yes' for canReadWrite", "yes", normalizedReadWrite);
        }

        // Test memberCommunity section if present
        JsonNode memberCommunity = extension.get("memberCommunity");
        if (memberCommunity != null) {
            JsonNode cooperativeMember = memberCommunity.get("cooperativeMember");
            if (cooperativeMember != null) {
                String normalized = normalizer.normalizeToLOV(cooperativeMember, "cooperativeMember");
                assertTrue("Should normalize to yes/no",
                    "yes".equals(normalized) || "no".equals(normalized));
            }
        }
    }

    @Test
    @Ignore("Requires full metadata service configuration")
    public void testProcessingWithGovStackDataMapper() throws Exception {
        // Load test-data.json
        String testDataPath = "/docs-metadata/test-data.json";
        InputStream is = getClass().getResourceAsStream(testDataPath);
        String jsonContent = new String(is.readAllBytes());

        // Process with GovStackDataMapperV3
        Map<String, Object> result = dataMapper.mapToMultipleForms(jsonContent);

        assertNotNull("Result should not be null", result);
        assertNotNull("Should have formData", result.get("formData"));
        assertNotNull("Should have primaryKey", result.get("primaryKey"));

        Map<String, Map<String, String>> formData = (Map<String, Map<String, String>>) result.get("formData");

        // Check that forms were created
        assertTrue("Should have at least one form", formData.size() > 0);

        // Check specific form data
        for (Map.Entry<String, Map<String, String>> entry : formData.entrySet()) {
            String formName = entry.getKey();
            Map<String, String> fields = entry.getValue();

            System.out.println("Form: " + formName + " has " + fields.size() + " fields");

            // Each form should have parent_id
            assertTrue("Form " + formName + " should have parent_id",
                fields.containsKey("parent_id"));
        }
    }

    @Test
    public void testAllLOVFieldsFromTestData() throws Exception {
        // Create a comprehensive test for all LOV fields in test-data.json

        String testDataPath = "/docs-metadata/test-data.json";
        InputStream is = getClass().getResourceAsStream(testDataPath);
        JsonNode rootNode = mapper.readTree(is);
        JsonNode firstFarmer = rootNode.get("testData").get(0);

        // Test various sections with LOV fields

        // Look for extension fields
        JsonNode extension = firstFarmer.get("extension");
        assertNotNull("extension field should exist", extension);

        // Agricultural Activities
        JsonNode agActivities = extension.get("agriculturalActivities");
        if (agActivities != null) {
            testFieldNormalization(agActivities, "engagedInLivestockProduction", "livestockProduction", "1");
            testFieldNormalization(agActivities, "engagedInCropProduction", "cropProduction", "yes");
            testFieldNormalization(agActivities, "canReadWrite", "canReadWrite", "yes");
        }

        // Member Community
        JsonNode memberCommunity = extension.get("memberCommunity");
        if (memberCommunity != null) {
            JsonNode cooperativeMember = memberCommunity.get("cooperativeMember");
            if (cooperativeMember != null) {
                String normalized = normalizer.normalizeToLOV(cooperativeMember, "cooperativeMember");
                assertNotNull("cooperativeMember should normalize", normalized);
            }
        }

        // Income and Programs
        JsonNode incomePrograms = extension.get("incomeAndPrograms");
        if (incomePrograms != null) {
            JsonNode everOnISP = incomePrograms.get("everOnISP");
            if (everOnISP != null) {
                String normalized = normalizer.normalizeToLOV(everOnISP, "everOnISP");
                assertNotNull("everOnISP should normalize", normalized);
            }
        }
    }

    private void testFieldNormalization(JsonNode parent, String jsonField, String normalizerField, String expectedValue) {
        JsonNode fieldNode = parent.get(jsonField);
        if (fieldNode != null) {
            String normalized = normalizer.normalizeToLOV(fieldNode, normalizerField);
            assertEquals("Field " + jsonField + " should normalize to " + expectedValue,
                expectedValue, normalized);
        }
    }

    @Test
    public void testMixedFormatHandling() throws Exception {
        // Test handling of mixed format document (some fields as strings, some as booleans)
        String mixedJson = "{" +
            "\"testData\": [{" +
            "  \"agriculturalActivities\": {" +
            "    \"engagedInLivestockProduction\": \"1\"," + // String format
            "    \"engagedInCropProduction\": true," + // Boolean format
            "    \"fertilizerApplied\": \"no\"," + // String LOV
            "    \"pesticidesApplied\": false" + // Boolean
            "  }" +
            "}]}";

        JsonNode rootNode = mapper.readTree(mixedJson);
        JsonNode firstFarmer = rootNode.get("testData").get(0);
        JsonNode agActivities = firstFarmer.get("agriculturalActivities");

        // Test normalization of each field
        assertEquals("1", normalizer.normalizeToLOV(
            agActivities.get("engagedInLivestockProduction"), "livestockProduction"));

        assertEquals("yes", normalizer.normalizeToLOV(
            agActivities.get("engagedInCropProduction"), "cropProduction"));

        assertEquals("no", normalizer.normalizeToLOV(
            agActivities.get("fertilizerApplied"), "fertilizerApplied"));

        assertEquals("no", normalizer.normalizeToLOV(
            agActivities.get("pesticidesApplied"), "pesticidesApplied"));
    }
}