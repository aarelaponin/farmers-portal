package global.govstack.processing.service.normalization;

import global.govstack.registration.receiver.service.normalization.ValueFormatDetector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for ValueFormatDetector
 */
public class ValueFormatDetectorTest {

    private ValueFormatDetector detector;
    private ObjectMapper mapper;

    @Before
    public void setUp() {
        detector = new ValueFormatDetector();
        mapper = new ObjectMapper();
    }

    // ==================== Format Detection Tests ====================

    @Test
    public void testDetectBooleanFormat() {
        JsonNode trueNode = BooleanNode.TRUE;
        JsonNode falseNode = BooleanNode.FALSE;

        assertEquals(ValueFormatDetector.Format.BOOLEAN, detector.detectFormat(trueNode));
        assertEquals(ValueFormatDetector.Format.BOOLEAN, detector.detectFormat(falseNode));
    }

    @Test
    public void testDetectBooleanStringFormat() {
        JsonNode trueString = TextNode.valueOf("true");
        JsonNode falseString = TextNode.valueOf("false");
        JsonNode trueUpperCase = TextNode.valueOf("TRUE");

        assertEquals(ValueFormatDetector.Format.BOOLEAN_STRING, detector.detectFormat(trueString));
        assertEquals(ValueFormatDetector.Format.BOOLEAN_STRING, detector.detectFormat(falseString));
        assertEquals(ValueFormatDetector.Format.BOOLEAN_STRING, detector.detectFormat(trueUpperCase));
    }

    @Test
    public void testDetectLOVNumericFormat() {
        JsonNode one = TextNode.valueOf("1");
        JsonNode two = TextNode.valueOf("2");
        JsonNode oneAsNumber = IntNode.valueOf(1);
        JsonNode twoAsNumber = IntNode.valueOf(2);

        assertEquals(ValueFormatDetector.Format.LOV_NUMERIC, detector.detectFormat(one));
        assertEquals(ValueFormatDetector.Format.LOV_NUMERIC, detector.detectFormat(two));
        assertEquals(ValueFormatDetector.Format.LOV_NUMERIC, detector.detectFormat(oneAsNumber));
        assertEquals(ValueFormatDetector.Format.LOV_NUMERIC, detector.detectFormat(twoAsNumber));
    }

    @Test
    public void testDetectLOVTextFormat() {
        JsonNode yes = TextNode.valueOf("yes");
        JsonNode no = TextNode.valueOf("no");
        JsonNode yesUpperCase = TextNode.valueOf("YES");

        assertEquals(ValueFormatDetector.Format.LOV_TEXT, detector.detectFormat(yes));
        assertEquals(ValueFormatDetector.Format.LOV_TEXT, detector.detectFormat(no));
        assertEquals(ValueFormatDetector.Format.LOV_TEXT, detector.detectFormat(yesUpperCase));
    }

    @Test
    public void testDetectCustomFormat() {
        JsonNode custom1 = TextNode.valueOf("maybe");
        JsonNode custom2 = TextNode.valueOf("unknown");
        JsonNode custom3 = TextNode.valueOf("3");

        assertEquals(ValueFormatDetector.Format.CUSTOM, detector.detectFormat(custom1));
        assertEquals(ValueFormatDetector.Format.CUSTOM, detector.detectFormat(custom2));
        assertEquals(ValueFormatDetector.Format.CUSTOM, detector.detectFormat(custom3));
    }

    @Test
    public void testDetectNullFormat() {
        JsonNode nullNode = NullNode.instance;
        JsonNode actualNull = null;

        assertEquals(ValueFormatDetector.Format.NULL, detector.detectFormat(nullNode));
        assertEquals(ValueFormatDetector.Format.NULL, detector.detectFormat(actualNull));
    }

    // ==================== Positive Value Tests ====================

    @Test
    public void testIsPositiveValueWithBoolean() {
        assertTrue(detector.isPositiveValue(BooleanNode.TRUE));
        assertFalse(detector.isPositiveValue(BooleanNode.FALSE));
    }

    @Test
    public void testIsPositiveValueWithBooleanString() {
        assertTrue(detector.isPositiveValue(TextNode.valueOf("true")));
        assertTrue(detector.isPositiveValue(TextNode.valueOf("TRUE")));
        assertFalse(detector.isPositiveValue(TextNode.valueOf("false")));
    }

    @Test
    public void testIsPositiveValueWithLOVNumeric() {
        assertTrue(detector.isPositiveValue(TextNode.valueOf("1")));
        assertTrue(detector.isPositiveValue(IntNode.valueOf(1)));
        assertFalse(detector.isPositiveValue(TextNode.valueOf("2")));
        assertFalse(detector.isPositiveValue(IntNode.valueOf(2)));
    }

    @Test
    public void testIsPositiveValueWithLOVText() {
        assertTrue(detector.isPositiveValue(TextNode.valueOf("yes")));
        assertTrue(detector.isPositiveValue(TextNode.valueOf("YES")));
        assertFalse(detector.isPositiveValue(TextNode.valueOf("no")));
    }

