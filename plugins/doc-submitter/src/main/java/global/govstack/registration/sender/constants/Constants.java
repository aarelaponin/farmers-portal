package global.govstack.registration.sender.constants;

/**
 * Constants used throughout the application
 */
public final class Constants {

    // Prevent instantiation
    private Constants() {}

    // Configuration defaults
    public static final String DEFAULT_CONFIG_FILE = "application-config.json";

    // HTTP Headers
    public static final String HEADER_API_ID = "api_id";  // Changed to match API expectations
    public static final String HEADER_API_KEY = "api_key"; // Changed to match API expectations
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_ACCEPT = "Accept";

    // Content types
    public static final String CONTENT_TYPE_JSON = "application/json";

    // HTTP Methods
    public static final String HTTP_METHOD_POST = "POST";
    public static final String HTTP_METHOD_GET = "GET";

    // Form field types
    public static final String FIELD_TYPE_STRING = "String";
    public static final String FIELD_TYPE_NUMBER = "Number";
    public static final String FIELD_TYPE_DATE = "Date";
    public static final String FIELD_TYPE_BOOLEAN = "Boolean";
    public static final String FIELD_TYPE_FILE = "File";

    // Date format
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT_ISO = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    // Bean names
    public static final String BEAN_FORM_DATA_DAO = "formDataDao";
    public static final String BEAN_FORM_DEFINITION_DAO = "formDefinitionDao";
    public static final String BEAN_WORKFLOW_MANAGER = "workflowManager";
}