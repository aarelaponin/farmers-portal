package global.govstack.processing.service.normalization;

import global.govstack.registration.receiver.service.normalization.ValueNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Comprehensive unit tests for ValueNormalizer
 */
public class ValueNormalizerTest {

    private ValueNormalizer normalizer;
    private ObjectMapper mapper;

    @Before
    public void setUp() {
        normalizer = new ValueNormalizer();
        mapper = new ObjectMapper();
    }

    // ==================== Basic Normalization Tests ====================

    @Test
    public void testNormalizeBoolean_To_OneTwo() {
        // Test fields that use 1/2 for yes/no (like livestockProduction)
        JsonNode trueNode = BooleanNode.TRUE;
        JsonNode falseNode = BooleanNode.FALSE;

        String result = normalizer.normalizeToLOV(trueNode, "livestockProduction");
        assertEquals("1", result);

        result = normalizer.normalizeToLOV(falseNode, "livestockProduction");
        assertEquals("2", result);
    }

    @Test
    public void testNormalizeBoolean_To_YesNo() {
        // Test fields that use yes/no strings (like cropProduction)
        JsonNode trueNode = BooleanNode.TRUE;
        JsonNode falseNode = BooleanNode.FALSE;

        String result = normalizer.normalizeToLOV(trueNode, "cropProduction");
        assertEquals("yes", result);

        result = normalizer.normalizeToLOV(falseNode, "cropProduction");
        assertEquals("no", result);
    }

    @Test
    public void testNormalizeBooleanString_To_OneTwo() {
        // Test "true"/"false" strings to 1/2
        JsonNode trueString = TextNode.valueOf("true");
        JsonNode falseString = TextNode.valueOf("false");

        String result = normalizer.normalizeToLOV(trueString, "participatesInAgriculture");
        assertEquals("1", result);

        result = normalizer.normalizeToLOV(falseString, "participatesInAgriculture");
        assertEquals("2", result);
    }

    @Test
    public void testNormalizeLOVNumeric_Passthrough() {
        // Test that "1" and "2" pass through unchanged for 1/2 fields
        JsonNode one = TextNode.valueOf("1");
        JsonNode two = TextNode.valueOf("2");

        String result = normalizer.normalizeToLOV(one, "livestockProduction");
        assertEquals("1", result);

        result = normalizer.normalizeToLOV(two, "livestockProduction");
        assertEquals("2", result);
    }

    @Test
    public void testNormalizeLOVText_To_OneTwo() {
        // Test "yes"/"no" to 1/2 for fields configured that way
        JsonNode yes = TextNode.valueOf("yes");
        JsonNode no = TextNode.valueOf("no");

        String result = normalizer.normalizeToLOV(yes, "livestockProduction");
        assertEquals("1", result);

        result = normalizer.normalizeToLOV(no, "livestockProduction");
        assertEquals("2", result);
    }

    @Test
    public void testNormalizeLOVText_Passthrough() {
        // Test "yes"/"no" pass through for fields configured for yes/no
        JsonNode yes = TextNode.valueOf("yes");
        JsonNode no = TextNode.valueOf("no");

        String result = normalizer.normalizeToLOV(yes, "cropProduction");
        assertEquals("yes", result);

        result = normalizer.normalizeToLOV(no, "cropProduction");
        assertEquals("no", result);
    }

    // ==================== Custom Mapping Tests ====================

    @Test
    public void testCustomMapping_MainSourceFarmLabour() {
        // Test numeric values
        JsonNode one = TextNode.valueOf("1");
        JsonNode two = TextNode.valueOf("2");
        JsonNode three = TextNode.valueOf("3");

        assertEquals("1", normalizer.normalizeToLOV(one, "mainSourceFarmLabour"));
        assertEquals("2", normalizer.normalizeToLOV(two, "mainSourceFarmLabour"));
        assertEquals("3", normalizer.normalizeToLOV(three, "mainSourceFarmLabour"));

        // Test text mappings
        JsonNode family = TextNode.valueOf("Family");
        JsonNode seasonal = TextNode.valueOf("Seasonally Hired");
        JsonNode permanent = TextNode.valueOf("Permanently Hired");

        assertEquals("1", normalizer.normalizeToLOV(family, "mainSourceFarmLabour"));
        assertEquals("2", normalizer.normalizeToLOV(seasonal, "mainSourceFarmLabour"));
        assertEquals("3", normalizer.normalizeToLOV(permanent, "mainSourceFarmLabour"));

        // Test lowercase variants
        JsonNode familyLower = TextNode.valueOf("family");
        JsonNode seasonalLower = TextNode.valueOf("seasonal");
        JsonNode permanentLower = TextNode.valueOf("permanent");

        assertEquals("1", normalizer.normalizeToLOV(familyLower, "mainSourceFarmLabour"));
        assertEquals("2", normalizer.normalizeToLOV(seasonalLower, "mainSourceFarmLabour"));
        assertEquals("3", normalizer.normalizeToLOV(permanentLower, "mainSourceFarmLabour"));
    }

