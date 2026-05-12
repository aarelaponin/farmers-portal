package global.govstack.formcreator.constants;

/**
 * Constants used throughout the Form Creator API plugin
 */
public final class ApiConstants {

    // Prevent instantiation
    private ApiConstants() {}

    /**
     * Joget Spring Bean Names
     */
    public static final class BeanNames {
        public static final String APP_SERVICE = "appService";
        public static final String FORM_SERVICE = "formService";
        public static final String FORM_DEFINITION_DAO = "formDefinitionDao";
        public static final String FORM_DATA_DAO = "formDataDao";
        public static final String BUILDER_DEFINITION_DAO = "builderDefinitionDao";
        public static final String DATALIST_DEFINITION_DAO = "datalistDefinitionDao";
        public static final String USERVIEW_DEFINITION_DAO = "userviewDefinitionDao";
        public static final String DATA_SOURCE = "dataSource";
        public static final String WORKFLOW_USER_MANAGER = "workflowUserManager";
    }

    /**
     * API Request Field Names
     */
    public static final class RequestFields {
        // Required fields
        public static final String FORM_ID = "formId";
        public static final String FORM_NAME = "formName";
        public static final String TABLE_NAME = "tableName";
        public static final String FORM_DEFINITION = "formDefinition";

        // Optional fields
        public static final String TARGET_APP_ID = "targetAppId";
        public static final String TARGET_APP_VERSION = "targetAppVersion";
        public static final String CREATE_API_ENDPOINT = "createApiEndpoint";
        public static final String API_NAME = "apiName";
        public static final String CREATE_CRUD = "createCrud";
        public static final String DATALIST_NAME = "datalistName";
        public static final String USERVIEW_NAME = "userviewName";

        // File upload fields (for multipart requests)
        public static final String FORM_DEFINITION_FILE = "formDefinitionFile";
        public static final String FORM_DEFINITION_FILE_NAME = "formDefinitionFileName";
    }

    /**
     * API Response Field Names
     */
    public static final class ResponseFields {
        public static final String STATUS = "status";
        public static final String FORM_ID = "formId";
        public static final String API_ID = "apiId";
        public static final String DATALIST_ID = "datalistId";
        public static final String USERVIEW_ID = "userviewId";
        public static final String MESSAGE = "message";
        public static final String ERROR_TYPE = "errorType";
        public static final String ERROR_MESSAGE = "errorMessage";
        public static final String TIMESTAMP = "timestamp";
    }

    /**
     * Status Values
     */
    public static final class StatusValues {
        public static final String SUCCESS = "success";
        public static final String ERROR = "error";
    }

    /**
     * Error Types
     */
    public static final class ErrorTypes {
        public static final String VALIDATION_ERROR = "Validation Error";
        public static final String INVALID_JSON = "Invalid JSON";
        public static final String APP_NOT_FOUND = "Application Not Found";
        public static final String FORM_CREATION_ERROR = "Form Creation Error";
        public static final String API_CREATION_ERROR = "API Creation Error";
        public static final String CRUD_CREATION_ERROR = "CRUD Creation Error";
        public static final String INTERNAL_SERVER_ERROR = "Internal Server Error";
        public static final String PROCESSING_ERROR = "Processing Error";
    }

    /**
     * File System Path Constants
     */
    public static final class Paths {
        public static final String WFLOW_BASE = "/wflow";
        public static final String APP_SRC = "/wflow/app_src";
        public static final String APP_FORMUPLOADS = "app_formuploads";

        // Builder subdirectories
        public static final String BUILDER_DIR = "/builder";
        public static final String API_DIR = "/api";
        public static final String DATALIST_DIR = "/datalists";
        public static final String USERVIEW_DIR = "/userviews";
        public static final String FORMS_DIR = "/forms";

        // File extensions
        public static final String JSON_EXTENSION = ".json";
    }

    /**
     * Database Table Names
     */
    public static final class TableNames {
        public static final String APP_FORM = "app_form";
        public static final String APP_BUILDER = "app_builder";
        public static final String APP_DATALIST = "app_datalist";
        public static final String APP_USERVIEW = "app_userview";
    }

