package global.govstack.processing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import global.govstack.registration.receiver.service.normalization.ValueNormalizer;
import global.govstack.registration.receiver.service.metadata.*;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * Test handling of DocSubmitter boolean output format
 * DocSubmitter sends boolean values (true/false) instead of LOV strings
 */
public class DocSubmitterFormatTest {

    private ObjectMapper mapper;
    private ValueNormalizer normalizer;

    @Before
    public void setUp() {
        mapper = new ObjectMapper();
        normalizer = new ValueNormalizer();
    }

    @Test
    public void testDocSubmitterBooleanFormat() throws Exception {
        // Simulate DocSubmitter output with boolean values
        String docSubmitterJson = "{" +
            "\"testData\": [{" +
            "  \"extension\": {" +
            "    \"agriculturalActivities\": {" +
            "      \"engagedInLivestockProduction\": false," + // Boolean false
            "      \"engagedInCropProduction\": true," + // Boolean true
            "      \"canReadWrite\": true," + // Boolean true
            "      \"mainSourceFarmLabour\": \"family\"," + // String (unchanged)
            "      \"fertilizerApplied\": false," + // Boolean false
            "      \"pesticidesApplied\": true" + // Boolean true
            "    }" +
            "  }" +
            "}]}";

        JsonNode rootNode = mapper.readTree(docSubmitterJson);
        JsonNode firstFarmer = rootNode.get("testData").get(0);
        JsonNode extension = firstFarmer.get("extension");
        JsonNode agActivities = extension.get("agriculturalActivities");

        // Test normalization of boolean fields to correct LOV values

        // livestockProduction uses 1/2
        String livestockNormalized = normalizer.normalizeToLOV(
            agActivities.get("engagedInLivestockProduction"), "livestockProduction");
        assertEquals("Boolean false should map to '2' for livestockProduction", "2", livestockNormalized);

        // cropProduction uses yes/no
        String cropNormalized = normalizer.normalizeToLOV(
            agActivities.get("engagedInCropProduction"), "cropProduction");
        assertEquals("Boolean true should map to 'yes' for cropProduction", "yes", cropNormalized);

        // canReadWrite uses yes/no
        String readWriteNormalized = normalizer.normalizeToLOV(
            agActivities.get("canReadWrite"), "canReadWrite");
        assertEquals("Boolean true should map to 'yes' for canReadWrite", "yes", readWriteNormalized);

        // fertilizerApplied uses yes/no
        String fertilizerNormalized = normalizer.normalizeToLOV(
            agActivities.get("fertilizerApplied"), "fertilizerApplied");
        assertEquals("Boolean false should map to 'no' for fertilizerApplied", "no", fertilizerNormalized);

        // pesticidesApplied uses yes/no
        String pesticidesNormalized = normalizer.normalizeToLOV(
            agActivities.get("pesticidesApplied"), "pesticidesApplied");
        assertEquals("Boolean true should map to 'yes' for pesticidesApplied", "yes", pesticidesNormalized);

        // String values should pass through unchanged
        String labourNormalized = normalizer.normalizeToLOV(
            agActivities.get("mainSourceFarmLabour"), "mainSourceFarmLabour");
        assertEquals("String 'family' should map to '1' based on custom mapping", "1", labourNormalized);
    }

    @Test
    public void testMixedBooleanAndStringFormats() throws Exception {
        // Test a document with mixed formats (some booleans, some strings)
        ObjectNode mixedDoc = mapper.createObjectNode();

        // Add mixed format fields
        mixedDoc.put("field1", true); // Boolean
        mixedDoc.put("field2", "1"); // String LOV
        mixedDoc.put("field3", false); // Boolean
        mixedDoc.put("field4", "no"); // String LOV
        mixedDoc.put("field5", "2"); // String LOV
        mixedDoc.put("field6", "yes"); // String LOV

        // Test normalization for different field configurations

        // Field configured for 1/2
        assertEquals("1", normalizer.normalizeToLOV(mixedDoc.get("field1"), "livestockProduction"));
        assertEquals("1", normalizer.normalizeToLOV(mixedDoc.get("field2"), "livestockProduction"));
        assertEquals("2", normalizer.normalizeToLOV(mixedDoc.get("field3"), "livestockProduction"));
        assertEquals("2", normalizer.normalizeToLOV(mixedDoc.get("field4"), "livestockProduction"));
        assertEquals("2", normalizer.normalizeToLOV(mixedDoc.get("field5"), "livestockProduction"));
        assertEquals("1", normalizer.normalizeToLOV(mixedDoc.get("field6"), "livestockProduction"));

        // Field configured for yes/no
        // Note: "1" and "2" strings are LOV_NUMERIC format, so they pass through unchanged
        // for fields that use yes/no configuration. This is expected behavior.
        assertEquals("yes", normalizer.normalizeToLOV(mixedDoc.get("field1"), "cropProduction"));
        assertEquals("1", normalizer.normalizeToLOV(mixedDoc.get("field2"), "cropProduction")); // "1" passes through
        assertEquals("no", normalizer.normalizeToLOV(mixedDoc.get("field3"), "cropProduction"));
        assertEquals("no", normalizer.normalizeToLOV(mixedDoc.get("field4"), "cropProduction"));
        assertEquals("2", normalizer.normalizeToLOV(mixedDoc.get("field5"), "cropProduction")); // "2" passes through
        assertEquals("yes", normalizer.normalizeToLOV(mixedDoc.get("field6"), "cropProduction"));
    }

