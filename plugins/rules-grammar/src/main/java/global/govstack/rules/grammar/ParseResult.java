package global.govstack.rules.grammar;

import global.govstack.rules.grammar.model.Script;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of parsing a Rules Script.
 * Contains either a successfully parsed Script or a list of errors.
 */
public record ParseResult(
        Script script,
        List<ParseError> errors
) {
    public ParseResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
    }

    /**
     * Creates a successful parse result.
     */
    public static ParseResult success(Script script) {
        return new ParseResult(Objects.requireNonNull(script), List.of());
    }

    /**
     * Creates a failed parse result.
     */
    public static ParseResult failure(List<ParseError> errors) {
        return new ParseResult(null, errors);
    }

    /**
     * Creates a result with both script and errors (partial success).
     */
    public static ParseResult partial(Script script, List<ParseError> errors) {
        return new ParseResult(script, errors);
    }

    /**
     * Returns true if parsing was successful (no errors).
     */
    public boolean isSuccess() {
        return errors.isEmpty() && script != null;
    }

    /**
     * Returns true if there were any parse errors.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Returns the parsed script if successful.
     */
    public Optional<Script> getScript() {
        return Optional.ofNullable(script);
    }

    /**
     * Returns the script or throws if parsing failed.
     */
    public Script getScriptOrThrow() {
        if (script == null || hasErrors()) {
            String errorMsg = errors.isEmpty()
                    ? "Parsing failed"
                    : "Parsing failed with errors:\n" + formatErrors();
            throw new RulesScriptParseException(errorMsg, errors);
        }
        return script;
    }

    /**
     * Returns a formatted string of all errors.
     */
    public String formatErrors() {
        return errors.stream()
                .map(ParseError::toString)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
