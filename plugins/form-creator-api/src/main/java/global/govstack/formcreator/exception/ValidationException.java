package global.govstack.formcreator.exception;

import global.govstack.formcreator.constants.ApiConstants;

/**
 * Exception thrown when request validation fails.
 * Automatically sets status code to 400 (Bad Request).
 */
public class ValidationException extends ApiProcessingException {

    public ValidationException(String message) {
        super(ApiConstants.HttpStatus.BAD_REQUEST,
              ApiConstants.ErrorTypes.VALIDATION_ERROR,
              message);
    }

    public ValidationException(String message, Throwable cause) {
        super(ApiConstants.HttpStatus.BAD_REQUEST,
              ApiConstants.ErrorTypes.VALIDATION_ERROR,
              message,
              cause);
    }
}