    // ==================== Default Configuration Tests ====================

    @Test
    public void testDefaultConfiguration_UnknownField() {
        // Fields without specific config should use default 1/2 mapping
        JsonNode trueNode = BooleanNode.TRUE;
        JsonNode falseNode = BooleanNode.FALSE;

        String result = normalizer.normalizeToLOV(trueNode, "unknownField");
        assertEquals("1", result);

        result = normalizer.normalizeToLOV(falseNode, "unknownField");
        assertEquals("2", result);
    }

    @Test
    public void testDefaultConfiguration_NoFieldName() {
        // Test using default method without field name
        JsonNode trueNode = BooleanNode.TRUE;
        JsonNode falseNode = BooleanNode.FALSE;

        String result = normalizer.normalizeToLOV(trueNode);
        assertEquals("1", result);

        result = normalizer.normalizeToLOV(falseNode);
        assertEquals("2", result);
    }

    // ==================== Null Handling Tests ====================

    @Test
    public void testNullHandling() {
        assertNull(normalizer.normalizeToLOV(null, "livestockProduction"));
        assertNull(normalizer.normalizeToLOV(NullNode.instance, "livestockProduction"));
    }

    // ==================== Edge Cases ====================

    @Test
    public void testNumericNodes() {
        // Test actual numeric nodes (not strings)
        JsonNode oneAsInt = IntNode.valueOf(1);
        JsonNode twoAsInt = IntNode.valueOf(2);

        assertEquals("1", normalizer.normalizeToLOV(oneAsInt, "livestockProduction"));
        assertEquals("2", normalizer.normalizeToLOV(twoAsInt, "livestockProduction"));
    }

    @Test
    public void testCustomValues_Passthrough() {
        // Custom values should pass through unchanged if no mapping
        JsonNode custom = TextNode.valueOf("maybe");

        String result = normalizer.normalizeToLOV(custom, "livestockProduction");
        assertEquals("maybe", result);
    }

    @Test
    public void testCaseInsensitivity() {
        JsonNode yesUpper = TextNode.valueOf("YES");
        JsonNode noUpper = TextNode.valueOf("NO");
        JsonNode trueMixed = TextNode.valueOf("True");
        JsonNode falseMixed = TextNode.valueOf("False");

        assertEquals("1", normalizer.normalizeToLOV(yesUpper, "livestockProduction"));
        assertEquals("2", normalizer.normalizeToLOV(noUpper, "livestockProduction"));
        assertEquals("1", normalizer.normalizeToLOV(trueMixed, "livestockProduction"));
        assertEquals("2", normalizer.normalizeToLOV(falseMixed, "livestockProduction"));
    }

    // ==================== Configuration Management Tests ====================

    @Test
    public void testHasFieldConfig() {
        assertTrue(normalizer.hasFieldConfig("livestockProduction"));
        assertTrue(normalizer.hasFieldConfig("cropProduction"));
        assertTrue(normalizer.hasFieldConfig("mainSourceFarmLabour"));
        assertFalse(normalizer.hasFieldConfig("unknownField"));
    }

    @Test
    public void testGetFieldConfig() {
        ValueNormalizer.NormalizationConfig config = normalizer.getFieldConfig("livestockProduction");
        assertNotNull(config);
        assertEquals("1", config.getPositiveValue());
        assertEquals("2", config.getNegativeValue());

        config = normalizer.getFieldConfig("cropProduction");
        assertNotNull(config);
        assertEquals("yes", config.getPositiveValue());
        assertEquals("no", config.getNegativeValue());

        assertNull(normalizer.getFieldConfig("unknownField"));
    }

    @Test
    public void testAddFieldConfig() {
        ValueNormalizer.NormalizationConfig customConfig =
            new ValueNormalizer.NormalizationConfig("active", "inactive");

        normalizer.addFieldConfig("customField", customConfig);

        assertTrue(normalizer.hasFieldConfig("customField"));
        assertEquals("active", normalizer.normalizeToLOV(BooleanNode.TRUE, "customField"));
        assertEquals("inactive", normalizer.normalizeToLOV(BooleanNode.FALSE, "customField"));
    }

    // ==================== Service Config Factory Tests ====================

