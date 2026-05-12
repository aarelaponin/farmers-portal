package global.govstack.regbb.engine.binder;

import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import global.govstack.regbb.engine.dao.MetaModelDao;
import global.govstack.regbb.engine.evaluator.RoutingEvaluator;
import org.joget.apps.form.model.FormRow;
import org.joget.commons.util.LogUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves "if it's policy, it's a rule" mappings for status assignment.
 *
 * <p>Per ADR-027 ({@code initial_status_assignment} scope) and ADR-028
 * ({@code decision_to_status} scope), the mapping from disposition→status
 * (or decision→status) is no longer hardcoded — it lives as
 * {@code mm_determinant} rows in the relevant scope. Each rule has a
 * boolean expression in its {@code ruleJson} and a {@code targetValue}
 * column carrying the status name to assign when the rule evaluates TRUE.
 *
 * <p>Resolution order (first match wins):
 * <ol>
 *   <li>Programme-specific rules (registrationId set on the rule)</li>
 *   <li>Service-wide rules (serviceId set, registrationId blank)</li>
 *   <li>Global rules (both blank)</li>
 * </ol>
 *
 * <p>If no rule matches in any tier, returns {@code null} — the caller
 * falls back to the hardcoded default mapping (defensive; preserves
 * pre-ADR-027 behaviour when seed rules are missing).
 *
 * <p>Per ADR-007, every rule evaluation writes to {@code reg_bb_eval_audit}
 * via the framework's {@code RoutingEvaluator}. Status assignment decisions
 * are forensically traceable.
 */
public final class StatusPolicyResolver {

    private static final String CLASS_NAME = StatusPolicyResolver.class.getName();

    /** Scope for {@link #resolveInitialStatus}. */
    public static final String SCOPE_INITIAL_STATUS = "initial_status_assignment";
    /** Scope for {@link #resolveDecisionStatus}. */
    public static final String SCOPE_DECISION_STATUS = "decision_to_status";

    private StatusPolicyResolver() {}

    /**
     * Resolve the initial application status from an eligibility disposition.
     *
     * @param disposition       the disposition string (e.g. "eligibility_passed")
     * @param serviceCode       optional service code for service-wide rules
     * @param registrationCode  optional programme code for programme-specific rules
     * @return the target status name, or {@code null} if no rule matches.
     */
    public static String resolveInitialStatus(String disposition,
                                              String serviceCode,
                                              String registrationCode) {
        if (disposition == null || disposition.isEmpty()) return null;
        Map<String, Object> data = new HashMap<>();
        data.put("disposition", disposition);
        return resolve(SCOPE_INITIAL_STATUS, serviceCode, registrationCode, data);
    }

    /**
     * Resolve the target application status from an operator decision.
     *
     * @param decisionValue     the operator's decision value (e.g. "approve")
     * @param serviceCode       optional service code for service-wide rules
     * @param registrationCode  optional programme code for programme-specific rules
     * @return the target status name, or {@code null} if no rule matches.
     */
    public static String resolveDecisionStatus(String decisionValue,
                                               String serviceCode,
                                               String registrationCode) {
        if (decisionValue == null || decisionValue.isEmpty()) return null;
        Map<String, Object> data = new HashMap<>();
        data.put("decision_value", decisionValue);
        return resolve(SCOPE_DECISION_STATUS, serviceCode, registrationCode, data);
    }

    /** Common resolution loop: programme → service → global; first TRUE wins. */
    private static String resolve(String scope,
                                  String serviceCode,
                                  String registrationCode,
                                  Map<String, Object> data) {
        try {
            MetaModelDao dao = new MetaModelDao();
            RoutingEvaluator evaluator = new RoutingEvaluator();

            // Tier 1: programme-specific
            if (registrationCode != null && !registrationCode.isEmpty()) {
                String hit = tryRules(dao.listDeterminantsByScope(scope, null, registrationCode),
                                      data, evaluator);
                if (hit != null) return hit;
            }
            // Tier 2: service-wide
            if (serviceCode != null && !serviceCode.isEmpty()) {
                String hit = tryRules(dao.listDeterminantsByScope(scope, serviceCode, null),
                                      data, evaluator);
                if (hit != null) return hit;
            }
            // Tier 3: global
            String hit = tryRules(dao.listDeterminantsByScope(scope, null, null),
                                  data, evaluator);
            return hit;
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "policy rule resolution failed for scope=" + scope
                    + " (falling back to hardcoded default): " + t.getMessage());
            return null;
        }
    }

    /** Iterate rules, return the first one whose evaluation is TRUE; that
     *  rule's {@code targetValue} is the answer. */
    private static String tryRules(List<FormRow> rules,
                                   Map<String, Object> data,
                                   RoutingEvaluator evaluator) {
        if (rules == null || rules.isEmpty()) return null;
        for (FormRow rule : rules) {
            String code = prop(rule, "code");
            if (code == null) continue;
            String target = prop(rule, "targetValue");
            if (target == null || target.isEmpty()) continue; // rule misconfigured

            EvalContext ctx = EvalContext.builder().data(data).build();
            try {
                EvalResult r = evaluator.evaluate(code, ctx);
                if (r != null && r.outcome == EvalResult.Outcome.TRUE) {
                    return target;
                }
            } catch (Throwable t) {
                LogUtil.warn(CLASS_NAME, "rule " + code + " evaluation threw "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
                // Continue with next rule; don't let one bad rule break the chain
            }
        }
        return null;
    }

    private static String prop(FormRow row, String key) {
        if (row == null || key == null) return null;
        Object v = row.get(key);
        if (v == null) v = row.get(key.toLowerCase());
        return v == null ? null : v.toString();
    }
}
