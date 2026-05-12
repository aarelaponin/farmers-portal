package global.govstack.rules.grammar.model;

/**
 * Enumeration of comparison operators in Rules Script.
 */
public enum ComparisonOperator {
    /** Equals (=) */
    EQ("="),

    /** Not equals (!=) */
    NEQ("!="),

    /** Greater than (>) */
    GT(">"),

    /** Greater than or equal (>=) */
    GTE(">="),

    /** Less than (<) */
    LT("<"),

    /** Less than or equal (<=) */
    LTE("<="),

    /** String contains */
    CONTAINS("CONTAINS"),

    /** String starts with */
    STARTS_WITH("STARTS WITH"),

    /** String ends with */
    ENDS_WITH("ENDS WITH");

    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return symbol;
    }
}