    @Test
    public void testCreateFromServiceConfig() {
        Map<String, Map<String, Object>> fieldMappings = new HashMap<>();

        // Add a field with normalization config
        Map<String, Object> fieldConfig = new HashMap<>();
        Map<String, Object> normalizationConfig = new HashMap<>();
        normalizationConfig.put("positive", "enabled");
        normalizationConfig.put("negative", "disabled");

        Map<String, String> customMappings = new HashMap<>();
        customMappings.put("on", "enabled");
        customMappings.put("off", "disabled");
        normalizationConfig.put("custom", customMappings);

        fieldConfig.put("normalization", normalizationConfig);
        fieldMappings.put("testField", fieldConfig);

        ValueNormalizer customNormalizer = ValueNormalizer.createFromServiceConfig(fieldMappings);

        assertTrue(customNormalizer.hasFieldConfig("testField"));
        assertEquals("enabled", customNormalizer.normalizeToLOV(BooleanNode.TRUE, "testField"));
        assertEquals("disabled", customNormalizer.normalizeToLOV(BooleanNode.FALSE, "testField"));
        assertEquals("enabled", customNormalizer.normalizeToLOV(TextNode.valueOf("on"), "testField"));
        assertEquals("disabled", customNormalizer.normalizeToLOV(TextNode.valueOf("off"), "testField"));
    }

    // ==================== Real-world Scenario Tests ====================

    @Test
    public void testBackwardCompatibility_TestDataJson() throws Exception {
        // Simulating values from test-data.json
        String json = "{" +
            "\"engagedInCropProduction\": \"yes\"," +
            "\"engagedInLivestockProduction\": \"1\"," +
            "\"participatesInAgriculture\": \"2\"," +
            "\"canReadWrite\": \"no\"" +
            "}";

        JsonNode node = mapper.readTree(json);

        // These should pass through correctly based on field configuration
        assertEquals("yes", normalizer.normalizeToLOV(node.get("engagedInCropProduction"), "cropProduction"));
        assertEquals("1", normalizer.normalizeToLOV(node.get("engagedInLivestockProduction"), "livestockProduction"));
        assertEquals("2", normalizer.normalizeToLOV(node.get("participatesInAgriculture"), "participatesInAgriculture"));
        assertEquals("no", normalizer.normalizeToLOV(node.get("canReadWrite"), "canReadWrite"));
    }

    @Test
    public void testNewFormat_DocSubmitterBooleans() throws Exception {
        // Simulating DocSubmitter boolean output
        String json = "{" +
            "\"engagedInCropProduction\": true," +
            "\"engagedInLivestockProduction\": false," +
            "\"participatesInAgriculture\": true," +
            "\"canReadWrite\": false" +
            "}";

        JsonNode node = mapper.readTree(json);

        // Booleans should be converted to appropriate LOV values
        assertEquals("yes", normalizer.normalizeToLOV(node.get("engagedInCropProduction"), "cropProduction"));
        assertEquals("2", normalizer.normalizeToLOV(node.get("engagedInLivestockProduction"), "livestockProduction"));
        assertEquals("1", normalizer.normalizeToLOV(node.get("participatesInAgriculture"), "participatesInAgriculture"));
        assertEquals("no", normalizer.normalizeToLOV(node.get("canReadWrite"), "canReadWrite"));
    }

    @Test
    public void testMixedDocument() throws Exception {
        // Document with mixed formats
        String json = "{" +
            "\"field1\": true," +
            "\"field2\": \"2\"," +
            "\"field3\": \"yes\"," +
            "\"field4\": \"false\"," +
            "\"field5\": 1," +
            "\"field6\": null" +
            "}";

        JsonNode node = mapper.readTree(json);

        assertEquals("1", normalizer.normalizeToLOV(node.get("field1"), "field1"));
        assertEquals("2", normalizer.normalizeToLOV(node.get("field2"), "field2"));
        assertEquals("yes", normalizer.normalizeToLOV(node.get("field3"), "cropProduction"));
        assertEquals("2", normalizer.normalizeToLOV(node.get("field4"), "field4"));
        assertEquals("1", normalizer.normalizeToLOV(node.get("field5"), "field5"));
        assertNull(normalizer.normalizeToLOV(node.get("field6"), "field6"));
    }

    // ==================== All Configured Fields Test ====================

    @Test
    public void testAllConfiguredFields() {
        // Test all fields that have default configurations

        // Fields using 1/2
        String[] oneTwoFields = {
            "livestockProduction", "participatesInAgriculture", "chronicallyIll"
        };

        for (String field : oneTwoFields) {
            assertEquals("Field " + field + " should map true to 1",
                "1", normalizer.normalizeToLOV(BooleanNode.TRUE, field));
            assertEquals("Field " + field + " should map false to 2",
                "2", normalizer.normalizeToLOV(BooleanNode.FALSE, field));
        }

        // Fields using yes/no
        String[] yesNoFields = {
            "cropProduction", "canReadWrite", "cooperativeMember",
            "everOnISP", "fertilizerApplied", "pesticidesApplied"
        };

        for (String field : yesNoFields) {
            assertEquals("Field " + field + " should map true to yes",
                "yes", normalizer.normalizeToLOV(BooleanNode.TRUE, field));
            assertEquals("Field " + field + " should map false to no",
                "no", normalizer.normalizeToLOV(BooleanNode.FALSE, field));
        }
    }
}