    /**
     * Form/Builder Property Keys (used in JSON definitions)
     */
    public static final class PropertyKeys {
        public static final String ID = "id";
        public static final String NAME = "name";
        public static final String TABLE_NAME = "tableName";
        public static final String CLASS_NAME = "className";
        public static final String PROPERTIES = "properties";
        public static final String ELEMENTS = "elements";
        public static final String LABEL = "label";
        public static final String TYPE = "type";
        public static final String COLUMNS = "columns";
        public static final String ACTIONS = "actions";
        public static final String CATEGORIES = "categories";
        public static final String MENUS = "menus";
        public static final String BINDER = "binder";
    }

    /**
     * Builder Type Values
     */
    public static final class BuilderTypes {
        public static final String API = "api";
        public static final String FORM = "form";
        public static final String DATALIST = "datalist";
        public static final String USERVIEW = "userview";
    }

    /**
     * Checkbox/Boolean Values (common representations)
     */
    public static final class CheckboxValues {
        public static final String TRUE = "true";
        public static final String ONE = "1";
        public static final String YES = "yes";
        public static final String ON = "on";
        public static final String CHECKED = "checked";
    }

    /**
     * ID Prefixes
     */
    public static final class IdPrefixes {
        public static final String API = "API-";
        public static final String LIST = "list_";
        public static final String USERVIEW = "v";  // Default userview ID
    }

    /**
     * Default Values and Templates
     */
    public static final class Defaults {
        public static final String DEFAULT_USERVIEW_ID = "v";
        public static final String LIST_NAME_PREFIX = "List - ";
        public static final String MANAGEMENT_SUFFIX = " Management";
        public static final String DEFAULT_API_NAME_SUFFIX = " API";
    }

    /**
     * Joget Class Names (fully qualified)
     */
    public static final class JogetClasses {
        public static final String APP_FORM_API = "org.joget.api.lib.AppFormAPI";
        public static final String FORM_BINDER = "org.joget.apps.form.model.FormBinder";
        public static final String FORM_LOAD_BINDER = "org.joget.apps.form.model.FormLoadBinder";
        public static final String FORM_STORE_BINDER = "org.joget.apps.form.model.FormStoreBinder";
        public static final String FORM_ROW_DATA_LIST_BINDER = "org.joget.apps.app.lib.FormRowDataListBinder";
        public static final String DATALIST_ACTION_DEFAULT = "org.joget.apps.datalist.lib.FormRowDataListAction";
        public static final String DATALIST_ACTION_DELETE = "org.joget.apps.datalist.lib.FormRowDeleteDataListAction";
        public static final String TEXT_FIELD = "org.joget.apps.form.lib.TextField";
        public static final String CRUD_MENU = "org.joget.apps.userview.lib.CRUDMenu";
    }

    /**
     * SQL Column Names (for form definition tables)
     */
    public static final class ColumnNames {
        public static final String ID = "id";
        public static final String APP_ID = "appId";
        public static final String APP_VERSION = "appVersion";
        public static final String FORM_ID = "formId";
        public static final String NAME = "name";
        public static final String TABLE_NAME = "tableName";
        public static final String JSON = "json";
        public static final String DATE_CREATED = "dateCreated";
        public static final String DATE_MODIFIED = "dateModified";
    }

    /**
     * HTTP Status Codes
     */
    public static final class HttpStatus {
        public static final int OK = 200;
        public static final int BAD_REQUEST = 400;
        public static final int NOT_FOUND = 404;
        public static final int INTERNAL_SERVER_ERROR = 500;
    }

    /**
     * Validation Messages
     */
    public static final class ValidationMessages {
        public static final String FORM_ID_REQUIRED = "formId is required";
        public static final String FORM_NAME_REQUIRED = "formName is required";
        public static final String TABLE_NAME_REQUIRED = "tableName is required";
        public static final String FORM_DEFINITION_REQUIRED = "formDefinition or formDefinitionFile is required";
        public static final String INVALID_FORM_DEFINITION_JSON = "Form definition JSON is invalid";
        public static final String TARGET_APP_NOT_FOUND = "Target application not found";
    }

    /**
     * System User Constants
     */
    public static final class SystemUser {
        public static final String USERNAME = "admin";
        public static final String CURRENT_USER_PLACEHOLDER = "currentUsername";
    }
}
