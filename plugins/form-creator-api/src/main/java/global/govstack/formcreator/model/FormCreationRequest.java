package global.govstack.formcreator.model;

/**
 * Model class representing a form creation request.
 * Contains all parameters needed to create a form, API endpoint, and CRUD interface.
 */
public class FormCreationRequest {

    // Required fields
    private String formId;
    private String formName;
    private String tableName;
    private String formDefinitionJson;  // JSON content as string

    // Optional fields for target application
    private String targetAppId;
    private String targetAppVersion;

    // Optional fields for API endpoint creation
    private boolean createApiEndpoint;
    private String apiName;

    // Optional fields for CRUD creation
    private boolean createCrud;
    private String datalistName;
    private String userviewName;

    // File upload support (alternative to JSON string)
    private byte[] formDefinitionFile;
    private String formDefinitionFileName;

    // Constructors
    public FormCreationRequest() {
    }

    // Required fields getters and setters
    public String getFormId() {
        return formId;
    }

    public void setFormId(String formId) {
        this.formId = formId;
    }

    public String getFormName() {
        return formName;
    }

    public void setFormName(String formName) {
        this.formName = formName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getFormDefinitionJson() {
        return formDefinitionJson;
    }

    public void setFormDefinitionJson(String formDefinitionJson) {
        this.formDefinitionJson = formDefinitionJson;
    }

    // Target application getters and setters
    public String getTargetAppId() {
        return targetAppId;
    }

    public void setTargetAppId(String targetAppId) {
        this.targetAppId = targetAppId;
    }

    public String getTargetAppVersion() {
        return targetAppVersion;
    }

    public void setTargetAppVersion(String targetAppVersion) {
        this.targetAppVersion = targetAppVersion;
    }

    // API endpoint getters and setters
    public boolean isCreateApiEndpoint() {
        return createApiEndpoint;
    }

    public void setCreateApiEndpoint(boolean createApiEndpoint) {
        this.createApiEndpoint = createApiEndpoint;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    // CRUD getters and setters
    public boolean isCreateCrud() {
        return createCrud;
    }

    public void setCreateCrud(boolean createCrud) {
        this.createCrud = createCrud;
    }

    public String getDatalistName() {
        return datalistName;
    }

    public void setDatalistName(String datalistName) {
        this.datalistName = datalistName;
    }

    public String getUserviewName() {
        return userviewName;
    }

    public void setUserviewName(String userviewName) {
        this.userviewName = userviewName;
    }

    // File upload getters and setters
    public byte[] getFormDefinitionFile() {
        return formDefinitionFile;
    }

    public void setFormDefinitionFile(byte[] formDefinitionFile) {
        this.formDefinitionFile = formDefinitionFile;
    }

    public String getFormDefinitionFileName() {
        return formDefinitionFileName;
    }

    public void setFormDefinitionFileName(String formDefinitionFileName) {
        this.formDefinitionFileName = formDefinitionFileName;
    }

    @Override
    public String toString() {
        return "FormCreationRequest{" +
                "formId='" + formId + '\'' +
                ", formName='" + formName + '\'' +
                ", tableName='" + tableName + '\'' +
                ", targetAppId='" + targetAppId + '\'' +
                ", targetAppVersion='" + targetAppVersion + '\'' +
                ", createApiEndpoint=" + createApiEndpoint +
                ", apiName='" + apiName + '\'' +
                ", createCrud=" + createCrud +
                ", datalistName='" + datalistName + '\'' +
                ", userviewName='" + userviewName + '\'' +
                ", formDefinitionFileName='" + formDefinitionFileName + '\'' +
                ", hasFormDefinitionJson=" + (formDefinitionJson != null && !formDefinitionJson.isEmpty()) +
                ", hasFormDefinitionFile=" + (formDefinitionFile != null && formDefinitionFile.length > 0) +
                '}';
    }
}
