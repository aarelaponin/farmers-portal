package global.govstack.formquality.model;

/**
 * In-memory view of one row from the {@code qa_issue} Joget form.
 */
public final class QualityIssue {

    private final String serviceId;
    private final String formId;
    private final String recordId;
    private final String ruleCode;
    private final String tabCode;
    private final QualityRule.Severity severity;
    private final String affectedFields;
    private final String message;

    public QualityIssue(String serviceId, String formId, String recordId,
                        String ruleCode, String tabCode,
                        QualityRule.Severity severity,
                        String affectedFields, String message) {
        this.serviceId = serviceId;
        this.formId = formId;
        this.recordId = recordId;
        this.ruleCode = ruleCode;
        this.tabCode = tabCode;
        this.severity = severity;
        this.affectedFields = affectedFields;
        this.message = message;
    }

    public String getServiceId()      { return serviceId; }
    public String getFormId()         { return formId; }
    public String getRecordId()       { return recordId; }
    public String getRuleCode()       { return ruleCode; }
    public String getTabCode()        { return tabCode; }
    public QualityRule.Severity getSeverity() { return severity; }
    public String getAffectedFields() { return affectedFields; }
    public String getMessage()        { return message; }
}
