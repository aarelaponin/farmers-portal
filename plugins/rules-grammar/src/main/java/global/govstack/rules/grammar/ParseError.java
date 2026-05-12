package global.govstack.rules.grammar;

/**
 * Represents a syntax error encountered during parsing.
 */
public record ParseError(
        int line,
        int column,
        String message,
        String offendingSymbol
) {
    @Override
    public String toString() {
        return String.format("Line %d:%d - %s", line, column, message);
    }
}
