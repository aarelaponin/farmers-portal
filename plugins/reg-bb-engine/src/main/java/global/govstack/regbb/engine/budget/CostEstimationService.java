package global.govstack.regbb.engine.budget;

import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import global.govstack.regbb.engine.dao.MetaModelDao;
import global.govstack.regbb.engine.evaluator.RoutingEvaluator;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * L3-1 1C — Cost Estimation Service.
 *
 * <p>Given a programme code and an admin-supplied expected applicant
 * count, computes:
 *
 * <ul>
 *   <li><b>estimated_cost</b> — expectedApplicantCount × per-applicant amount
 *       (per-applicant amount = mm_determinant.targetValue of
 *       BENEFIT_AMOUNT_&lt;programme&gt;, same flat-amount strategy as 1B-i).</li>
 *   <li><b>coverage_ratio_pct</b> — 100 × estimated_cost / envelope_allocated.
 *       The envelope's share that this programme would consume if all
 *       expected applicants are approved.</li>
 *   <li><b>source_breakdown</b> — estimated_cost prorated across the
 *       envelope's funding-source contributions, using BudgetEngine's
 *       same banker's-rounding-largest-residual proration. Donors and
 *       Treasury get their own slice.</li>
 *   <li><b>programme_launch_gate</b> — evaluates each
 *       mm_determinant.scope=programme_launch_gate rule for this
 *       programme against a context containing the metrics above.
 *       Default policy: {@code coverage_ratio_pct &lt;= 105}
 *       (allow 5% headroom over the envelope). All rules must pass for
 *       the launch gate to be GREEN; the first FALSE returns its rule's
 *       failMessage.</li>
 * </ul>
 *
 * <p><b>Why mode-1 (admin-supplied count) is the production path for
 * Phase 1.</b> Programme rules reference application-form fields
 * (agro_zone, area_hectares, drought_affected_decl, etc.) that the
 * registry doesn't carry. Counting "eligible farmers in the registry"
 * for these rules requires either a synthetic applicant projection or
 * mode-2 (rules-to-SQL against the application table), which is L3-1
 * 3b. Until 3b lands, admin estimates from prior-cycle data are the
 * authoritative input.
 *
 * <p><b>Idempotent + read-only.</b> CES never writes — purely a
 * derivation over current envelope state + programme rules. Safe to
 * call from operator UX as often as needed.
 */
public final class CostEstimationService {

    private static final String CLASS_NAME = CostEstimationService.class.getName();

    private final FormDataDao dao;
    private final RoutingEvaluator evaluator;
    private final MetaModelDao mmDao;

    public CostEstimationService() {
        this.dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        this.evaluator = new RoutingEvaluator();
        this.mmDao = new MetaModelDao();
    }

    public CostEstimationService(FormDataDao dao, RoutingEvaluator evaluator, MetaModelDao mmDao) {
        this.dao = dao;
        this.evaluator = evaluator;
        this.mmDao = mmDao;
    }

    /**
     * The estimate result. All fields are nullable in error paths;
     * {@code errorCause} is non-null on failure.
     */
    public static final class EstimateResult {
        public final String programmeCode;
        public final String envelopeCode;
        public final BigDecimal envelopeAllocated;
        public final String currency;
        public final int expectedApplicantCount;
        public final BigDecimal perApplicantAmount;
        public final BigDecimal estimatedCost;
        public final BigDecimal coverageRatioPct;       // 0-100+ percent
        public final Map<String, BigDecimal> sourceBreakdown;
        public final List<LaunchGateRuleResult> launchGateRules;
        public final boolean launchGateGreen;
        public final String launchGateFailMessage;      // first FALSE rule's message
        public final String errorCause;
        public final long elapsedMs;

        EstimateResult(String programmeCode, String envelopeCode, BigDecimal envelopeAllocated,
                       String currency, int expectedApplicantCount,
                       BigDecimal perApplicantAmount, BigDecimal estimatedCost,
                       BigDecimal coverageRatioPct, Map<String, BigDecimal> sourceBreakdown,
                       List<LaunchGateRuleResult> launchGateRules, boolean launchGateGreen,
                       String launchGateFailMessage, String errorCause, long elapsedMs) {
            this.programmeCode = programmeCode;
            this.envelopeCode = envelopeCode;
            this.envelopeAllocated = envelopeAllocated;
            this.currency = currency;
            this.expectedApplicantCount = expectedApplicantCount;
            this.perApplicantAmount = perApplicantAmount;
            this.estimatedCost = estimatedCost;
            this.coverageRatioPct = coverageRatioPct;
            this.sourceBreakdown = sourceBreakdown == null
                    ? Collections.emptyMap() : sourceBreakdown;
            this.launchGateRules = launchGateRules == null
                    ? Collections.emptyList() : launchGateRules;
            this.launchGateGreen = launchGateGreen;
            this.launchGateFailMessage = launchGateFailMessage;
            this.errorCause = errorCause;
            this.elapsedMs = elapsedMs;
        }