    // ==================== Negative Value Tests ====================

    @Test
    public void testIsNegativeValueWithBoolean() {
        assertTrue(detector.isNegativeValue(BooleanNode.FALSE));
        assertFalse(detector.isNegativeValue(BooleanNode.TRUE));
    }

    @Test
    public void testIsNegativeValueWithBooleanString() {
        assertTrue(detector.isNegativeValue(TextNode.valueOf("false")));
        assertTrue(detector.isNegativeValue(TextNode.valueOf("FALSE")));
        assertFalse(detector.isNegativeValue(TextNode.valueOf("true")));
    }

    @Test
    public void testIsNegativeValueWithLOVNumeric() {
        assertTrue(detector.isNegativeValue(TextNode.valueOf("2")));
        assertTrue(detector.isNegativeValue(IntNode.valueOf(2)));
        assertFalse(detector.isNegativeValue(TextNode.valueOf("1")));
        assertFalse(detector.isNegativeValue(IntNode.valueOf(1)));
    }

    @Test
    public void testIsNegativeValueWithLOVText() {
        assertTrue(detector.isNegativeValue(TextNode.valueOf("no")));
        assertTrue(detector.isNegativeValue(TextNode.valueOf("NO")));
        assertFalse(detector.isNegativeValue(TextNode.valueOf("yes")));
    }

    // ==================== Edge Cases ====================

    @Test
    public void testCustomValuesNotPositiveOrNegative() {
        JsonNode custom = TextNode.valueOf("maybe");
        assertFalse(detector.isPositiveValue(custom));
        assertFalse(detector.isNegativeValue(custom));
    }

    @Test
    public void testNullValuesNotPositiveOrNegative() {
        JsonNode nullNode = NullNode.instance;
        assertFalse(detector.isPositiveValue(nullNode));
        assertFalse(detector.isNegativeValue(nullNode));
    }

    @Test
    public void testWhitespaceHandling() {
        JsonNode yesWithSpace = TextNode.valueOf(" yes ");
        JsonNode oneWithSpace = TextNode.valueOf(" 1 ");

        assertEquals(ValueFormatDetector.Format.LOV_TEXT, detector.detectFormat(yesWithSpace));
        assertEquals(ValueFormatDetector.Format.LOV_NUMERIC, detector.detectFormat(oneWithSpace));
    }

    // ==================== Format Description Tests ====================

    @Test
    public void testGetFormatDescription() {
        assertEquals("Numeric LOV (1/2)", detector.getFormatDescription(ValueFormatDetector.Format.LOV_NUMERIC));
        assertEquals("Text LOV (yes/no)", detector.getFormatDescription(ValueFormatDetector.Format.LOV_TEXT));
        assertEquals("Boolean (true/false)", detector.getFormatDescription(ValueFormatDetector.Format.BOOLEAN));
        assertEquals("Boolean string (\"true\"/\"false\")", detector.getFormatDescription(ValueFormatDetector.Format.BOOLEAN_STRING));
        assertEquals("Custom string value", detector.getFormatDescription(ValueFormatDetector.Format.CUSTOM));
        assertEquals("Null/missing value", detector.getFormatDescription(ValueFormatDetector.Format.NULL));
    }

    // ==================== Real-world Scenario Tests ====================

    @Test
    public void testTestDataJsonFormat() throws Exception {
        // Simulating test-data.json format
        String json = "{\"engagedInLivestockProduction\": \"1\"}";
        JsonNode node = mapper.readTree(json);
        JsonNode value = node.get("engagedInLivestockProduction");

        assertEquals(ValueFormatDetector.Format.LOV_NUMERIC, detector.detectFormat(value));
        assertTrue(detector.isPositiveValue(value));
    }

    @Test
    public void testDocSubmitterBooleanFormat() throws Exception {
        // Simulating DocSubmitter output
        String json = "{\"engagedInLivestockProduction\": false}";
        JsonNode node = mapper.readTree(json);
        JsonNode value = node.get("engagedInLivestockProduction");

        assertEquals(ValueFormatDetector.Format.BOOLEAN, detector.detectFormat(value));
        assertTrue(detector.isNegativeValue(value));
    }

    @Test
    public void testMixedFormatsInDocument() throws Exception {
        // Simulating mixed formats in one document
        String json = "{" +
            "\"field1\": true," +
            "\"field2\": \"2\"," +
            "\"field3\": \"yes\"," +
            "\"field4\": \"false\"" +
            "}";
        JsonNode node = mapper.readTree(json);

        assertEquals(ValueFormatDetector.Format.BOOLEAN, detector.detectFormat(node.get("field1")));
        assertEquals(ValueFormatDetector.Format.LOV_NUMERIC, detector.detectFormat(node.get("field2")));
        assertEquals(ValueFormatDetector.Format.LOV_TEXT, detector.detectFormat(node.get("field3")));
        assertEquals(ValueFormatDetector.Format.BOOLEAN_STRING, detector.detectFormat(node.get("field4")));
    }
}