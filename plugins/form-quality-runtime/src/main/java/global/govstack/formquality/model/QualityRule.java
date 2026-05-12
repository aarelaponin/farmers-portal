package global.govstack.formquality.model;

/**
 * In-memory view of one row from the {@code qa_rule} Joget form.
 * Immutable after construction.
 */
public final class QualityRule {

    public enum Severity { ERROR, WARNING, INFO }

    private final String id;
    private final String serviceId;
    private final String tabCode;          // may be null
    private final String ruleCode;
    private final Severity severity;
    private final String affectedFields;   // CSV
    private final String ruleScript;       // SQL today; JRE DSL later
    private final String message;

    public QualityRule(String id, String serviceId, String tabCode, String ruleCode,
                       Severity severity, String affectedFields, String ruleScript,
                       String message) {
        this.id = id;
        this.serviceId = serviceId;
        this.tabCode = tabCode;
        this.ruleCode = ruleCode;
        this.severity = severity;
        this.affectedFields = affectedFields;
        this.ruleScript = ruleScript;
        this.message = message;
    }

    public String   getId()             { return id; }
    public String   getServiceId()      { return serviceId; }
    public String   getTabCode()        { return tabCode; }
    public String   getRuleCode()       { return ruleCode; }
    public Severity getSeverity()       { return severity; }
    public String   getAffectedFields() { return affectedFields; }
    public String   getRuleScript()     { return ruleScript; }
    public String   getMessage()        { return message; }
}
