package global.govstack.processing.api;

import global.govstack.registration.receiver.exception.ApiProcessingException;
import global.govstack.registration.receiver.util.ErrorResponseUtil;
import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Contract tests for ProcessingAPI
 * These tests verify that the API contract remains stable after refactoring
 * to BaseServiceProvider architecture.
 */
public class ProcessingAPIContractTest {

    @Test
    public void testErrorResponseJsonStructure() {
        // Test that error responses have the expected JSON structure
        String errorJson = ErrorResponseUtil.createErrorResponse("TestError", "Test message");
        JSONObject json = new JSONObject(errorJson);

        assertTrue("Error response must have 'error' field", json.has("error"));
        assertTrue("Error response must have 'message' field", json.has("message"));
        assertEquals("Error type must match", "TestError", json.getString("error"));
        assertEquals("Error message must match", "Test message", json.getString("message"));
    }

    @Test
    public void testErrorResponseWithDetails() {
        // Test detailed error responses
        JSONObject details = new JSONObject();
        details.put("field", "testField");
        details.put("value", "testValue");

        String errorJson = ErrorResponseUtil.createDetailedErrorResponse(
                "ValidationError",
                "Field validation failed",
                details
        );

        JSONObject json = new JSONObject(errorJson);
        assertTrue("Detailed error must have 'error' field", json.has("error"));
        assertTrue("Detailed error must have 'message' field", json.has("message"));
        assertTrue("Detailed error must have 'details' field", json.has("details"));

        JSONObject returnedDetails = json.getJSONObject("details");
        assertEquals("testField", returnedDetails.getString("field"));
    }

    @Test
    public void testApiProcessingExceptionContract() {
        // Test that ApiProcessingException has the required properties
        ApiProcessingException exception = new ApiProcessingException(
                "Test message",
                "Test error type",
                400
        );

        assertEquals("Exception message must match", "Test message", exception.getMessage());
        assertEquals("Error type must match", "Test error type", exception.getErrorType());
        assertEquals("Status code must match", 400, exception.getStatusCode());
    }

    @Test
    public void testApiProcessingExceptionFactoryMethods() {
        // Test factory methods produce correct status codes
        ApiProcessingException invalidRequest = ApiProcessingException.invalidRequest("Invalid");
        assertEquals("Invalid request should return 400", 400, invalidRequest.getStatusCode());
        assertEquals("Invalid request", invalidRequest.getErrorType());

        ApiProcessingException validationError = ApiProcessingException.validationError("Validation failed");
        assertEquals("Validation error should return 400", 400, validationError.getStatusCode());
        assertEquals("Validation Error", validationError.getErrorType());

        ApiProcessingException formError = ApiProcessingException.formSubmissionError("Form error");
        assertEquals("Form error should return 400", 400, formError.getStatusCode());

        ApiProcessingException workflowError = ApiProcessingException.workflowError("Workflow error");
        assertEquals("Workflow error should return 500", 500, workflowError.getStatusCode());

        ApiProcessingException configError = ApiProcessingException.configError("Config error");
        assertEquals("Config error should return 500", 500, configError.getStatusCode());

        ApiProcessingException serverError = ApiProcessingException.serverError("Server error");
        assertEquals("Server error should return 500", 500, serverError.getStatusCode());
    }

    @Test
    public void testApiProcessingExceptionWithCause() {
        // Test that exceptions can wrap other exceptions
        Throwable cause = new RuntimeException("Root cause");
        ApiProcessingException exception = new ApiProcessingException(
                "Wrapped exception",
                "WrapperError",
                500,
                cause
        );

        assertEquals("Wrapped exception", exception.getMessage());
        assertNotNull("Exception must have a cause", exception.getCause());
        assertEquals("Root cause", exception.getCause().getMessage());
    }

    @Test
    public void testErrorResponseFormatConsistency() {
        // Verify all error responses follow the same format
        String[] errorTypes = {"InvalidRequest", "ValidationError", "FormError", "WorkflowError"};
        String[] messages = {"Msg1", "Msg2", "Msg3", "Msg4"};

        for (int i = 0; i < errorTypes.length; i++) {
            String errorJson = ErrorResponseUtil.createErrorResponse(errorTypes[i], messages[i]);
            JSONObject json = new JSONObject(errorJson);

            // All must have same structure
            assertEquals("All errors must have 2 fields", 2, json.length());
            assertTrue(json.has("error"));
            assertTrue(json.has("message"));
            assertEquals(errorTypes[i], json.getString("error"));
            assertEquals(messages[i], json.getString("message"));
        }
    }
}
