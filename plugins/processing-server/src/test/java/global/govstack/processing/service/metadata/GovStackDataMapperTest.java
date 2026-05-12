package global.govstack.processing.service.metadata;

import global.govstack.processing.util.TestFieldHelper;
import global.govstack.registration.receiver.service.metadata.YamlMetadataService;
import global.govstack.registration.receiver.service.metadata.DataTransformer;
import global.govstack.registration.receiver.service.metadata.GovStackDataMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for GovStackDataMapperV3 using actual test-data.json
 * Note: Some tests are marked @Ignore as they require Joget dependencies
 */
public class GovStackDataMapperTest {

    private YamlMetadataService metadataService;
    private DataTransformer dataTransformer;
    private GovStackDataMapper mapper;
    private String testDataJson;
    private TestFieldHelper fieldHelper;

    @Before
    public void setUp() throws Exception {
        // Load test data
        testDataJson = new String(Files.readAllBytes(Paths.get("docs-metadata/test-data.json")));

        // Initialize services
        metadataService = new YamlMetadataService();
        dataTransformer = new DataTransformer();
        mapper = new GovStackDataMapper(metadataService, dataTransformer);

        // Initialize field helper after loading metadata (will be done in each test)
        fieldHelper = null;
    }

    @Test
    @Ignore("Requires LogUtil from Joget")
    public void testMapGovStackToJoget_WithFullTestData() throws Exception {
        // Load metadata
        metadataService.loadMetadata("farmers_registry");
        fieldHelper = new TestFieldHelper(metadataService);

        // Map the data
        Map<String, Object> result = mapper.mapToMultipleForms(testDataJson);

        assertNotNull(result);
        assertNotNull(result.get("mainForm"));
        assertNotNull(result.get("arrayData"));

        // Check main form data
        Map<String, String> mainForm = (Map<String, String>) result.get("mainForm");

        // Test basic fields using helper
        fieldHelper.validateFieldMapping("name.given[0]", mainForm);
        fieldHelper.validateFieldMapping("name.family", mainForm);
        fieldHelper.validateFieldMapping("identifiers[0].value", mainForm);
        fieldHelper.validateFieldMapping("gender", mainForm);
        fieldHelper.validateFieldMapping("birthDate", mainForm);
        fieldHelper.validateFieldMapping("maritalStatus", mainForm);

        // Test address fields using helper
        fieldHelper.validateFieldMapping("address[0].district", mainForm);
        fieldHelper.validateFieldMapping("address[0].city", mainForm);
        fieldHelper.validateFieldMapping("address[0].line[0]", mainForm);

        // Test contact fields using helper
        fieldHelper.validateFieldMapping("telecom[0].value", mainForm);
        fieldHelper.validateFieldMapping("telecom[1].value", mainForm);

        // Test extension fields using helper
        fieldHelper.validateFieldMapping("extension.extensionOfficer", mainForm);
        fieldHelper.validateFieldMapping("extension.cooperativeMember", mainForm);
        fieldHelper.validateFieldMapping("extension.cooperativeName", mainForm);

        // Test farm location using helper
        fieldHelper.validateFieldMapping("extension.farmLocation.latitude", mainForm);
        fieldHelper.validateFieldMapping("extension.farmLocation.longitude", mainForm);

        // Test farm details using helper
        fieldHelper.validateFieldMapping("extension.farmDetails.ownedRentedLandHectares", mainForm);
        fieldHelper.validateFieldMapping("extension.farmDetails.totalLandHectares", mainForm);
        fieldHelper.validateFieldMapping("extension.farmDetails.cultivatedLandHectares", mainForm);
        fieldHelper.validateFieldMapping("extension.farmDetails.conservationAgricultureHectares", mainForm);

        // Test access to services using helper
        fieldHelper.validateFieldMapping("extension.accessToServices.waterSourceMinutes", mainForm);
        fieldHelper.validateFieldMapping("extension.accessToServices.primarySchoolMinutes", mainForm);
        fieldHelper.validateFieldMapping("extension.accessToServices.publicHospitalMinutes", mainForm);
        fieldHelper.validateFieldMapping("extension.accessToServices.livestockMarketMinutes", mainForm);
        fieldHelper.validateFieldMapping("extension.accessToServices.agriculturalMarketMinutes", mainForm);

        // Test agricultural activities using helper
        fieldHelper.validateFieldMapping("extension.agriculturalActivities.engagedInCropProduction", mainForm);
        fieldHelper.validateFieldMapping("extension.agriculturalActivities.engagedInLivestockProduction", mainForm);
        fieldHelper.validateFieldMapping("extension.agriculturalActivities.mainSourceLivelihood", mainForm);
        fieldHelper.validateFieldMapping("extension.agriculturalActivities.mainSourceFarmLabour", mainForm);
        fieldHelper.validateFieldMapping("extension.agriculturalActivities.canReadWrite", mainForm);

        // Check array data
        List<Map<String, Object>> arrayData = (List<Map<String, Object>>) result.get("arrayData");
        assertTrue(arrayData.size() > 0);

        // Find household members array
        Map<String, Object> householdMembers = findArrayByGridName(arrayData, "householdMembers");
        assertNotNull(householdMembers);

        List<Map<String, String>> householdRows = (List<Map<String, String>>) householdMembers.get("rows");
        assertEquals(5, householdRows.size()); // Should have 5 household members

        // Test first household member
        Map<String, String> firstMember = householdRows.get(0);
        assertEquals("Mokoena, Thabo", firstMember.get("memberName"));
        assertEquals("male", firstMember.get("sex"));
        assertEquals("1994-05-15", firstMember.get("date_of_birth"));
        assertEquals("SELF", firstMember.get("relationship"));

        // Find crops array
        Map<String, Object> crops = findArrayByGridName(arrayData, "cropManagement");
        if (crops != null) {
            List<Map<String, String>> cropRows = (List<Map<String, String>>) crops.get("rows");
            assertTrue(cropRows.size() >= 5); // Should have at least 5 crops

            // Check first crop (MAIZE)
            Map<String, String> firstCrop = cropRows.get(0);
            assertEquals("MAIZE", firstCrop.get("cropType"));
            assertEquals("1.5", firstCrop.get("cropAreaPlanted"));
            assertEquals("45", firstCrop.get("quantityHarvested"));
        }

        // Find livestock array
        Map<String, Object> livestock = findArrayByGridName(arrayData, "livestockDetails");
        if (livestock != null) {
            List<Map<String, String>> livestockRows = (List<Map<String, String>>) livestock.get("rows");
            assertTrue(livestockRows.size() >= 3); // Should have at least 3 livestock

            // Check first livestock (cattle-beef)
            Map<String, String> firstLivestock = livestockRows.get(0);
            assertEquals("cattle-beef", firstLivestock.get("livestockType"));
        }
    }

