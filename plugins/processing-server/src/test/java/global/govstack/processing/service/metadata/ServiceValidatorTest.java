package global.govstack.processing.service.metadata;

import global.govstack.registration.receiver.exception.InvalidRequestException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for ServiceValidator
 */
public class ServiceValidatorTest {

    @Test
    public void testValidServiceId() throws InvalidRequestException {
        ServiceValidator validator = new ServiceValidator("farmers_registry");

        // Should not throw exception
        validator.validateServiceId("farmers_registry");

        // Test with extra spaces
        validator.validateServiceId("  farmers_registry  ");
    }

    @Test(expected = InvalidRequestException.class)
    public void testInvalidServiceId() throws InvalidRequestException {
        ServiceValidator validator = new ServiceValidator("farmers_registry");
        validator.validateServiceId("business_registry");
    }

    @Test(expected = InvalidRequestException.class)
    public void testNullServiceId() throws InvalidRequestException {
        ServiceValidator validator = new ServiceValidator("farmers_registry");
        validator.validateServiceId(null);
    }

    @Test(expected = InvalidRequestException.class)
    public void testEmptyServiceId() throws InvalidRequestException {
        ServiceValidator validator = new ServiceValidator("farmers_registry");
        validator.validateServiceId("");
    }

    @Test(expected = InvalidRequestException.class)
    public void testBlankServiceId() throws InvalidRequestException {
        ServiceValidator validator = new ServiceValidator("farmers_registry");
        validator.validateServiceId("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullServiceId() {
        new ServiceValidator(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithEmptyServiceId() {
        new ServiceValidator("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithBlankServiceId() {
        new ServiceValidator("   ");
    }

    @Test
    public void testIsValidService() {
        ServiceValidator validator = new ServiceValidator("farmers_registry");

        assertTrue(validator.isValidService("farmers_registry"));
        assertTrue(validator.isValidService("  farmers_registry  ")); // With spaces
        assertFalse(validator.isValidService("business_registry"));
        assertFalse(validator.isValidService(null));
        assertFalse(validator.isValidService(""));
    }

    @Test
    public void testGetConfiguredServiceId() {
        ServiceValidator validator = new ServiceValidator("farmers_registry");
        assertEquals("farmers_registry", validator.getConfiguredServiceId());

        // Test that spaces are trimmed in constructor
        ServiceValidator validator2 = new ServiceValidator("  test_service  ");
        assertEquals("test_service", validator2.getConfiguredServiceId());
    }

    @Test
    public void testCaseSensitivity() throws InvalidRequestException {
        ServiceValidator validator = new ServiceValidator("farmers_registry");

        // Service IDs should be case-sensitive
        try {
            validator.validateServiceId("FARMERS_REGISTRY");
            fail("Should throw exception for case mismatch");
        } catch (InvalidRequestException e) {
            assertTrue(e.getMessage().contains("Service ID mismatch"));
        }
    }

    @Test
    public void testValidateServiceId_ErrorMessage() {
        ServiceValidator validator = new ServiceValidator("farmers_registry");

        try {
            validator.validateServiceId("wrong_service");
            fail("Should throw InvalidRequestException");
        } catch (InvalidRequestException e) {
            assertTrue(e.getMessage().contains("farmers_registry"));
            assertTrue(e.getMessage().contains("wrong_service"));
        }
    }
}