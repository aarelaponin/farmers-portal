package global.govstack.farmreg.registration.validation;

import global.govstack.farmreg.registration.service.metadata.GenericFormDataExtractor;
import global.govstack.farmreg.registration.service.metadata.GovStackJsonEncoder;
import global.govstack.farmreg.registration.service.metadata.YamlMetadataService;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.commons.util.LogUtil;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import static org.junit.Assert.*;

/**
 * Validation test to ensure DocSubmitter correctly extracts data from parent form table
 * This test validates the extraction approach matches ProcessingAPI's storage pattern
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:applicationContext.xml"})
public class ExtractionValidationTest {

    private static final String TEST_UUID = "a3a6df93-ac7e-4036-a4c9-70ffdc3cdc78";
    private static final String PARENT_FORM_ID = "farmerRegistrationForm";

    @Autowired
    private FormDataDao formDataDao;

    @Autowired
    private FormDefinitionDao formDefinitionDao;

    private GenericFormDataExtractor extractor;
    private GovStackJsonEncoder encoder;
    private YamlMetadataService metadataService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() {
        metadataService = new YamlMetadataService();
        extractor = new GenericFormDataExtractor(metadataService);
        encoder = new GovStackJsonEncoder(metadataService);
    }

    @Test
    public void testParentTableResolution() {
        LogUtil.info(getClass().getName(), "=== Testing Parent Table Resolution ===");

        // Get the app definition
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        assertNotNull("App definition should exist", appDef);

        // Load the parent form definition
        FormDefinition formDef = formDefinitionDao.loadById(PARENT_FORM_ID, appDef);
        assertNotNull("Parent form definition should exist", formDef);

        // Get the table name
        String tableName = formDef.getTableName();
        assertNotNull("Table name should not be null", tableName);

        // The table name should be farms_registry (without app_fd_ prefix)
        LogUtil.info(getClass().getName(), "Resolved table name: " + tableName);
        assertTrue("Table name should be farms_registry or similar",
                   tableName.contains("registry") || tableName.contains("farmer"));
    }

    @Test
    public void testDataExistsInParentTable() {
        LogUtil.info(getClass().getName(), "=== Testing Data Exists in Parent Table ===");

        // Get table name
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        FormDefinition formDef = formDefinitionDao.loadById(PARENT_FORM_ID, appDef);
        String tableName = formDef.getTableName();

        // Load the record
        FormRow row = formDataDao.load(PARENT_FORM_ID, tableName, TEST_UUID);
        assertNotNull("Record should exist with UUID: " + TEST_UUID, row);

        // Check for key fields with c_ prefix
        String firstName = row.getProperty("c_first_name");
        String lastName = row.getProperty("c_last_name");
        String nationalId = row.getProperty("c_national_id");

        LogUtil.info(getClass().getName(), "Found data - First Name: " + firstName +
                     ", Last Name: " + lastName + ", National ID: " + nationalId);

        // At least one field should have data
        assertTrue("Should have some data fields",
                   firstName != null || lastName != null || nationalId != null);
    }

    @Test
    public void testCompleteExtraction() {
        LogUtil.info(getClass().getName(), "=== Testing Complete Data Extraction ===");

        // Extract all data
        Map<String, Object> extractedData = extractor.extractAllFormData(TEST_UUID);
        assertNotNull("Extracted data should not be null", extractedData);

        // Should have the record ID
        assertEquals("Should have correct ID", TEST_UUID, extractedData.get("id"));

        // Should have multiple sections
        LogUtil.info(getClass().getName(), "Extracted sections: " + extractedData.keySet());

        // Check for key sections
        assertNotNull("Should have farmerBasicInfo section", extractedData.get("farmerBasicInfo"));
        assertNotNull("Should have farmerLocation section", extractedData.get("farmerLocation"));
        assertNotNull("Should have farmerAgriculture section", extractedData.get("farmerAgriculture"));

        // Validate basic info has fields
        Map<String, Object> basicInfo = (Map<String, Object>) extractedData.get("farmerBasicInfo");
        if (basicInfo != null && !basicInfo.isEmpty()) {
            LogUtil.info(getClass().getName(), "Basic info fields: " + basicInfo.keySet());

            // Check for key fields (without c_ prefix)
            assertTrue("Should have first_name or last_name",
                      basicInfo.containsKey("first_name") || basicInfo.containsKey("last_name"));
        }
    }

    @Test
    public void testJsonEncoding() {
        LogUtil.info(getClass().getName(), "=== Testing JSON Encoding ===");

        // Extract data
        Map<String, Object> extractedData = extractor.extractAllFormData(TEST_UUID);

        // Encode to GovStack JSON
        String govStackJson = encoder.encodeToGovStackJson(extractedData);
        assertNotNull("Encoded JSON should not be null", govStackJson);

        try {
            // Parse JSON to validate structure
            JsonNode jsonNode = objectMapper.readTree(govStackJson);

            // Check for GovStack structure
            assertEquals("Should be Person resource", "Person", jsonNode.path("resourceType").asText());

            // Check for name structure
            JsonNode nameNode = jsonNode.path("name");
            assertFalse("Should have name object", nameNode.isMissingNode());

            // Check for extension
            JsonNode extensionNode = jsonNode.path("extension");
            assertFalse("Should have extension object", extensionNode.isMissingNode());

            LogUtil.info(getClass().getName(), "JSON structure validated successfully");

            // Log sample of JSON for debugging
            if (govStackJson.length() > 500) {
                LogUtil.info(getClass().getName(), "Sample JSON: " + govStackJson.substring(0, 500) + "...");
            } else {
                LogUtil.info(getClass().getName(), "JSON: " + govStackJson);
            }

        } catch (Exception e) {
            fail("Failed to parse JSON: " + e.getMessage());
        }
    }

    @Test
    public void testFieldMapping() {
        LogUtil.info(getClass().getName(), "=== Testing Field Mapping ===");

        // Get raw data from database
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        FormDefinition formDef = formDefinitionDao.loadById(PARENT_FORM_ID, appDef);
        String tableName = formDef.getTableName();
        FormRow row = formDataDao.load(PARENT_FORM_ID, tableName, TEST_UUID);

        // Extract using our extractor
        Map<String, Object> extractedData = extractor.extractAllFormData(TEST_UUID);
        Map<String, Object> basicInfo = (Map<String, Object>) extractedData.get("farmerBasicInfo");

        if (basicInfo != null && row != null) {
            // Check mapping: database c_first_name -> extracted first_name
            String dbFirstName = row.getProperty("c_first_name");
            String extractedFirstName = (String) basicInfo.get("first_name");

            if (dbFirstName != null && extractedFirstName != null) {
                assertEquals("Field mapping should work correctly", dbFirstName, extractedFirstName);
                LogUtil.info(getClass().getName(), "Field mapping verified: c_first_name -> first_name");
            }

            // Check more mappings
            String dbGender = row.getProperty("c_gender");
            String extractedGender = (String) basicInfo.get("gender");

            if (dbGender != null && extractedGender != null) {
                assertEquals("Gender mapping should work", dbGender, extractedGender);
                LogUtil.info(getClass().getName(), "Field mapping verified: c_gender -> gender");
            }
        }
    }

    /**
     * Main validation that proves the complete flow works
     */
    @Test
    public void validateCompleteRoundTrip() {
        LogUtil.info(getClass().getName(), "=== COMPLETE ROUND-TRIP VALIDATION ===");

        try {
            // Step 1: Verify record exists
            LogUtil.info(getClass().getName(), "Step 1: Verifying record exists...");
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            FormDefinition formDef = formDefinitionDao.loadById(PARENT_FORM_ID, appDef);
            String tableName = formDef.getTableName();
            FormRow row = formDataDao.load(PARENT_FORM_ID, tableName, TEST_UUID);
            assertNotNull("Record must exist in database", row);
            LogUtil.info(getClass().getName(), "✓ Record exists in table: " + tableName);

            // Step 2: Extract data
            LogUtil.info(getClass().getName(), "Step 2: Extracting data...");
            Map<String, Object> extractedData = extractor.extractAllFormData(TEST_UUID);
            assertNotNull("Extraction must succeed", extractedData);
            assertTrue("Must extract multiple sections", extractedData.size() > 2);
            LogUtil.info(getClass().getName(), "✓ Extracted " + extractedData.size() + " sections");

            // Step 3: Encode to JSON
            LogUtil.info(getClass().getName(), "Step 3: Encoding to GovStack JSON...");
            String govStackJson = encoder.encodeToGovStackJson(extractedData);
            assertNotNull("JSON encoding must succeed", govStackJson);
            assertTrue("JSON must have content", govStackJson.length() > 100);
            LogUtil.info(getClass().getName(), "✓ Encoded to " + govStackJson.length() + " characters of JSON");

            // Step 4: Validate JSON structure
            LogUtil.info(getClass().getName(), "Step 4: Validating JSON structure...");
            JsonNode jsonNode = objectMapper.readTree(govStackJson);

            // Critical fields that must exist
            assertEquals("Person", jsonNode.path("resourceType").asText());
            assertFalse(jsonNode.path("identifiers").isMissingNode());
            assertFalse(jsonNode.path("name").isMissingNode());
            assertFalse(jsonNode.path("extension").isMissingNode());
            LogUtil.info(getClass().getName(), "✓ JSON structure is valid");

            // Step 5: Verify key data paths
            LogUtil.info(getClass().getName(), "Step 5: Verifying key data paths...");
            JsonNode extension = jsonNode.path("extension");
            assertFalse("Should have agricultural activities",
                       extension.path("agriculturalActivities").isMissingNode());
            assertFalse("Should have income data",
                       extension.path("income").isMissingNode());
            LogUtil.info(getClass().getName(), "✓ All key data paths present");

            LogUtil.info(getClass().getName(), "=== VALIDATION SUCCESSFUL ===");
            LogUtil.info(getClass().getName(), "DocSubmitter can successfully extract and encode data!");

        } catch (Exception e) {
            fail("Round-trip validation failed: " + e.getMessage());
        }
    }
}