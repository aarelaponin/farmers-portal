package com.fiscaladmin.gam.appdefinitionprovider.exception;

/**
 * Exception thrown during export operations.
 *
 * Includes HTTP status code and error code for API responses.
 */
public class ExportException extends Exception {

    private final int statusCode;
    private final String errorCode;

    public ExportException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public ExportException(int statusCode, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // ===== Convenience Factory Methods =====

    public static ExportException metadataNotExported(String formName) {
        return new ExportException(
            400,
            "METADATA_NOT_EXPORTED",
            "Metadata forms cannot be exported: " + formName
        );
    }

    public static ExportException formNotFound(String formId) {
        return new ExportException(
            404,
            "FORM_NOT_FOUND",
            "Form not found: " + formId
        );
    }

    public static ExportException appNotFound(String appId) {
        return new ExportException(
            404,
            "APP_NOT_FOUND",
            "Application not found: " + appId
        );
    }

    public static ExportException ioError(String resource, Throwable cause) {
        return new ExportException(
            500,
            "IO_ERROR",
            "Error reading resource: " + resource,
            cause
        );
    }

    public static ExportException jsonParseError(String resource, Throwable cause) {
        return new ExportException(
            500,
            "JSON_PARSE_ERROR",
            "Error parsing JSON for: " + resource,
            cause
        );
    }
}
