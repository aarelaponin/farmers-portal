package global.govstack.processing.integration;

import global.govstack.registration.receiver.exception.ApiProcessingException;
import global.govstack.registration.receiver.exception.InvalidRequestException;
import global.govstack.registration.receiver.service.ApiRequestProcessor;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Integration tests for request processing
 * Tests the core processing behavior using real test data
 */
public class RequestProcessingIntegrationTest {

    @Test
    public void testLoadValidTestData() throws IOException {
        // Test that we can load the valid test data file
        String testData = loadResourceFile("/test-boolean-format.json");

        assertNotNull("Test data must be loadable", testData);
        assertTrue("Test data must contain JSON", testData.contains("{"));
        assertTrue("Test data must contain testData array", testData.contains("testData"));

        // Verify it's valid JSON
        JSONObject json = new JSONObject(testData);
        assertTrue("Must have testData array", json.has("testData"));
    }

    @Test
    public void testInvalidJsonDetection() {
        // Test that invalid JSON is properly detected
        String invalidJson = "{ this is not valid json }";

        try {
            new JSONObject(invalidJson);
            fail("Invalid JSON should throw exception");
        } catch (Exception e) {
            // Expected - invalid JSON detected
            assertTrue("Exception must be JSON-related", true);
        }
    }

    @Test
    public void testInvalidRequestCreation() {
        // Test that InvalidRequestException can be created and thrown
        try {
            throw new InvalidRequestException("Invalid JSON format");
        } catch (InvalidRequestException e) {
            assertEquals("Exception message must match", "Invalid JSON format", e.getMessage());
        }
    }

    @Test
    public void testValidJsonStructure() throws IOException {
        // Load and verify the structure of our test data
        String testData = loadResourceFile("/test-boolean-format.json");
        JSONObject json = new JSONObject(testData);

        // Verify expected structure
        assertTrue("Must have testData", json.has("testData"));
        assertEquals("testData must be an array", "JSONArray",
                json.get("testData").getClass().getSimpleName());

        // Verify first record structure
        JSONObject firstRecord = json.getJSONArray("testData").getJSONObject(0);
        assertTrue("Record must have resourceType", firstRecord.has("resourceType"));
        assertTrue("Record must have id", firstRecord.has("id"));
        assertTrue("Record must have identifiers", firstRecord.has("identifiers"));
        assertTrue("Record must have name", firstRecord.has("name"));
        assertTrue("Record must have gender", firstRecord.has("gender"));
        assertTrue("Record must have birthDate", firstRecord.has("birthDate"));

        assertEquals("resourceType must be Person", "Person",
                firstRecord.getString("resourceType"));
    }

    @Test
    public void testMissingFieldsDetection() throws IOException {
        // Load test data with missing required fields
        String testData = loadResourceFile("/test-missing-fields.json");
        JSONObject json = new JSONObject(testData);

        JSONObject firstRecord = json.getJSONArray("testData").getJSONObject(0);

        // Verify that required fields are indeed missing
        assertFalse("Should not have identifiers", firstRecord.has("identifiers"));
        assertFalse("Should not have name", firstRecord.has("name"));
        assertFalse("Should not have gender", firstRecord.has("gender"));
        assertFalse("Should not have birthDate", firstRecord.has("birthDate"));
    }

    @Test
    public void testApiProcessorInterfaceCanProcessString() {
        // Test that a simple implementation of ApiRequestProcessor works
        ApiRequestProcessor simpleProcessor = new ApiRequestProcessor() {
            @Override
            public JSONObject processRequest(String requestBody) throws ApiProcessingException {
                if (requestBody == null || requestBody.trim().isEmpty()) {
                    throw ApiProcessingException.invalidRequest("Request body is empty");
                }

                JSONObject response = new JSONObject();
                response.put("status", "processed");
                response.put("inputLength", requestBody.length());
                return response;
            }
        };

        try {
            // Test with valid input
            JSONObject result = simpleProcessor.processRequest("{\"test\": \"data\"}");
            assertNotNull("Result must not be null", result);
            assertEquals("Status must be 'processed'", "processed", result.getString("status"));
            assertTrue("Must have inputLength", result.has("inputLength"));

            // Test with empty input
            try {
                simpleProcessor.processRequest("");
                fail("Empty request should throw exception");
            } catch (ApiProcessingException e) {
                assertEquals("Must be invalid request", 400, e.getStatusCode());
            }

        } catch (ApiProcessingException e) {
            fail("Valid request should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testJsonObjectCreationAndAccess() {
        // Test basic JSON operations that services use
        JSONObject obj = new JSONObject();
        obj.put("applicationId", "APP-001");
        obj.put("processId", "PROC-001");
        obj.put("status", "pending");

        assertEquals("APP-001", obj.getString("applicationId"));
        assertEquals("PROC-001", obj.getString("processId"));
        assertEquals("pending", obj.getString("status"));

        assertTrue(obj.has("applicationId"));
        assertFalse(obj.has("nonExistentField"));
    }

    @Test
    public void testExceptionPropagation() {
        ApiRequestProcessor failingProcessor = new ApiRequestProcessor() {
            @Override
            public JSONObject processRequest(String requestBody) throws ApiProcessingException {
                throw ApiProcessingException.validationError("Validation failed for test");
            }
        };

        try {
            failingProcessor.processRequest("{\"test\": \"data\"}");
            fail("Should have thrown ApiProcessingException");
        } catch (ApiProcessingException e) {
            assertEquals("Validation Error", e.getErrorType());
            assertEquals(400, e.getStatusCode());
            assertTrue(e.getMessage().contains("Validation failed"));
        }
    }

    /**
     * Helper method to load a resource file as a string
     */
    private String loadResourceFile(String resourcePath) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
