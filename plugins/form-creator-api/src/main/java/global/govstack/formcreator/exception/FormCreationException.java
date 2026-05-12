package global.govstack.formcreator.exception;

import global.govstack.formcreator.constants.ApiConstants;

/**
 * Exception thrown when form creation fails.
 * Automatically sets status code to 500 (Internal Server Error).
 */
public class FormCreationException extends ApiProcessingException {

    public FormCreationException(String message) {
        super(ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
              ApiConstants.ErrorTypes.FORM_CREATION_ERROR,
              message);
    }

    public FormCreationException(String message, Throwable cause) {
        super(ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
              ApiConstants.ErrorTypes.FORM_CREATION_ERROR,
              message,
              cause);
    }
}
