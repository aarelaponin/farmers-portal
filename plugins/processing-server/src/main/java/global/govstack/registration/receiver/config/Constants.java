package global.govstack.registration.receiver.config;

/**
 * Centralized configuration constants for the registration system
 */
public class Constants {
    // Config file name
    public static final String CONFIG_FILE = "server-config.json";

    // JSON property keys
    public static final String FORM_ID = "formId";
    public static final String PROCESS_DEF_ID = "processDefinitionId";
    public static final String APPLICATION_DATA = "applicationData";
    public static final String FIELDS = "fields";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_VALUE = "value";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_MANDATORY = "mandatory";

    // Role related constants
    public static final String ROLES = "roles";
    public static final String ADMIN_USERNAME = "adminUsername";
    public static final String REGISTRANT_USERNAME = "registrantUsername";
    public static final String CHIEF_REVIEWER_USERNAME = "chiefReviewerUsername";

    // Activity related constants
    public static final String ACTIVITIES = "activities";
    public static final String SUBMIT_ACTIVITY_ID = "submitActivityId";
    public static final String REVIEW_ACTIVITY_ID = "reviewActivityId";

    // Process variables
    public static final String STATUS = "status";
    public static final String STATUS_PENDING = "pending";

    // Response keys
    public static final String APPLICATION_ID = "applicationId";
    public static final String PROCESS_ID = "processId";
    public static final String ERROR = "error";

    // DAta fields
    public static final String ID = "id";
    public static final String FIELD_APPLICATION_ID = "application_id";

}