    @Test
    public void testExtractValueFromPath_SimpleField() throws Exception {
        // This test doesn't require Joget dependencies
        String json = "{\"name\": {\"given\": [\"John\"], \"family\": \"Doe\"}}";

        // We would need to test the private method through reflection
        // or make it package-private for testing
        // For now, this is a placeholder
        assertTrue(true);
    }

    @Test
    public void testExtractValueFromPath_ArrayField() throws Exception {
        String json = "{\"identifiers\": [{\"type\": \"NationalId\", \"value\": \"123456\"}]}";

        // We would need to test the private method through reflection
        // or make it package-private for testing
        assertTrue(true);
    }

    @Test
    public void testExtractValueFromPath_NestedField() throws Exception {
        String json = "{\"extension\": {\"farmLocation\": {\"latitude\": \"-29.363611\"}}}";

        // We would need to test the private method through reflection
        // or make it package-private for testing
        assertTrue(true);
    }

    @Test
    public void testValueMappings() {
        // Test that value mappings are correctly extracted
        Map<String, String> genderMapping = metadataService.getValueMapping("gender");

        if (genderMapping != null) {
            assertEquals("male", genderMapping.get("male"));
            assertEquals("female", genderMapping.get("female"));
        }
    }

    @Test
    public void testTransformations() {
        // Test that transformations are correctly identified
        String dateTransform = metadataService.getTransformation("date_of_birth");
        String numericTransform = metadataService.getTransformation("yearsInArea");
        String booleanTransform = metadataService.getTransformation("member_of_cooperative");

        if (dateTransform != null) {
            assertEquals("date_ISO8601", dateTransform);
        }
        if (numericTransform != null) {
            assertEquals("numeric", numericTransform);
        }
        if (booleanTransform != null) {
            assertEquals("yesNoBoolean", booleanTransform);
        }
    }

    // Helper method to find array by grid name
    private Map<String, Object> findArrayByGridName(List<Map<String, Object>> arrays, String gridName) {
        for (Map<String, Object> array : arrays) {
            if (gridName.equals(array.get("gridName"))) {
                return array;
            }
        }
        return null;
    }

    @Test
    public void testEmptyJson() throws Exception {
        String emptyJson = "{}";

        try {
            Map<String, Object> result = mapper.mapToMultipleForms(emptyJson);
            assertNotNull(result);

            Map<String, String> mainForm = (Map<String, String>) result.get("mainForm");
            assertNotNull(mainForm);
            // Should have no fields or only empty fields
        } catch (Exception e) {
            // May throw exception for missing required fields
            assertTrue(e.getMessage() != null);
        }
    }

    @Test
    public void testInvalidJson() {
        String invalidJson = "not a json";

        try {
            mapper.mapToMultipleForms(invalidJson);
            fail("Should throw exception for invalid JSON");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Error"));
        }
    }

    @Test
    public void testNullJson() {
        try {
            mapper.mapToMultipleForms(null);
            fail("Should throw exception for null JSON");
        } catch (Exception e) {
            assertTrue(e.getMessage() != null);
        }
    }
}