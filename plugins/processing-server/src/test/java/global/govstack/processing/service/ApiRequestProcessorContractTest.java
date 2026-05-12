package global.govstack.processing.service;

import global.govstack.registration.receiver.exception.ApiProcessingException;
import global.govstack.registration.receiver.service.ApiRequestProcessor;
import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Contract tests for ApiRequestProcessor interface
 * Verifies the core interface contract remains stable across refactorings
 */
public class ApiRequestProcessorContractTest {

    @Test
    public void testApiRequestProcessorInterfaceSignature() throws NoSuchMethodException {
        // Verify the interface has the expected method signature
        Method method = ApiRequestProcessor.class.getMethod("processRequest", String.class);

        assertNotNull("processRequest method must exist", method);
        assertEquals("processRequest must return JSONObject",
                JSONObject.class, method.getReturnType());

        Class<?>[] exceptions = method.getExceptionTypes();
        assertEquals("processRequest must declare 1 exception", 1, exceptions.length);
        assertEquals("processRequest must throw ApiProcessingException",
                ApiProcessingException.class, exceptions[0]);
    }

    @Test
    public void testApiRequestProcessorIsInterface() {
        // Verify ApiRequestProcessor is an interface
        assertTrue("ApiRequestProcessor must be an interface",
                ApiRequestProcessor.class.isInterface());
    }

    @Test
    public void testApiRequestProcessorMethodCount() {
        // Verify interface has exactly one method
        Method[] methods = ApiRequestProcessor.class.getDeclaredMethods();
        assertEquals("ApiRequestProcessor must have exactly 1 method", 1, methods.length);
        assertEquals("Method must be named 'processRequest'",
                "processRequest", methods[0].getName());
    }

    /**
     * Mock implementation for testing
     */
    private static class TestApiRequestProcessor implements ApiRequestProcessor {
        private final JSONObject response;
        private final boolean shouldThrow;

        TestApiRequestProcessor(JSONObject response) {
            this.response = response;
            this.shouldThrow = false;
        }

        TestApiRequestProcessor(boolean shouldThrow) {
            this.response = null;
            this.shouldThrow = shouldThrow;
        }

        @Override
        public JSONObject processRequest(String requestBody) throws ApiProcessingException {
            if (shouldThrow) {
                throw ApiProcessingException.invalidRequest("Test exception");
            }
            return response;
        }
    }

    @Test
    public void testApiRequestProcessorImplementation() throws ApiProcessingException {
        // Test that we can implement the interface and it works as expected
        JSONObject expected = new JSONObject();
        expected.put("status", "success");
        expected.put("message", "Test response");

        ApiRequestProcessor processor = new TestApiRequestProcessor(expected);
        JSONObject result = processor.processRequest("{\"test\": \"data\"}");

        assertNotNull("Result must not be null", result);
        assertEquals("Response must match", "success", result.getString("status"));
        assertEquals("Message must match", "Test response", result.getString("message"));
    }

    @Test
    public void testApiRequestProcessorCanThrowException() {
        // Test that implementing classes can throw ApiProcessingException
        ApiRequestProcessor processor = new TestApiRequestProcessor(true);

        try {
            processor.processRequest("{\"test\": \"data\"}");
            fail("Should have thrown ApiProcessingException");
        } catch (ApiProcessingException e) {
            assertEquals("Exception must have correct message", "Test exception", e.getMessage());
            assertEquals("Exception must have correct status code", 400, e.getStatusCode());
            assertEquals("Exception must have correct error type", "Invalid request", e.getErrorType());
        }
    }

    @Test
    public void testApiRequestProcessorAcceptsStringInput() throws ApiProcessingException {
        // Test various string inputs
        JSONObject response = new JSONObject().put("result", "ok");
        ApiRequestProcessor processor = new TestApiRequestProcessor(response);

        // Test with valid JSON string
        JSONObject result1 = processor.processRequest("{\"key\": \"value\"}");
        assertNotNull("Must handle valid JSON string", result1);

        // Test with empty string (implementation decides how to handle)
        JSONObject result2 = processor.processRequest("");
        assertNotNull("Must handle empty string", result2);

        // Test with null (implementation decides how to handle)
        JSONObject result3 = processor.processRequest(null);
        assertNotNull("Must handle null", result3);
    }
}
