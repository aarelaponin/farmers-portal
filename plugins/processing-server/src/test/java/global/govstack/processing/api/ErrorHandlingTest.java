package global.govstack.processing.api;

import global.govstack.registration.receiver.exception.*;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for error handling and exception to HTTP status code mapping
 * Verifies that all exception types map to correct HTTP status codes
 * and error responses follow the expected format
 */
public class ErrorHandlingTest {

    @Test
    public void testInvalidRequestExceptionMapsTo400() {
        ApiProcessingException exception = ApiProcessingException.invalidRequest("Invalid JSON format");

        assertEquals("Invalid request must map to 400", 400, exception.getStatusCode());
        assertEquals("Error type must be 'Invalid request'", "Invalid request", exception.getErrorType());
        assertEquals("Message must be preserved", "Invalid JSON format", exception.getMessage());
    }

    @Test
    public void testValidationExceptionMapsTo400() {
        ApiProcessingException exception = ApiProcessingException.validationError("Field validation failed");

        assertEquals("Validation error must map to 400", 400, exception.getStatusCode());
        assertEquals("Error type must be 'Validation Error'", "Validation Error", exception.getErrorType());
        assertEquals("Message must be preserved", "Field validation failed", exception.getMessage());
    }

    @Test
    public void testFormSubmissionExceptionMapsTo400() {
        ApiProcessingException exception = ApiProcessingException.formSubmissionError("Form submission failed");

        assertEquals("Form submission error must map to 400", 400, exception.getStatusCode());
        assertEquals("Error type must be 'Form submission error'", "Form submission error", exception.getErrorType());
        assertEquals("Message must be preserved", "Form submission failed", exception.getMessage());
    }

    @Test
    public void testWorkflowExceptionMapsTo500() {
        ApiProcessingException exception = ApiProcessingException.workflowError("Workflow process failed");

        assertEquals("Workflow error must map to 500", 500, exception.getStatusCode());
        assertEquals("Error type must be 'Workflow processing error'",
                "Workflow processing error", exception.getErrorType());
        assertEquals("Message must be preserved", "Workflow process failed", exception.getMessage());
    }

    @Test
    public void testConfigurationExceptionMapsTo500() {
        ApiProcessingException exception = ApiProcessingException.configError("Configuration file not found");

        assertEquals("Config error must map to 500", 500, exception.getStatusCode());
        assertEquals("Error type must be 'Configuration error'", "Configuration error", exception.getErrorType());
        assertEquals("Message must be preserved", "Configuration file not found", exception.getMessage());
    }

    @Test
    public void testServerExceptionMapsTo500() {
        ApiProcessingException exception = ApiProcessingException.serverError("Internal server error occurred");

        assertEquals("Server error must map to 500", 500, exception.getStatusCode());
        assertEquals("Error type must be 'Internal server error'",
                "Internal server error", exception.getErrorType());
        assertEquals("Message must be preserved", "Internal server error occurred", exception.getMessage());
    }

    @Test
    public void testExceptionStatusCodeRanges() {
        // Test that client errors are in 400 range and server errors in 500 range
        ApiProcessingException invalidRequest = ApiProcessingException.invalidRequest("test");
        ApiProcessingException validation = ApiProcessingException.validationError("test");
        ApiProcessingException formError = ApiProcessingException.formSubmissionError("test");

        assertTrue("Invalid request must be client error (4xx)",
                invalidRequest.getStatusCode() >= 400 && invalidRequest.getStatusCode() < 500);
        assertTrue("Validation error must be client error (4xx)",
                validation.getStatusCode() >= 400 && validation.getStatusCode() < 500);
        assertTrue("Form error must be client error (4xx)",
                formError.getStatusCode() >= 400 && formError.getStatusCode() < 500);

        ApiProcessingException workflow = ApiProcessingException.workflowError("test");
        ApiProcessingException config = ApiProcessingException.configError("test");
        ApiProcessingException server = ApiProcessingException.serverError("test");

        assertTrue("Workflow error must be server error (5xx)",
                workflow.getStatusCode() >= 500 && workflow.getStatusCode() < 600);
        assertTrue("Config error must be server error (5xx)",
                config.getStatusCode() >= 500 && config.getStatusCode() < 600);
        assertTrue("Server error must be server error (5xx)",
                server.getStatusCode() >= 500 && server.getStatusCode() < 600);
    }

    @Test
    public void testExceptionChaining() {
        // Test that exceptions can wrap other exceptions
        Exception rootCause = new RuntimeException("Root cause message");

        ApiProcessingException wrapped = new ApiProcessingException(
                "Wrapped message",
                "WrapperError",
                500,
                rootCause
        );

        assertNotNull("Exception must have a cause", wrapped.getCause());
        assertEquals("Cause must be preserved", rootCause, wrapped.getCause());
        assertEquals("Root cause message must be accessible",
                "Root cause message", wrapped.getCause().getMessage());
    }

    @Test
    public void testErrorResponseCreationFromException() {
        // Verify that exceptions can be converted to proper error responses
        ApiProcessingException exception = ApiProcessingException.validationError("Invalid field value");

        // Simulate what the API would do
        String errorJson = createErrorResponseFromException(exception);
        JSONObject json = new JSONObject(errorJson);

        assertTrue("Error response must have 'error' field", json.has("error"));
        assertTrue("Error response must have 'message' field", json.has("message"));
        assertEquals("Error type must match exception",
                exception.getErrorType(), json.getString("error"));
        assertEquals("Message must match exception",
                exception.getMessage(), json.getString("message"));
    }

    @Test
    public void testMultipleExceptionInstancesIndependent() {
        // Test that multiple exception instances don't affect each other
        ApiProcessingException ex1 = ApiProcessingException.invalidRequest("Error 1");
        ApiProcessingException ex2 = ApiProcessingException.validationError("Error 2");
        ApiProcessingException ex3 = ApiProcessingException.serverError("Error 3");

        assertEquals("Error 1", ex1.getMessage());
        assertEquals("Error 2", ex2.getMessage());
        assertEquals("Error 3", ex3.getMessage());

        assertEquals(400, ex1.getStatusCode());
        assertEquals(400, ex2.getStatusCode());
        assertEquals(500, ex3.getStatusCode());
    }

    @Test
    public void testExceptionMessagePreservation() {
        // Test that error messages are not modified or truncated
        String longMessage = "This is a very long error message that contains " +
                "detailed information about what went wrong including " +
                "field names, values, and context information that should " +
                "be preserved exactly as provided";

        ApiProcessingException exception = ApiProcessingException.invalidRequest(longMessage);

        assertEquals("Long messages must be preserved exactly",
                longMessage, exception.getMessage());
    }

    /**
     * Helper method to simulate error response creation
     * (This simulates what ErrorResponseUtil.createErrorResponse does)
     */
    private String createErrorResponseFromException(ApiProcessingException exception) {
        JSONObject error = new JSONObject();
        error.put("error", exception.getErrorType());
        error.put("message", exception.getMessage());
        return error.toString();
    }
}
