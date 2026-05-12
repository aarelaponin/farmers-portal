package global.govstack.regbb.engine.api;

/**
 * Outcome of evaluating a single {@code mm_determinant}.
 *
 * <p>For Boolean determinants, {@link #outcome} is {@link Outcome#TRUE},
 * {@link Outcome#FALSE}, {@link Outcome#NULL} (per the closed-grammar
 * null-propagation rule), or {@link Outcome#ERROR} (registry unavailable
 * or compilation failure — the engine never substitutes false for "I don't
 * know" per spec P5 / §8.6.3).
 *
 * <p>For fee-formula determinants, {@link #numericValue} carries the result.
 *
 * <p>For Determinants with structured action targets (e.g. scope=eligibility
 * with action.applicable), {@link #actionTarget} carries the resolved target
 * id (e.g. the {@code mm_registration.id} that this Determinant applies to).
 *
 * <p>{@link #evaluator} indicates which internal evaluator produced the result
 * — {@code "fast"} or {@code "sql"} — used by the audit row writer.
 */
public final class EvalResult {

    public enum Outcome { TRUE, FALSE, NULL, ERROR }

    public final Outcome outcome;
    public final Double numericValue;       // null for Boolean determinants
    public final String actionTarget;       // null when the rule's action has no target
    public final String evaluator;          // "fast" | "sql"
    public final String errorCause;         // populated when outcome=ERROR

    public EvalResult(Outcome outcome,
                      Double numericValue,
                      String actionTarget,
                      String evaluator,
                      String errorCause) {
        this.outcome = outcome;
        this.numericValue = numericValue;
        this.actionTarget = actionTarget;
        this.evaluator = evaluator;
        this.errorCause = errorCause;
    }

    public static EvalResult bool(boolean v, String evaluator) {
        return new EvalResult(v ? Outcome.TRUE : Outcome.FALSE, null, null, evaluator, null);
    }

    public static EvalResult nullResult(String evaluator) {
        return new EvalResult(Outcome.NULL, null, null, evaluator, null);
    }

    public static EvalResult error(String cause, String evaluator) {
        return new EvalResult(Outcome.ERROR, null, null, evaluator, cause);
    }

    public static EvalResult numeric(double v, String evaluator) {
        return new EvalResult(Outcome.TRUE, v, null, evaluator, null);
    }
}
