package global.govstack.processing;

import global.govstack.registration.receiver.service.metadata.*;
import global.govstack.processing.util.TestFieldHelper;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Comprehensive test to verify complete alignment between:
 * - Joget form definitions
 * - YAML metadata mappings
 * - Test data structure
 * - Mapping functionality
 */
public class ComprehensiveAlignmentTest {

    private YamlMetadataService metadataService;
    private GovStackDataMapper mapper;
    private String testJson;
    private TestFieldHelper fieldHelper;

    @Before
    public void setUp() throws Exception {
        // Load test data
        testJson = new String(Files.readAllBytes(
            Paths.get("src/main/resources/docs-metadata/test-data.json")));

        // Initialize services
        metadataService = new YamlMetadataService();
        metadataService.loadMetadata("farmers_registry");

        DataTransformer dataTransformer = new DataTransformer();
        mapper = new GovStackDataMapper(metadataService, dataTransformer);
        fieldHelper = new TestFieldHelper(metadataService);
    }

    @Test
    public void testCompleteDataMapping() throws Exception {
        // Perform mapping
        Map<String, Object> result = mapper.mapGovStackToJoget(testJson);

        assertNotNull("Result should not be null", result);

        // Check main form data
        Map<String, String> mainForm = (Map<String, String>) result.get("mainForm");
        assertNotNull("Main form data should not be null", mainForm);

        // We expect 69 fields from all non-array sections
        assertEquals("Should map 69 main form fields", 69, mainForm.size());

        // Check array data
        List<Map<String, Object>> arrayData = (List<Map<String, Object>>) result.get("arrayData");
        assertNotNull("Array data should not be null", arrayData);
        assertEquals("Should have 3 array sections", 3, arrayData.size());
    }

    @Test
    public void testAllSectionsProcessed() throws Exception {
        Map<String, Object> result = mapper.mapGovStackToJoget(testJson);
        Map<String, String> mainForm = (Map<String, String>) result.get("mainForm");

        // Test each section has its fields using helper
        // farmerBasicInfo section
        fieldHelper.validateSection("farmerBasicInfo", mainForm);

        // farmerLocation section
        fieldHelper.validateSection("farmerLocation", mainForm);

        // farmerAgriculture section
        fieldHelper.validateSection("farmerAgriculture", mainForm);

        // farmerIncomePrograms section
        fieldHelper.validateSection("farmerIncomePrograms", mainForm);

        // farmerDeclaration section
        fieldHelper.validateSection("farmerDeclaration", mainForm);
    }

    @Test
    public void testArraySectionsProcessed() throws Exception {
        Map<String, Object> result = mapper.mapGovStackToJoget(testJson);
        List<Map<String, Object>> arrayData = (List<Map<String, Object>>) result.get("arrayData");

        // Find each array section
        Map<String, Object> householdSection = null;
        Map<String, Object> cropsSection = null;
        Map<String, Object> livestockSection = null;

        for (Map<String, Object> section : arrayData) {
            String gridName = (String) section.get("gridName");
            if ("householdMembers".equals(gridName)) {
                householdSection = section;
            } else if ("cropManagement".equals(gridName)) {
                cropsSection = section;
            } else if ("livestockDetails".equals(gridName)) {
                livestockSection = section;
            }
        }

        // Verify household members
        assertNotNull("Should have household members section", householdSection);
        List<Map<String, String>> householdRows = (List<Map<String, String>>) householdSection.get("rows");
        assertEquals("Should have 5 household members", 5, householdRows.size());

        // Verify first household member
        Map<String, String> firstMember = householdRows.get(0);
        assertEquals("Mokoena, Thabo", firstMember.get("memberName"));
        assertEquals("male", firstMember.get("sex"));
        assertEquals("SELF", firstMember.get("relationship"));

        // Verify crops
        assertNotNull("Should have crops section", cropsSection);
        List<Map<String, String>> cropRows = (List<Map<String, String>>) cropsSection.get("rows");
        assertEquals("Should have 4 crops", 4, cropRows.size());

        // Verify first crop
        Map<String, String> firstCrop = cropRows.get(0);
        assertEquals("MAIZE", firstCrop.get("cropType"));
        assertEquals("1.5", firstCrop.get("areaCultivated"));
        assertEquals("45", firstCrop.get("bagsHarvested"));

        // Verify livestock
        assertNotNull("Should have livestock section", livestockSection);
        List<Map<String, String>> livestockRows = (List<Map<String, String>>) livestockSection.get("rows");
        assertEquals("Should have 4 livestock types", 4, livestockRows.size());

        // Verify first livestock
        Map<String, String> firstLivestock = livestockRows.get(0);
        assertEquals("cattle-beef", firstLivestock.get("livestockType"));
        assertEquals("2", firstLivestock.get("numberOfMale"));
        assertEquals("5", firstLivestock.get("numberOfFemale"));
    }

