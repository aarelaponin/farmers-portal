package global.govstack.application.model;

/**
 * One row from {@code sp_elig_criterion} — a single eligibility criterion
 * defined on the programme's Eligibility tab.
 */
public final class EligibilityCriterion {
    public final String id;
    public final String fieldName;
    public final String operatorCode;
    public final String criterionValue;
    public final String criterionValueTo;
    public final String ruleType;          // e.g. INCLUSION / EXCLUSION
    public final String fieldCategory;     // e.g. HOUSEHOLD / FARMER
    public final String failMessage;
    public final String mandatory;         // Y / N
    public final String score;
    public final String order;
    public final String notes;

    public EligibilityCriterion(String id, String fieldName, String operatorCode,
                                String criterionValue, String criterionValueTo,
                                String ruleType, String fieldCategory,
                                String failMessage, String mandatory,
                                String score, String order, String notes) {
        this.id = id;
        this.fieldName = fieldName;
        this.operatorCode = operatorCode;
        this.criterionValue = criterionValue;
        this.criterionValueTo = criterionValueTo;
        this.ruleType = ruleType;
        this.fieldCategory = fieldCategory;
        this.failMessage = failMessage;
        this.mandatory = mandatory;
        this.score = score;
        this.order = order;
        this.notes = notes;
    }

    /**
     * Human-readable label for the application-time chip / row label.
     * Falls back through fieldName, failMessage, then a placeholder.
     */
    public String displayLabel() {
        if (failMessage != null && !failMessage.isEmpty()) return failMessage;
        if (fieldName   != null && !fieldName.isEmpty())   return fieldName;
        return "Criterion " + id;
    }
}