        static EstimateResult error(String programmeCode, String cause, long startedAt) {
            return new EstimateResult(programmeCode, null, null, null, 0, null, null, null,
                    null, null, false, null, cause, System.currentTimeMillis() - startedAt);
        }
    }

    public static final class LaunchGateRuleResult {
        public final String ruleCode;
        public final String outcome;       // TRUE | FALSE | NULL | ERROR
        public final String failMessage;
        public final String errorCause;

        LaunchGateRuleResult(String ruleCode, String outcome, String failMessage, String errorCause) {
            this.ruleCode = ruleCode;
            this.outcome = outcome;
            this.failMessage = failMessage;
            this.errorCause = errorCause;
        }
    }

    public EstimateResult estimate(String programmeCode, int expectedApplicantCount) {
        long startedAt = System.currentTimeMillis();
        try {
            if (programmeCode == null || programmeCode.isEmpty()) {
                return EstimateResult.error(null, "programmeCode_required", startedAt);
            }
            if (expectedApplicantCount < 0) {
                return EstimateResult.error(programmeCode, "expectedApplicantCount_negative", startedAt);
            }

            // 1. Resolve envelope code by the same naming convention the
            //    BudgetEngine uses (BudgetEngine.fireForLifecycle).
            String envelopeCode = resolveEnvelopeForProgramme(programmeCode);

            // 2. Read envelope + source contributions.
            FormRow envelope = findEnvelopeByCode(envelopeCode);
            if (envelope == null) {
                return EstimateResult.error(programmeCode,
                        "envelope_not_found:" + envelopeCode, startedAt);
            }
            BigDecimal allocated = parseDecimal(prop(envelope, "allocated_amount"));
            if (allocated == null || allocated.signum() <= 0) {
                return EstimateResult.error(programmeCode,
                        "envelope_allocated_invalid:" + envelopeCode, startedAt);
            }
            String currency = nz(prop(envelope, "currency"), "LSL");

            List<FormRow> sources = listSourcesForEnvelope(envelopeCode);
            if (sources.isEmpty()) {
                return EstimateResult.error(programmeCode,
                        "envelope_has_no_sources:" + envelopeCode, startedAt);
            }

            // 3. Resolve per-applicant amount from BENEFIT_AMOUNT_<programme>.
            String amountRuleCode = "BENEFIT_AMOUNT_" + programmeCode;
            FormRow amountRule = mmDao.findDeterminantByCode(amountRuleCode);
            if (amountRule == null) {
                return EstimateResult.error(programmeCode,
                        "amount_rule_not_found:" + amountRuleCode, startedAt);
            }
            BigDecimal perApplicantAmount = parseDecimal(prop(amountRule, "targetValue"));
            if (perApplicantAmount == null || perApplicantAmount.signum() <= 0) {
                return EstimateResult.error(programmeCode,
                        "amount_rule_target_value_invalid:" + amountRuleCode, startedAt);
            }
            perApplicantAmount = perApplicantAmount.setScale(2, RoundingMode.HALF_EVEN);

            // 4. Compute estimated cost + coverage ratio.
            BigDecimal estimatedCost = perApplicantAmount
                    .multiply(BigDecimal.valueOf(expectedApplicantCount))
                    .setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal coverageRatioPct = estimatedCost
                    .multiply(BigDecimal.valueOf(100))
                    .divide(allocated, 2, RoundingMode.HALF_EVEN);

            // 5. Source proration — uses the same BudgetEngine logic so
            //    CES estimates align with what the engine will actually
            //    post when applications start coming in.
            Map<String, BigDecimal> breakdown = BudgetEngine.prorateAcrossSources(
                    sources, estimatedCost);

            // 6. Evaluate programme_launch_gate rules.
            List<FormRow> gateRules = listLaunchGateRules(programmeCode);
            List<LaunchGateRuleResult> gateResults = new ArrayList<>(gateRules.size());
            boolean gateGreen = true;
            String firstFailMessage = null;

            // Prepare context the rules can read. Numeric values come in as
            // primitives so the closed-twenty grammar's comparison ops work.
            Map<String, Object> gateData = new HashMap<>();
            gateData.put("programme_code",         programmeCode);
            gateData.put("envelope_code",          envelopeCode);
            gateData.put("envelope_allocated",     allocated.toPlainString());
            gateData.put("expected_applicant_count", String.valueOf(expectedApplicantCount));
            gateData.put("per_applicant_amount",   perApplicantAmount.toPlainString());
            gateData.put("estimated_cost",         estimatedCost.toPlainString());
            gateData.put("coverage_ratio_pct",     coverageRatioPct.toPlainString());

            EvalContext gateCtx = EvalContext.builder().data(gateData).build();
            for (FormRow rule : gateRules) {
                String code = nz(prop(rule, "code"), "");
                EvalResult er = null;
                try {
                    er = evaluator.evaluate(code, gateCtx);
                } catch (Throwable t) {
                    LogUtil.error(CLASS_NAME, t, "launch-gate eval failed: " + code);
                }
                String outcome = (er == null || er.outcome == null)
                        ? "NULL" : er.outcome.name();
                String failMsg = nz(prop(rule, "failMessage"), null);
                String errCause = (er != null) ? er.errorCause : "evaluator_threw";
                gateResults.add(new LaunchGateRuleResult(code, outcome, failMsg, errCause));
                if (er == null || er.outcome != EvalResult.Outcome.TRUE) {
                    if (gateGreen && er != null && er.outcome == EvalResult.Outcome.FALSE) {
                        firstFailMessage = failMsg != null
                                ? failMsg
                                : "Programme launch gate rule " + code + " returned FALSE.";
                    } else if (gateGreen) {
                        firstFailMessage = "Programme launch gate rule " + code
                                + " did not pass (outcome=" + outcome + ").";
                    }
                    gateGreen = false;
                }
            }

            return new EstimateResult(programmeCode, envelopeCode, allocated, currency,
                    expectedApplicantCount, perApplicantAmount, estimatedCost,
                    coverageRatioPct, breakdown, gateResults, gateGreen, firstFailMessage,
                    null, System.currentTimeMillis() - startedAt);

        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "estimate failed for programme=" + programmeCode);
            return EstimateResult.error(programmeCode,
                    "internal:" + t.getClass().getSimpleName() + ":"
                  + (t.getMessage() == null ? "" : t.getMessage()), startedAt);
        }
    }

    // ---- DAO helpers (read-only; mirror the BudgetEngine's read paths) ----

    private FormRow findEnvelopeByCode(String code) {
        FormRowSet rs = dao.find("budget_envelope", "budget_envelope",
                "WHERE e.customProperties.code = ?", new Object[] { code },
                null, false, null, null);
        return (rs == null || rs.isEmpty()) ? null : rs.get(0);
    }

    private List<FormRow> listSourcesForEnvelope(String envelopeCode) {
        FormRowSet rs = dao.find("budget_envelope_source", "budget_envelope_source",
                "WHERE e.customProperties.envelope_code = ?",
                new Object[] { envelopeCode }, "code", false, null, null);
        return (rs == null || rs.isEmpty()) ? new ArrayList<>() : new ArrayList<>(rs);
    }

    private List<FormRow> listLaunchGateRules(String programmeCode) {
        // Two tiers, in order of priority: programme-specific rules first,
        // then service-wide / global. Same idiom as the
        // initial_status_assignment / decision_to_status policy resolution.
        List<FormRow> rules = new ArrayList<>();
        FormRowSet specific = dao.find("mm_determinant", "mm_determinant",
                "WHERE e.customProperties.scope = ? AND e.customProperties.registrationId = ?",
                new Object[] { "programme_launch_gate", programmeCode },
                "code", false, null, null);
        if (specific != null) rules.addAll(specific);
        FormRowSet global = dao.find("mm_determinant", "mm_determinant",
                "WHERE e.customProperties.scope = ? AND (e.customProperties.registrationId IS NULL OR e.customProperties.registrationId = '')",
                new Object[] { "programme_launch_gate" },
                "code", false, null, null);
        if (global != null) rules.addAll(global);
        return rules;
    }

    // ---- Small helpers (kept private to avoid sprawl) ----

    private static String resolveEnvelopeForProgramme(String programmeCode) {
        if (programmeCode == null || programmeCode.isEmpty()) return null;
        String[] parts = programmeCode.split("_");
        if (parts.length >= 3) {
            try {
                int year = Integer.parseInt(parts[1]);
                int next = year + 1;
                String fy = "FY" + (year % 100) + (next % 100);
                return "ENV_" + programmeCode + "_" + fy;
            } catch (NumberFormatException ignore) {}
        }
        return "ENV_" + programmeCode + "_FY2526";
    }

    private static String prop(FormRow row, String key) {
        if (row == null || key == null) return null;
        Object v = row.getProperty(key);
        if (v == null) v = row.getProperty(key.toLowerCase());
        return v == null ? null : v.toString();
    }

    private static String nz(String s, String fallback) {
        return (s == null || s.isEmpty()) ? fallback : s;
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return new BigDecimal(s.trim()); }
        catch (NumberFormatException nfe) { return null; }
    }
}