    @Test
    public void testValueTransformations() throws Exception {
        Map<String, Object> result = mapper.mapGovStackToJoget(testJson);
        Map<String, String> mainForm = (Map<String, String>) result.get("mainForm");

        // Test boolean transformations using helper
        fieldHelper.validateFieldMapping("extension.agriculturalActivities.engagedInCropProduction", mainForm);
        fieldHelper.validateFieldMapping("extension.income.hasGainfulEmployment", mainForm);
        fieldHelper.validateFieldMapping("extension.cooperativeMember", mainForm);

        // Test value mappings using helper
        fieldHelper.validateFieldMapping("gender", mainForm);
        fieldHelper.validateFieldMapping("extension.residencyType", mainForm);

        // Test multi-checkbox transformations
        String hazards = mainForm.get("hazardsExperienced");
        assertNotNull("Should have hazardsExperienced", hazards);
        assertTrue(hazards.contains("dry_spell"));
        assertTrue(hazards.contains("crop_pests"));
    }

    @Test
    public void testMetadataConfiguration() {
        // Verify metadata is properly loaded
        assertTrue("Metadata should be loaded", metadataService.isLoaded());
        assertEquals("farmers_registry", metadataService.getServiceId());

        // Check all form mappings are present
        Map<String, Object> mappings = metadataService.getAllFormMappings();
        assertEquals("Should have 8 form sections", 8, mappings.size());

        // Verify array mappings
        List<Map<String, Object>> arrayMappings = metadataService.getArrayMappings();
        assertEquals("Should have 3 array mappings", 3, arrayMappings.size());
    }

    @Test
    public void testFieldCounts() throws Exception {
        Map<String, Object> result = mapper.mapGovStackToJoget(testJson);
        Map<String, String> mainForm = (Map<String, String>) result.get("mainForm");

        // Count fields by section (based on YAML definition)
        // farmerBasicInfo: 12 fields
        // farmerLocation: 19 fields
        // farmerAgriculture: 12 fields
        // farmerIncomePrograms: 18 fields
        // farmerDeclaration: 8 fields
        // Total: 69 fields

        int expectedTotal = 12 + 19 + 12 + 18 + 8;
        assertEquals("Total main form fields should be " + expectedTotal, expectedTotal, mainForm.size());
    }

    @Test
    public void testCriticalFieldsPresent() throws Exception {
        Map<String, Object> result = mapper.mapGovStackToJoget(testJson);
        Map<String, String> mainForm = (Map<String, String>) result.get("mainForm");

        // Critical GovStack paths to validate
        String[] criticalPaths = {
            "identifiers[0].value",         // National ID
            "name.given[0]",                // First name
            "name.family",                  // Last name
            "identifiers[1].value",         // Beneficiary Code
            "address[0].district",          // District
            "address[0].city",              // Village
            "extension.agriculturalActivities.engagedInCropProduction",  // Crop production
            "extension.income.mainSource",  // Main income source
            "extension.declaration.fullName", // Declaration name
            "extension.registration.status"   // Registration status
        };

        for (String path : criticalPaths) {
            String jogetField = fieldHelper.getJogetFieldName(path);
            if (jogetField != null) {
                assertNotNull("Critical field '" + jogetField + "' (path: " + path + ") should be present", mainForm.get(jogetField));
                assertFalse("Critical field '" + jogetField + "' should not be empty",
                           mainForm.get(jogetField).isEmpty());
            }
        }
    }
}