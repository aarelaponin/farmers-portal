package global.govstack.rules.grammar;

import java.util.List;

/**
 * Exception thrown when Rules Script parsing fails.
 */
public class RulesScriptParseException extends RuntimeException {

    private final List<ParseError> errors;

    public RulesScriptParseException(String message, List<ParseError> errors) {
        super(message);
        this.errors = errors != null ? List.copyOf(errors) : List.of();
    }

    public RulesScriptParseException(String message) {
        this(message, List.of());
    }

    /**
     * Returns the list of parse errors.
     */
    public List<ParseError> getErrors() {
        return errors;
    }

    /**
     * Returns true if there are specific parse errors.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
