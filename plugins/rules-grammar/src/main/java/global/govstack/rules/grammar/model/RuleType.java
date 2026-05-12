package global.govstack.rules.grammar.model;

/**
 * Enumeration of rule types in Rules Script.
 */
public enum RuleType {
    /**
     * Inclusion rule - includes applicant if condition is met.
     */
    INCLUSION,

    /**
     * Exclusion rule - excludes applicant if condition is met.
     */
    EXCLUSION,

    /**
     * Priority rule - assigns priority based on condition.
     */
    PRIORITY,

    /**
     * Bonus rule - adds bonus points if condition is met.
     */
    BONUS
}
