package global.govstack.regbb.engine.api;

/**
 * Resolves a single field of a {@code $registry.<capability>.<field>} reference
 * for a given applicant. Per RegBB spec §3.2 capability registry pattern.
 *
 * <p><b>Why an interface and not a hard-coded table mapping.</b> The earlier
 * {@code SqlPathEvaluator.entityToTable} switch statement worked for one
 * capability (farmer-by-NID). It didn't extend to:
 * <ul>
 *   <li>1:N aggregates (parcels per farmer summed to a single value)</li>
 *   <li>Capabilities backed by a query other than {@code SELECT c_<field> FROM <t> WHERE c_national_id = ?}</li>
 *   <li>Cross-cutting capabilities authored by future modules (IM, reporting) without touching the evaluator</li>
 * </ul>
 * The registry pattern lets each capability declare its own query while sharing
 * the rule-grammar plumbing.
 *
 * <p><b>Lifecycle.</b> Adapters are stateless or thread-safe. The
 * {@code SqlPathEvaluator} resolves them once at evaluation time via
 * {@link #getCapabilityName()}; concurrent evaluations may share an adapter
 * instance.
 *
 * <p><b>Field semantics.</b> The {@code field} argument is the second
 * segment of the reference (e.g. {@code first_name} for
 * {@code $registry.farmer.first_name}). What constitutes a valid field is
 * adapter-specific: a 1:1 adapter typically maps to a column on its source
 * table; an aggregate adapter exposes virtual fields like
 * {@code cultivated_total} or {@code parcel_count}.
 *
 * <p><b>Null contract.</b> Returning {@code null} means "the registry has no
 * row for this applicant" or "this field is null on the row." The
 * {@code SqlPathEvaluator} substitutes the bareword {@code null} into the
 * rule source; the fast-path evaluator's null-propagation per spec §4.3.0.1
 * turns the comparison into NULL → {@code disposition=indeterminate}.
 *
 * <p><b>Errors.</b> Throw with a descriptive message for unrecoverable
 * problems (unknown field for this capability, JDBC failure, etc.). The
 * evaluator catches and reports as {@code evaluator_error:<message>}; the
 * audit row records the cause.
 *
 * @see global.govstack.regbb.engine.evaluator.SqlPathEvaluator
 */
public interface CapabilityAdapter {

    /**
     * The reference token for this capability — the second segment of
     * {@code $registry.<capability>.<field>}. Should be lowercase
     * snake_case to match the rule-grammar style.
     *
     * <p>Examples: {@code "farmer"}, {@code "parcels_summary"},
     * {@code "households_vulnerability"}.
     */
    String getCapabilityName();

    /**
     * Resolve the given field for the applicant identified by national_id.
     *
     * @param field        the third segment of the reference (e.g. {@code first_name},
     *                     {@code cultivated_total}). Adapter-specific.
     * @param nationalId   the applicant's national_id. Never null but may be empty —
     *                     adapter should treat empty as "no applicant" (return null).
     * @param ctx          the eval context (provides applicant data + service ids
     *                     when needed; most adapters can ignore).
     * @return resolved value as a string, or {@code null} for "no row" / "field null".
     * @throws Exception for unrecoverable failures (unknown field, JDBC error, etc.)
     */
    String resolve(String field, String nationalId, EvalContext ctx) throws Exception;
}
