package global.govstack.processing.service.metadata;

import global.govstack.registration.receiver.service.metadata.DataTransformer;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for DataTransformer
 */
public class DataTransformerTest {

    private DataTransformer transformer;

    @Before
    public void setUp() {
        transformer = new DataTransformer();
    }

    @Test
    public void testTransformDate_ISO8601() {
        String input = "2025-01-20T10:30:00Z";
        String result = transformer.transformValue(input, "date_ISO8601");
        assertEquals("2025-01-20", result);
    }

    @Test
    public void testTransformDate_SimpleFormat() {
        String input = "1994-05-15";
        String result = transformer.transformValue(input, "date_ISO8601");
        assertEquals("1994-05-15", result);
    }

    @Test
    public void testTransformBooleanToYesNo_True() {
        assertEquals("yes", transformer.transformValue("true", "yesNoBoolean"));
        assertEquals("yes", transformer.transformValue("1", "yesNoBoolean"));
        assertEquals("yes", transformer.transformValue("yes", "yesNoBoolean"));
    }

    @Test
    public void testTransformBooleanToYesNo_False() {
        assertEquals("no", transformer.transformValue("false", "yesNoBoolean"));
        assertEquals("no", transformer.transformValue("0", "yesNoBoolean"));
        assertEquals("no", transformer.transformValue("no", "yesNoBoolean"));
    }

    @Test
    public void testTransformNumeric() {
        assertEquals("25", transformer.transformValue("25", "numeric"));
        assertEquals("3.5", transformer.transformValue("3.5", "numeric"));
        assertEquals("100", transformer.transformValue("100 hectares", "numeric"));
        assertEquals("-29.363611", transformer.transformValue("-29.363611", "numeric"));
    }

    @Test
    public void testTransformMultiCheckbox() {
        String input = "[\"crop_rotation\", \"mulching\", \"contours\"]";
        String result = transformer.transformValue(input, "multiCheckbox");
        assertEquals("crop_rotation;mulching;contours", result);
    }

    @Test
    public void testTransformMultiCheckbox_CommaSeparated() {
        String input = "crop_rotation,mulching,contours";
        String result = transformer.transformValue(input, "multiCheckbox");
        assertEquals("crop_rotation;mulching;contours", result);
    }

    @Test
    public void testApplyValueMapping_Gender() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("male", "1");
        mapping.put("female", "2");

        assertEquals("1", transformer.applyValueMapping("male", mapping));
        assertEquals("2", transformer.applyValueMapping("female", mapping));
    }

    @Test
    public void testApplyValueMapping_RelationshipCodes() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("head", "SELF");
        mapping.put("spouse", "SPOUSE");
        mapping.put("child", "CHILD");
        mapping.put("parent", "PARENT");

        assertEquals("SELF", transformer.applyValueMapping("head", mapping));
        assertEquals("SPOUSE", transformer.applyValueMapping("spouse", mapping));
        assertEquals("CHILD", transformer.applyValueMapping("child", mapping));
        assertEquals("PARENT", transformer.applyValueMapping("parent", mapping));
    }

    @Test
    public void testApplyValueMapping_BooleanToNumber() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("true", "1");
        mapping.put("false", "2");

        assertEquals("1", transformer.applyValueMapping("true", mapping));
        assertEquals("2", transformer.applyValueMapping("false", mapping));
    }

    @Test
    public void testApplyValueMapping_ResidencyType() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("1", "rural");
        mapping.put("2", "urban");

        assertEquals("rural", transformer.applyValueMapping("1", mapping));
        assertEquals("urban", transformer.applyValueMapping("2", mapping));
    }

    @Test
    public void testTransformObjectToString_Boolean() {
        Boolean boolValue = Boolean.TRUE;
        assertEquals("true", transformer.transformObjectToString(boolValue, null));
        assertEquals("yes", transformer.transformObjectToString(boolValue, "yesnoboolean"));
    }

    @Test
    public void testTransformObjectToString_Number() {
        Integer intValue = 42;
        Double doubleValue = 3.14;

        assertEquals("42", transformer.transformObjectToString(intValue, null));
        assertEquals("3.14", transformer.transformObjectToString(doubleValue, null));
    }

    @Test
    public void testTransformValue_NullHandling() {
        assertNull(transformer.transformValue(null, "date_ISO8601"));
        assertEquals("", transformer.transformValue("", "date_ISO8601"));
    }

    @Test
    public void testTransformValue_UnknownTransformation() {
        String input = "test value";
        assertEquals(input, transformer.transformValue(input, "unknownType"));
        assertEquals(input, transformer.transformValue(input, null));
    }

    @Test
    public void testTransformValue_InvalidDate() {
        String input = "not-a-date";
        String result = transformer.transformValue(input, "date_ISO8601");
        assertEquals(input, result); // Should return original if can't parse
    }

    @Test
    public void testTransformValue_InvalidNumeric() {
        String input = "not-a-number";
        String result = transformer.transformValue(input, "numeric");
        assertEquals("0", result); // Should return "0" for invalid numbers
    }
}