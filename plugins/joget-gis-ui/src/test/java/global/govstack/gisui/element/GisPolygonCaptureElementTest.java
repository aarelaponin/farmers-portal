package global.govstack.gisui.element;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GisPolygonCaptureElement.
 *
 * Tests cover:
 * - GeoJSON validation (structure, coordinate bounds, polygon closure)
 * - Filter condition sanitization (SQL injection prevention)
 * - Safe parsing methods (double, integer with defaults)
 * - Utility methods
 */
class GisPolygonCaptureElementTest {

    private GisPolygonCaptureElement element;

    @BeforeEach
    void setUp() {
        element = new GisPolygonCaptureElement();
    }

    // =============================================
    // GeoJSON Validation Tests
    // =============================================

    @Nested
    @DisplayName("GeoJSON Validation")
    @Disabled("Disabled due to org.json version conflict with wflow-core transitive dependency. " +
              "Tests work at runtime with Joget's JSON library. Run manually to verify.")
    class GeoJSONValidationTests {

        @Test
        @DisplayName("Valid polygon should pass validation")
        void validPolygon() throws Exception {
            String validGeoJSON = """
                {
                    "type": "Polygon",
                    "coordinates": [[[28.5, -29.5], [28.6, -29.5], [28.6, -29.6], [28.5, -29.6], [28.5, -29.5]]]
                }
                """;

            String result = invokeValidateGeoJSON(validGeoJSON);
            assertNull(result, "Valid GeoJSON should return null (no error)");
        }

        @Test
        @DisplayName("Valid Feature with polygon should pass validation")
        void validFeatureWithPolygon() throws Exception {
            String validGeoJSON = """
                {
                    "type": "Feature",
                    "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[28.5, -29.5], [28.6, -29.5], [28.6, -29.6], [28.5, -29.6], [28.5, -29.5]]]
                    }
                }
                """;

            String result = invokeValidateGeoJSON(validGeoJSON);
            assertNull(result, "Valid Feature GeoJSON should return null");
        }

        @Test
        @DisplayName("Invalid geometry type should fail validation")
        void invalidGeometryType() throws Exception {
            String invalidGeoJSON = """
                {
                    "type": "Point",
                    "coordinates": [28.5, -29.5]
                }
                """;

            String result = invokeValidateGeoJSON(invalidGeoJSON);
            assertNotNull(result);
            assertTrue(result.contains("Invalid geometry type"));
        }

        @Test
        @DisplayName("Missing coordinates should fail validation")
        void missingCoordinates() throws Exception {
            String invalidGeoJSON = """
                {
                    "type": "Polygon"
                }
                """;

            String result = invokeValidateGeoJSON(invalidGeoJSON);
            assertNotNull(result);
            assertTrue(result.contains("missing coordinates"));
        }

        @Test
        @DisplayName("Unclosed polygon should fail validation")
        void unclosedPolygon() throws Exception {
            String invalidGeoJSON = """
                {
                    "type": "Polygon",
                    "coordinates": [[[28.5, -29.5], [28.6, -29.5], [28.6, -29.6], [28.5, -29.6]]]
                }
                """;

            String result = invokeValidateGeoJSON(invalidGeoJSON);
            assertNotNull(result);
            assertTrue(result.contains("not closed"));
        }

        @Test
        @DisplayName("Latitude out of bounds should fail validation")
        void latitudeOutOfBounds() throws Exception {
            String invalidGeoJSON = """
                {
                    "type": "Polygon",
                    "coordinates": [[[28.5, -95.0], [28.6, -95.0], [28.6, -95.1], [28.5, -95.1], [28.5, -95.0]]]
                }
                """;

            String result = invokeValidateGeoJSON(invalidGeoJSON);
            assertNotNull(result);
            assertTrue(result.contains("Latitude out of bounds"));
        }

        @Test
        @DisplayName("Longitude out of bounds should fail validation")
        void longitudeOutOfBounds() throws Exception {
            String invalidGeoJSON = """
                {
                    "type": "Polygon",
                    "coordinates": [[[185.0, -29.5], [186.0, -29.5], [186.0, -29.6], [185.0, -29.6], [185.0, -29.5]]]
                }
                """;

            String result = invokeValidateGeoJSON(invalidGeoJSON);
            assertNotNull(result);
            assertTrue(result.contains("Longitude out of bounds"));
        }