    @Test
    public void testRelatedPersonWithBooleans() throws Exception {
        // Test relatedPerson/household members with boolean fields
        String json = "{" +
            "\"relatedPerson\": [{" +
            "  \"name\": {\"text\": \"Test Person\"}," +
            "  \"gender\": \"male\"," +
            "  \"extension\": {" +
            "    \"participatesInAgriculture\": true," + // Boolean instead of "1"
            "    \"chronicallyIll\": false" + // Boolean instead of "2"
            "  }" +
            "}]}";

        JsonNode rootNode = mapper.readTree(json);
        JsonNode relatedPerson = rootNode.get("relatedPerson").get(0);
        JsonNode extension = relatedPerson.get("extension");

        // These fields use 1/2 LOV values
        String participatesNormalized = normalizer.normalizeToLOV(
            extension.get("participatesInAgriculture"), "participatesInAgriculture");
        assertEquals("Boolean true should map to '1' for participatesInAgriculture", "1", participatesNormalized);

        String chronicallyIllNormalized = normalizer.normalizeToLOV(
            extension.get("chronicallyIll"), "chronicallyIll");
        assertEquals("Boolean false should map to '2' for chronicallyIll", "2", chronicallyIllNormalized);
    }

    @Test
    public void testBooleanStringVariants() throws Exception {
        // Test "true"/"false" string variants
        ObjectNode doc = mapper.createObjectNode();
        doc.put("field1", "true"); // String "true"
        doc.put("field2", "false"); // String "false"
        doc.put("field3", "TRUE"); // Uppercase
        doc.put("field4", "False"); // Mixed case

        // Test normalization for 1/2 fields
        assertEquals("1", normalizer.normalizeToLOV(doc.get("field1"), "livestockProduction"));
        assertEquals("2", normalizer.normalizeToLOV(doc.get("field2"), "livestockProduction"));
        assertEquals("1", normalizer.normalizeToLOV(doc.get("field3"), "livestockProduction"));
        assertEquals("2", normalizer.normalizeToLOV(doc.get("field4"), "livestockProduction"));

        // Test normalization for yes/no fields
        assertEquals("yes", normalizer.normalizeToLOV(doc.get("field1"), "cropProduction"));
        assertEquals("no", normalizer.normalizeToLOV(doc.get("field2"), "cropProduction"));
        assertEquals("yes", normalizer.normalizeToLOV(doc.get("field3"), "cropProduction"));
        assertEquals("no", normalizer.normalizeToLOV(doc.get("field4"), "cropProduction"));
    }

    @Test
    public void testCompleteDocSubmitterScenario() throws Exception {
        // Comprehensive test simulating full DocSubmitter output
        String fullJson = "{" +
            "\"testData\": [{" +
            "  \"id\": \"test-farmer-001\"," +
            "  \"extension\": {" +
            "    \"cooperativeMember\": false," + // Boolean
            "    \"agriculturalActivities\": {" +
            "      \"engagedInLivestockProduction\": true," + // Boolean
            "      \"engagedInCropProduction\": true," + // Boolean
            "      \"canReadWrite\": false," + // Boolean
            "      \"mainSourceFarmLabour\": \"Seasonally Hired\"," + // String (with mapping)
            "      \"fertilizerApplied\": true," + // Boolean
            "      \"pesticidesApplied\": false" + // Boolean
            "    }," +
            "    \"incomeAndPrograms\": {" +
            "      \"everOnISP\": true" + // Boolean
            "    }" +
            "  }," +
            "  \"relatedPerson\": [{" +
            "    \"extension\": {" +
            "      \"participatesInAgriculture\": false," + // Boolean
            "      \"chronicallyIll\": true" + // Boolean
            "    }" +
            "  }]" +
            "}]}";

        JsonNode rootNode = mapper.readTree(fullJson);
        JsonNode firstFarmer = rootNode.get("testData").get(0);
        JsonNode extension = firstFarmer.get("extension");

        // Test cooperativeMember (uses yes/no)
        String coopNormalized = normalizer.normalizeToLOV(
            extension.get("cooperativeMember"), "cooperativeMember");
        assertEquals("no", coopNormalized);

        // Test agriculturalActivities fields
        JsonNode agActivities = extension.get("agriculturalActivities");

        assertEquals("1", normalizer.normalizeToLOV(
            agActivities.get("engagedInLivestockProduction"), "livestockProduction"));
        assertEquals("yes", normalizer.normalizeToLOV(
            agActivities.get("engagedInCropProduction"), "cropProduction"));
        assertEquals("no", normalizer.normalizeToLOV(
            agActivities.get("canReadWrite"), "canReadWrite"));
        assertEquals("2", normalizer.normalizeToLOV(
            agActivities.get("mainSourceFarmLabour"), "mainSourceFarmLabour")); // Maps "Seasonally Hired" to "2"
        assertEquals("yes", normalizer.normalizeToLOV(
            agActivities.get("fertilizerApplied"), "fertilizerApplied"));
        assertEquals("no", normalizer.normalizeToLOV(
            agActivities.get("pesticidesApplied"), "pesticidesApplied"));

        // Test incomeAndPrograms
        JsonNode incomePrograms = extension.get("incomeAndPrograms");
        assertEquals("yes", normalizer.normalizeToLOV(
            incomePrograms.get("everOnISP"), "everOnISP"));

        // Test relatedPerson fields
        JsonNode relatedPerson = firstFarmer.get("relatedPerson").get(0);
        JsonNode personExt = relatedPerson.get("extension");

        assertEquals("2", normalizer.normalizeToLOV(
            personExt.get("participatesInAgriculture"), "participatesInAgriculture"));
        assertEquals("1", normalizer.normalizeToLOV(
            personExt.get("chronicallyIll"), "chronicallyIll"));
    }
}