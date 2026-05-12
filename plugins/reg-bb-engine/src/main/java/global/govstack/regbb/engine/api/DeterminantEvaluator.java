package global.govstack.regbb.engine.api;

import java.util.List;

/**
 * The single entry point for rule evaluation in the converged engine.
 *
 * <p>Backed by two internal evaluators (fast-path tree-walker over the closed
 * twenty operator subset; SQL-path compile-and-execute via joget-rules-api).
 * Routing between them is implementation-private — by static AST analysis on
 * first evaluation per ADR-001 revision 2 and {@code regbb-solution-architecture-spec.md}
 * §8.1 / §8.6. Callers see one interface, one cache, one audit shape (with
 * an {@code evaluator} discriminator on each {@code reg_bb_eval_audit} row).
 *
 * <p>Five adapter surfaces call into the same instance: form element
 * ({@code MetaScreenElement}), hash variable ({@code #regbb.eval[detId]#}),
 * process tool ({@code RegBbEvaluatorTool}), eval servlet
 * ({@code POST /regbb/eval/screen}), and §8 REST endpoint
 * ({@code POST /api/registration/v1/eval}).
 *
 * <p>Phase 1 Week 1: this interface defines the contract. The implementation
 * (fast-path) lands Week 3; SQL-path bridges to {@code joget-rules-api} Week 4.
 */
public interface DeterminantEvaluator {

    /**
     * Evaluate one named determinant against the current application state.
     *
     * @param determinantId  the {@code mm_determinant.id} to evaluate
     * @param ctx            evaluation context (application data, registry epoch, etc.)
     * @return the boolean outcome plus the action target if the rule fired
     */
    EvalResult evaluate(String determinantId, EvalContext ctx);

    /**
     * Evaluate all determinants on a given screen, returning per-field UI toggles.
     *
     * <p>Used by {@code MetaScreenElement} on initial render and by the
     * Ajax conditional-UI endpoint on each watched-field change.
     *
     * @param screenId  the {@code mm_screen.id} being rendered
     * @param ctx       evaluation context (current data, even partial)
     * @return per-field visibility / required toggles plus a degraded-mode
     *         indicator if any registry-touching evaluation deferred per §6.4
     */
    ScreenEvalResult evaluateScreen(String screenId, EvalContext ctx);

    /**
     * Evaluate all fee determinants for a service, returning total + breakdown.
     *
     * @param serviceId  the {@code mm_service.id} whose fees to compute
     * @param ctx        evaluation context
     * @return total fee amount in the service's currency plus per-fee breakdown
     */
    FeeResult computeFees(String serviceId, EvalContext ctx);

    /**
     * Resolve the effective required-document list for a service given current data.
     *
     * <p>Walks {@code mm_required_doc} for the service, evaluates each row's
     * conditional Determinant, returns the resolved list including each row's
     * acceptedTypes / maxSizeBytes / captureFieldsJson per audit §6.2 #6.
     *
     * @param serviceId  the {@code mm_service.id}
     * @param ctx        evaluation context
     */
    List<RequiredDocResult> resolveRequiredDocs(String serviceId, EvalContext ctx);

    /**
     * Invalidate cached results for an application (called on data writes).
     *
     * @param applicationId  the {@code app_application.id} whose cache to evict
     */
    void invalidate(String applicationId);

    /**
     * Invalidate cached results for a service version (called on republish).
     *
     * @param serviceId  the {@code mm_service.id} whose cache to evict
     */
    void invalidateService(String serviceId);
}