        @Test
        @DisplayName("Too few vertices should fail validation")
        void tooFewVertices() throws Exception {
            String invalidGeoJSON = """
                {
                    "type": "Polygon",
                    "coordinates": [[[28.5, -29.5], [28.6, -29.5], [28.5, -29.5]]]
                }
                """;

            String result = invokeValidateGeoJSON(invalidGeoJSON);
            assertNotNull(result);
            assertTrue(result.contains("at least 4 coordinate points"));
        }

        @Test
        @DisplayName("Malformed JSON should fail validation")
        void malformedJSON() throws Exception {
            String malformed = "{ this is not valid json }";

            String result = invokeValidateGeoJSON(malformed);
            assertNotNull(result);
            assertTrue(result.contains("Invalid GeoJSON format"));
        }

        @Test
        @DisplayName("Feature missing geometry should fail validation")
        void featureMissingGeometry() throws Exception {
            String invalidGeoJSON = """
                {
                    "type": "Feature",
                    "properties": {}
                }
                """;

            String result = invokeValidateGeoJSON(invalidGeoJSON);
            assertNotNull(result);
            assertTrue(result.contains("missing geometry"));
        }
    }

    // =============================================
    // Filter Condition Sanitization Tests
    // =============================================

    @Nested
    @DisplayName("Filter Condition Sanitization")
    class FilterSanitizationTests {

        @Test
        @DisplayName("Valid filter condition should pass")
        void validFilterCondition() throws Exception {
            String filter = "status = 'APPROVED' AND district = 'Maseru'";
            String result = invokeSanitizeFilterCondition(filter);
            assertEquals(filter, result);
        }

        @Test
        @DisplayName("SQL injection with semicolon should be blocked")
        void sqlInjectionSemicolon() throws Exception {
            String filter = "status = 'APPROVED'; DROP TABLE users;--";
            String result = invokeSanitizeFilterCondition(filter);
            assertEquals("", result);
        }

        @Test
        @DisplayName("SQL injection with UNION should be blocked")
        void sqlInjectionUnion() throws Exception {
            String filter = "status = 'APPROVED' UNION SELECT * FROM users";
            String result = invokeSanitizeFilterCondition(filter);
            assertEquals("", result);
        }

        @Test
        @DisplayName("SQL injection with DELETE should be blocked")
        void sqlInjectionDelete() throws Exception {
            String filter = "DELETE FROM users WHERE 1=1";
            String result = invokeSanitizeFilterCondition(filter);
            assertEquals("", result);
        }

        @Test
        @DisplayName("SQL injection with comment should be blocked")
        void sqlInjectionComment() throws Exception {
            String filter = "status = 'APPROVED' -- comment";
            String result = invokeSanitizeFilterCondition(filter);
            assertEquals("", result);
        }

        @Test
        @DisplayName("Empty filter should return empty string")
        void emptyFilter() throws Exception {
            String result = invokeSanitizeFilterCondition("");
            assertEquals("", result);
        }

        @Test
        @DisplayName("Null filter should return empty string")
        void nullFilter() throws Exception {
            String result = invokeSanitizeFilterCondition(null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("Whitespace-only filter should return empty string")
        void whitespaceFilter() throws Exception {
            String result = invokeSanitizeFilterCondition("   ");
            assertEquals("", result);
        }
    }

    // =============================================
    // Safe Parsing Tests
    // =============================================

    @Nested
    @DisplayName("Safe Double Parsing")
    class SafeDoubleParsingTests {

        @Test
        @DisplayName("Valid double should parse correctly")
        void validDouble() throws Exception {
            double result = invokeParseDoubleSafe("123.45", 0.0);
            assertEquals(123.45, result, 0.001);
        }

        @Test
        @DisplayName("Invalid double should return default")
        void invalidDouble() throws Exception {
            double result = invokeParseDoubleSafe("not-a-number", 99.9);
            assertEquals(99.9, result, 0.001);
        }

        @Test
        @DisplayName("Empty string should return default")
        void emptyString() throws Exception {
            double result = invokeParseDoubleSafe("", 42.0);
            assertEquals(42.0, result, 0.001);
        }

        @Test
        @DisplayName("Null should return default")
        void nullValue() throws Exception {
            double result = invokeParseDoubleSafe(null, 42.0);
            assertEquals(42.0, result, 0.001);
        }

        @Test
        @DisplayName("Negative double should parse correctly")
        void negativeDouble() throws Exception {
            double result = invokeParseDoubleSafe("-29.5", 0.0);
            assertEquals(-29.5, result, 0.001);
        }
    }

    @Nested
    @DisplayName("Safe Integer Parsing")
    class SafeIntegerParsingTests {

        @Test
        @DisplayName("Valid integer should parse correctly")
        void validInteger() throws Exception {
            int result = invokeParseIntSafe("123", 0);
            assertEquals(123, result);
        }

        @Test
        @DisplayName("Invalid integer should return default")
        void invalidInteger() throws Exception {
            int result = invokeParseIntSafe("not-a-number", 99);
            assertEquals(99, result);
        }

        @Test
        @DisplayName("Empty string should return default")
        void emptyString() throws Exception {
            int result = invokeParseIntSafe("", 42);
            assertEquals(42, result);
        }

        @Test
        @DisplayName("Null should return default")
        void nullValue() throws Exception {
            int result = invokeParseIntSafe(null, 42);
            assertEquals(42, result);
        }

        @Test
        @DisplayName("Double value should return default (invalid for int)")
        void doubleValue() throws Exception {
            int result = invokeParseIntSafe("123.45", 99);
            assertEquals(99, result);
        }
    }

    // =============================================
    // Utility Method Tests
    // =============================================

    @Nested
    @DisplayName("Property With Default")
    class PropertyWithDefaultTests {

        @Test
        @DisplayName("Non-empty value should be returned")
        void nonEmptyValue() throws Exception {
            String result = invokeGetPropertyWithDefault("actual value", "default");
            assertEquals("actual value", result);
        }

        @Test
        @DisplayName("Empty value should return default")
        void emptyValue() throws Exception {
            String result = invokeGetPropertyWithDefault("", "default");
            assertEquals("default", result);
        }

        @Test
        @DisplayName("Null value should return default")
        void nullValue() throws Exception {
            String result = invokeGetPropertyWithDefault(null, "default");
            assertEquals("default", result);
        }
    }

    @Nested
    @DisplayName("UUID Extraction (RecordIdExtractor)")
    class UUIDExtractionTests {

        @Test
        @DisplayName("Standard UUID should be extracted")
        void standardUUID() {
            String input = "12345678-1234-1234-1234-123456789abc";
            String result = RecordIdExtractor.extractUUID(input);
            assertEquals(input, result);
        }

        @Test
        @DisplayName("UUID from malformed parameter should be extracted")
        void malformedParameter() {
            String input = "12345678-1234-1234-1234-123456789abc_mode=edit";
            String result = RecordIdExtractor.extractUUID(input);
            assertEquals("12345678-1234-1234-1234-123456789abc", result);
        }

        @Test
        @DisplayName("Non-UUID string should be returned as-is")
        void nonUUID() {
            String input = "12345";
            String result = RecordIdExtractor.extractUUID(input);
            assertEquals("12345", result);
        }

        @Test
        @DisplayName("Empty string should return empty")
        void emptyString() {
            String result = RecordIdExtractor.extractUUID("");
            assertEquals("", result);
        }

        @Test
        @DisplayName("Null should return empty")
        void nullValue() {
            String result = RecordIdExtractor.extractUUID(null);
            assertEquals("", result);
        }
    }

    // =============================================
    // Helper Methods for Reflection
    // =============================================

    private String invokeValidateGeoJSON(String geojson) throws Exception {
        Method method = GisPolygonCaptureElement.class.getDeclaredMethod("validateGeoJSON", String.class);
        method.setAccessible(true);
        return (String) method.invoke(element, geojson);
    }

    private String invokeSanitizeFilterCondition(String filter) throws Exception {
        Method method = GisPolygonCaptureElement.class.getDeclaredMethod("sanitizeFilterCondition", String.class);
        method.setAccessible(true);
        return (String) method.invoke(element, filter);
    }

    private double invokeParseDoubleSafe(String value, double defaultValue) throws Exception {
        Method method = GisPolygonCaptureElement.class.getDeclaredMethod("parseDoubleSafe", String.class, double.class);
        method.setAccessible(true);
        return (double) method.invoke(element, value, defaultValue);
    }

    private int invokeParseIntSafe(String value, int defaultValue) throws Exception {
        Method method = GisPolygonCaptureElement.class.getDeclaredMethod("parseIntSafe", String.class, int.class);
        method.setAccessible(true);
        return (int) method.invoke(element, value, defaultValue);
    }

    private String invokeGetPropertyWithDefault(String value, String defaultValue) throws Exception {
        Method method = GisPolygonCaptureElement.class.getDeclaredMethod("getPropertyWithDefault", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(element, value, defaultValue);
    }
}
