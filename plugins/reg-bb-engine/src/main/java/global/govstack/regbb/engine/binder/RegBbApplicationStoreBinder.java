package global.govstack.regbb.engine.binder;

import global.govstack.regbb.engine.api.DeterminantEvaluator;
import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import global.govstack.regbb.engine.dao.MetaModelDao;
import global.govstack.regbb.engine.evaluator.RoutingEvaluator;
import global.govstack.regbb.engine.support.RowWriter;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 1 storeBinder hook for the application form. Per ADR-005 §2:
 * extends Joget's {@link WorkflowFormBinder}; on save, runs the standard
 * persistence first (tx 1), then evaluates eligibility Determinants for the
 * applicant's selected Registration(s), aggregates per
 * {@code mm_registration.evaluationStrategy} (D6), persists the structured
 * outcome to the {@code eligibility_outcome} column (tx 2). Per ADR-007: two
 * transactions; never-null discipline (try/catch wraps eval; second write
 * always runs); failure becomes data ({@code disposition=indeterminate}).
 *
 * <p>Bind by editing the application form's storeBinder property:
 * {@code "storeBinder": { "className": "global.govstack.regbb.engine.binder.RegBbApplicationStoreBinder" }}.
 *
 * <p>Per ADR-002 r2: single-bundle layout; binder, evaluator and DAO live in
 * {@code reg-bb-engine} together. Direct construction over OSGi service
 * lookups — no cross-bundle traffic to mediate.
 *
 * <p>Per Phase 3 plan (architecture doc §13 slice 3), this binder retires when
 * {@code MetaScreenElement.submit} ships. Until then it carries the eval hook.
 */
public class RegBbApplicationStoreBinder extends WorkflowFormBinder {

    private static final String CLASS_NAME = RegBbApplicationStoreBinder.class.getName();

    @Override
    public String getName() { return "RegBB Application Store Binder"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getLabel() { return "RegBB Application Store Binder"; }

    @Override
    public String getClassName() { return getClass().getName(); }

    @Override
    public String getDescription() { return "Persists the application row, then evaluates eligibility Determinants and writes the structured outcome to dataJson.eligibilityOutcome (per ADR-005, ADR-006, ADR-007)."; }

    @Override
    public FormRowSet store(Element element, FormRowSet rowSet, FormData formData) {
        // ADR-030: the binder now does ONLY the standard form save and
        // returns. Eligibility evaluation and budget dispatch run
        // asynchronously via EligibilityProcessingWorker, which discovers
        // pending applications by polling app_fd_subsidy_app_2025 for rows
        // where c_status is empty AND c_lifecycleState='submitted' (added
        // W3.4 — DRAFT rows must NOT be auto-processed).
        //
        // Design note (revision after 2026-05-05 incident):
        // We initially wrote a row to app_fd_processing_queue here. That
        // shared the form-save transaction; any Hibernate failure inside
        // the queue write marked the transaction rollback-only, which
        // Spring then surfaced as UnexpectedRollbackException at commit
        // time, causing the entire submit to fail — even though the
        // try/catch around the queue write was supposed to protect us.
        // The lesson: any FormDataDao call inside store() participates
        // in the form-save transaction, and catching its exceptions
        // doesn't undo the rollback flag. The queue table now exists
        // for failure-tracking only (worker writes there on retry
        // exhaustion, not on the happy path).
        FormRowSet stored = super.store(element, rowSet, formData);

        // W3.4 — DRAFT/SUBMITTED lifecycle flip based on the citizen's
        // submission confirmation. The checkbox `submit_confirmation` on
        // the final wizard tab (APP_REVIEW screen) is the explicit
        // submission signal: ticked → transition DRAFT → SUBMITTED, worker
        // picks it up; left unticked → stays DRAFT, citizen can return
        // later and continue editing.
        try {
            applyLifecycleTransition(stored, formData);
        } catch (Throwable t) {
            // Lifecycle-audit failures must NEVER block the form save.
            LogUtil.warn(CLASS_NAME, "lifecycle transition failed: "
                    + t.getClass().getSimpleName() + ":" + t.getMessage());
        }
        return stored;
    }

    /**
     * W3.4 lifecycle hook. Reads {@code submit_confirmation} from the request
     * parameters; if ticked, transitions to SUBMITTED; otherwise ensures the
     * row is in DRAFT (create-if-missing). Idempotent on DRAFT: re-saving a
     * DRAFT row is a no-op for the lifecycle column.
     */
    private void applyLifecycleTransition(FormRowSet stored, FormData formData) {
        if (stored == null || stored.isEmpty() || formData == null) return;
        FormRow row = stored.get(0);
        String applicationId = row.getId();
        if (applicationId == null || applicationId.isEmpty()) return;

        // Citizen submission confirmation. The checkbox emits "Y" when
        // ticked, empty / null when unticked. The mm_field's `widget=checkbox`
        // is rendered via MetaScreenElement which wires Joget's CheckBox; the
        // single option's `value="Y"` means tick → "Y" in the request param.
        String[] vals = formData.getRequestParameterValues("submit_confirmation");
        boolean submitting = false;
        if (vals != null) {
            for (String v : vals) {
                if (v != null && ("Y".equalsIgnoreCase(v.trim())
                                || "true".equalsIgnoreCase(v.trim())
                                || "yes".equalsIgnoreCase(v.trim()))) {
                    submitting = true; break;
                }
            }
        }

        global.govstack.regbb.engine.lifecycle.AppLifecycleStatus current =
            global.govstack.regbb.engine.lifecycle.AppAudit.currentState(applicationId);

        String actor = "citizen:" + currentUsername();
        if (submitting) {
            // The citizen explicitly ticked "I confirm submission of this
            // application". Transition to SUBMITTED — worker will pick it up.
            if (current == global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.SUBMITTED
             || current == global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.UNDER_REVIEW
             || current == global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.APPROVED
             || current == global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.REJECTED
             || current == global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.PENDING_REVIEW
             || current == global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.WITHDRAWN) {
                // Already submitted (or further along). Don't re-fire.
                return;
            }
            global.govstack.regbb.engine.lifecycle.AppAudit.transition(
                    applicationId,
                    global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.SUBMITTED,
                    actor,
                    "citizen confirmed submission on final tab");
            LogUtil.info(CLASS_NAME, "Lifecycle: " + applicationId + " → SUBMITTED ("
                    + actor + ", from " + current + ")");
        } else {
            // No explicit submission confirmation — keep as DRAFT (or
            // create initially as DRAFT for a brand-new row).
            if (current == null) {
                global.govstack.regbb.engine.lifecycle.AppAudit.create(
                        applicationId,
                        global.govstack.regbb.engine.lifecycle.AppLifecycleStatus.DRAFT,
                        actor,
                        "citizen saved wizard tab (no submission yet)");
                LogUtil.info(CLASS_NAME, "Lifecycle: " + applicationId
                        + " null → DRAFT (" + actor + ")");
            }
            // If current is DRAFT, no-op. If current is SUBMITTED or later,
            // the citizen is editing a submitted application — that's allowed,
            // but we don't downgrade the state.
        }
    }

    /** Builds context, looks up evaluator, evaluates, persists outcome.
     *  Never throws to caller — failure becomes the indeterminate outcome. */
    private void evaluateAndPersist(Element element, FormRowSet stored, FormData formData) {
        if (stored == null || stored.isEmpty()) {
            LogUtil.warn(CLASS_NAME, "stored rowSet empty; skipping evaluation");
            return;
        }
        FormRow row = stored.get(0);
        String applicationId = row.getId();
        String appliedProgramme = prop(row, "applied_programme");

        Form form = findRootForm(element);
        if (form == null) {
            LogUtil.warn(CLASS_NAME, "rootForm not found; skipping evaluation");
            return;
        }
        String formDefId = form.getPropertyString("id");
        String tableName = form.getPropertyString("tableName");

        Map<String, Object> data = new HashMap<>();
        for (Object k : row.keySet()) {
            data.put(k.toString(), row.get(k));
        }
        runChain(applicationId, appliedProgramme, formDefId, tableName, data,
                 currentUsername());
    }

    /**
     * ADR-030 — public entry point for the eligibility chain. Same logic as
     * {@link #evaluateAndPersist} but with explicit args, callable by the
     * async worker (Step 3) without needing Joget {@code Element} /
     * {@code FormRowSet} / {@code FormData} contexts.
     *
     * <p>Side effects: writes {@code c_eligibility_outcome} + transitions
     * {@code c_status} on {@code app_fd_<tableName>}, writes audit rows,
     * dispatches budget lifecycle events. Never throws — all failure paths
     * land in {@code disposition=indeterminate} per ADR-007 P5.
     */
    public void runChain(String applicationId, String appliedProgramme,
                         String formDefId, String tableName,
                         Map<String, Object> data, String actor) {
        if (applicationId == null || applicationId.isEmpty()) {
            LogUtil.warn(CLASS_NAME, "runChain: missing applicationId — skipping");
            return;
        }
        if (formDefId == null || formDefId.isEmpty()) formDefId = "subsidyApplication2025";
        if (tableName == null || tableName.isEmpty()) tableName = "subsidy_app_2025";

        EvalContext ctx = EvalContext.builder()
                .applicationId(applicationId)
                .data(data == null ? new HashMap<>() : data)
                .currentUsername(actor)
                .build();

        String outcomeJson;
        try {
            outcomeJson = aggregate(appliedProgramme, ctx);
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "evaluation failure for applicationId=" + applicationId);
            outcomeJson = indeterminateOutcome("internal:" + t.getClass().getSimpleName() + ":" + safe(t.getMessage()));
        }

        // tx 2: persist outcome — always runs, including in failure paths.
        try {
            persistOutcome(formDefId, tableName, applicationId, outcomeJson, appliedProgramme);
        } catch (Throwable persistFailure) {
            LogUtil.error(CLASS_NAME, persistFailure, "Failed to persist eligibility outcome for applicationId=" + applicationId);
        }

        // Best-effort cache invalidate.
        try { evaluator().invalidate(applicationId); } catch (Throwable ignore) {}

        // L3-1 1B-ii — fire budget lifecycle events.
        try {
            String finalStatus = statusForDisposition(outcomeJson, "SUBSIDY_2025", appliedProgramme);
            global.govstack.regbb.engine.budget.BudgetEngine.fireForLifecycle(
                    applicationId, appliedProgramme, finalStatus,
                    data == null ? new HashMap<>() : data, actor);
        } catch (Throwable budgetFailure) {
            LogUtil.error(CLASS_NAME, budgetFailure,
                    "Budget lifecycle dispatch failed for applicationId=" + applicationId
                  + " (non-fatal — application save still committed)");
        }
    }

    /** Aggregate eligibility-scope Determinants for the applied registration.
     *  Slice 1B-a: walks all rules where {@code mm_determinant.registrationId =
     *  appliedProgramme AND scope = applicability}, evaluates each via the
     *  fast-path evaluator, aggregates per {@code mm_registration.evaluationStrategy}
     *  (D6). Falls back to the slice-1A single-rule path
     *  ({@code applicabilityDeterminantId}) when the registration has no
     *  multi-rule entries — preserves backward compatibility for older fixtures.
     *  Score-based aggregation lands in slice 1B-c. */
    private String aggregate(String appliedProgramme, EvalContext ctx) {
        if (appliedProgramme == null || appliedProgramme.isEmpty()) {
            return jsonObject(
                    "disposition", "indeterminate",
                    "reason", "no_programme_selected"
            );
        }

        MetaModelDao dao = new MetaModelDao();

        FormRow registration = dao.findRegistrationByCode(appliedProgramme);
        if (registration == null) {
            return indeterminateOutcome("registration_not_found:" + appliedProgramme);
        }

        String evalStrategy = nz(prop(registration, "evaluationStrategy"), "all_must_pass");

        // Slice 1B-a: prefer the multi-rule listing. If no rows match, fall
        // back to the legacy single-rule applicabilityDeterminantId path so
        // pre-1B-a fixtures keep working without surprises.
        List<FormRow> determinants = dao.listDeterminantsForRegistration(appliedProgramme, "applicability");
        if (determinants.isEmpty()) {
            String legacyCode = prop(registration, "applicabilityDeterminantId");
            if (legacyCode != null && !legacyCode.isEmpty()) {
                FormRow legacy = dao.findDeterminantByCode(legacyCode);
                if (legacy != null) {
                    determinants = Collections.singletonList(legacy);
                }
            }
        }

        if (determinants.isEmpty()) {
            return jsonObject(
                    "disposition", "eligibility_passed",
                    "reason", "no_applicability_rule_configured",
                    "evaluationStrategy", evalStrategy,
                    "appliedProgramme", appliedProgramme
            );
        }

        // Evaluate every rule. Per-rule result captured for the outcome JSON.
        DeterminantEvaluator evaluator = evaluator();
        List<RuleEval> evals = new ArrayList<>();
        for (FormRow det : determinants) {
            String code = prop(det, "code");
            if (code == null || code.isEmpty()) continue;
            EvalResult result = evaluator.evaluate(code, ctx);
            evals.add(new RuleEval(code, result, prop(det, "failMessage")));
        }

        // Aggregate per strategy. Slice 1B-a added all_must_pass; slice 1B-c
        // adds score_based + the pending_review band (D15). weighted_score
        // and stricter strategies stay deferred — every other strategy falls
        // back to all_must_pass with the configured value reported in the
        // outcome so operators can see what *would* have applied.
        Aggregation agg;
        if ("score_based".equalsIgnoreCase(evalStrategy)) {
            agg = aggregateScoreBased(evals, determinants, registration);
        } else {
            agg = aggregateAllMustPass(evals);
        }

        // Build outcome JSON.
        List<Object> kv = new ArrayList<>();
        kv.add("disposition");        kv.add(agg.disposition);
        kv.add("evaluationStrategy"); kv.add(evalStrategy);
        kv.add("appliedProgramme");   kv.add(appliedProgramme);
        kv.add("evaluatedAt");        kv.add(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new java.util.Date()));
        if (agg.failMessage != null) {
            kv.add("failMessage"); kv.add(agg.failMessage);
        }
        if (agg.failedRule != null) {
            kv.add("failedRule"); kv.add(agg.failedRule);
        }
        if (agg.score != null) {
            kv.add("score"); kv.add(agg.score);
        }
        if (agg.passingThreshold != null) {
            kv.add("passingThreshold"); kv.add(agg.passingThreshold);
        }
        if (agg.minimumScore != null) {
            kv.add("minimumScore"); kv.add(agg.minimumScore);
        }
        kv.add("rulesEvaluated");     kv.add(evals.size());
        kv.add("rulesJson");          kv.add(rulesArrayJson(evals));
        return jsonObject(kv.toArray());
    }

    /** all_must_pass: any ERROR or NULL → indeterminate; any FALSE → failed_mandatory;
     *  all TRUE → passed. ERROR/NULL outrank FALSE because the engine has lost
     *  a decision dimension and we can't honestly say "rejected" — per ADR-007
     *  and spec P5. */
    private Aggregation aggregateAllMustPass(List<RuleEval> evals) {
        boolean sawError = false; String errorCause = null; String errorRule = null;
        boolean sawNull  = false; String nullRule    = null;
        boolean sawFalse = false; String falseRule   = null; String falseMsg = null;

        for (RuleEval e : evals) {
            switch (e.result.outcome) {
                case ERROR:
                    if (!sawError) { sawError = true; errorCause = e.result.errorCause; errorRule = e.code; }
                    break;
                case NULL:
                    if (!sawNull) { sawNull = true; nullRule = e.code; }
                    break;
                case FALSE:
                    if (!sawFalse) { sawFalse = true; falseRule = e.code; falseMsg = e.failMessage; }
                    break;
                case TRUE:
                default:
                    break;
            }
        }

        if (sawError) return new Aggregation("indeterminate", errorRule, "evaluator_error:" + errorCause);
        if (sawNull)  return new Aggregation("indeterminate", nullRule,  "rule_returned_null:applicant_data_incomplete");
        if (sawFalse) return new Aggregation("eligibility_failed_mandatory", falseRule, falseMsg);
        return new Aggregation("eligibility_passed", null, null);
    }

    /** score_based + D15 pending_review band. Each rule contributes its
     *  configured {@code score} when TRUE; FALSE contributes 0. The summed
     *  score is then placed in one of three bands defined by
     *  {@code mm_registration.passingThreshold} and {@code mm_registration.minimumScore}:
     *
     *  <ul>
     *    <li>{@code score >= passingThreshold} → {@code eligibility_passed}</li>
     *    <li>{@code minimumScore <= score < passingThreshold} →
     *        {@code eligibility_pending_review} (D15 — operator review)</li>
     *    <li>{@code score < minimumScore} → {@code eligibility_failed_mandatory}</li>
     *  </ul>
     *
     *  <p>NULL or ERROR on any rule still produces {@code indeterminate} —
     *  score-based isn't a way to skip past missing data per spec P5
     *  (loud failure). The engine will not silently treat NULL as 0 when
     *  it could mean "we couldn't tell".
     *
     *  <p>If both thresholds are unset (or zero), the rule degenerates to
     *  "any TRUE rule passes" — score-based without configuration is
     *  surprising but not broken.
     */
    private Aggregation aggregateScoreBased(List<RuleEval> evals, List<FormRow> determinants, FormRow registration) {
        // ERROR / NULL preempt scoring — same as all_must_pass.
        for (RuleEval e : evals) {
            if (e.result.outcome == EvalResult.Outcome.ERROR) {
                return new Aggregation("indeterminate", e.code,
                        "evaluator_error:" + e.result.errorCause);
            }
            if (e.result.outcome == EvalResult.Outcome.NULL) {
                return new Aggregation("indeterminate", e.code,
                        "rule_returned_null:applicant_data_incomplete");
            }
        }

        // Sum scores of TRUE rules. The score is on the determinant row
        // itself (mm_determinant.score), not the registration. Fall back to
        // 0 if a row's score is missing or unparseable — a misconfigured
        // rule shouldn't crash the binder.
        Map<String, Integer> scoreByCode = new HashMap<>();
        for (FormRow det : determinants) {
            String code = prop(det, "code");
            int score = parseIntSafe(prop(det, "score"), 0);
            scoreByCode.put(code, score);
        }
        int totalScore = 0;
        String topRuleCode = null;
        int topRuleScore = -1;
        for (RuleEval e : evals) {
            if (e.result.outcome == EvalResult.Outcome.TRUE) {
                Integer s = scoreByCode.get(e.code);
                int sv = s == null ? 0 : s;
                totalScore += sv;
                if (sv > topRuleScore) { topRuleScore = sv; topRuleCode = e.code; }
            }
        }

        int passingThreshold = parseIntSafe(prop(registration, "passingThreshold"), 0);
        int minimumScore     = parseIntSafe(prop(registration, "minimumScore"),     0);

        Aggregation a;
        if (totalScore >= passingThreshold && passingThreshold > 0) {
            a = new Aggregation("eligibility_passed", null, null);
        } else if (totalScore >= minimumScore && passingThreshold > minimumScore) {
            // Pending review band — D15. Some rules passed but score is below
            // the strict threshold. failMessage flags this as advisory, not
            // a rejection.
            a = new Aggregation("eligibility_pending_review",
                    null,
                    "score_below_passing_threshold:" + totalScore + "<" + passingThreshold);
        } else {
            // Sum below minimumScore, or thresholds unset — treat as failed.
            // Identify the first FALSE rule for failedRule + failMessage.
            String failedRule = null;
            String failMsg    = null;
            for (RuleEval e : evals) {
                if (e.result.outcome == EvalResult.Outcome.FALSE) {
                    failedRule = e.code;
                    failMsg    = e.failMessage;
                    break;
                }
            }
            a = new Aggregation("eligibility_failed_mandatory", failedRule,
                    failMsg != null ? failMsg : ("score_below_minimum:" + totalScore + "<" + minimumScore));
        }
        a.score            = totalScore;
        a.passingThreshold = passingThreshold;
        a.minimumScore     = minimumScore;
        a.topRuleCode      = topRuleCode;
        return a;
    }

    /** Parse a String to int, returning {@code dflt} on null / blank / NumberFormatException.
     *  Used for score / threshold reads off mm_determinant + mm_registration where
     *  the column type is text per Joget convention. */
    private static int parseIntSafe(String s, int dflt) {
        if (s == null || s.trim().isEmpty()) return dflt;
        try { return (int) Math.round(Double.parseDouble(s.trim())); }
        catch (NumberFormatException nfe) { return dflt; }
    }

    /** Render the per-rule breakdown as a tiny JSON array literal. Stays
     *  with the file's no-Gson convention so the binder remains a single
     *  self-contained class. */
    private String rulesArrayJson(List<RuleEval> evals) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < evals.size(); i++) {
            if (i > 0) sb.append(',');
            RuleEval e = evals.get(i);
            sb.append('{')
              .append("\"code\":\"").append(escape(e.code)).append("\",")
              .append("\"outcome\":\"").append(e.result.outcome.name()).append("\",")
              .append("\"evaluator\":\"").append(escape(nz(e.result.evaluator, ""))).append("\"");
            if (e.result.errorCause != null) {
                sb.append(",\"errorCause\":\"").append(escape(e.result.errorCause)).append("\"");
            }
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    /** Per-rule evaluation tuple. Package-private for clarity; never escapes
     *  the binder. */
    private static final class RuleEval {
        final String code;
        final EvalResult result;
        final String failMessage;
        RuleEval(String code, EvalResult result, String failMessage) {
            this.code = code;
            this.result = result;
            this.failMessage = failMessage;
        }
    }

    /** Aggregation outcome — disposition plus the rule that drove it (if any)
     *  plus its message. Score fields are populated by score_based aggregation
     *  and stay null for all_must_pass; rendered into the outcome JSON only
     *  when set so the simpler strategy stays uncluttered. */
    private static final class Aggregation {
        final String disposition;
        final String failedRule;
        final String failMessage;
        Integer score;             // total score for the applicant (score_based only)
        Integer passingThreshold;  // mm_registration.passingThreshold echoed back for transparency
        Integer minimumScore;      // mm_registration.minimumScore echoed back
        String  topRuleCode;       // highest-scoring TRUE rule (informational)
        Aggregation(String disposition, String failedRule, String failMessage) {
            this.disposition = disposition;
            this.failedRule = failedRule;
            this.failMessage = failMessage;
        }
    }

    private void persistOutcome(String formDefId, String tableName, String applicationId,
                                String outcomeJson, String appliedProgramme) {
        if (applicationId == null || applicationId.isEmpty()) return;
        FormRow patch = new FormRow();
        patch.setId(applicationId);
        patch.setProperty("eligibility_outcome", outcomeJson);

        // Phase 2-a: derive initial application status from the disposition.
        // Per ADR-027 (initial_status_assignment scope), the mapping is rule-
        // driven; programme-specific rules can override the default. Operator
        // decisions later in the lifecycle move the status to approved /
        // rejected / sent_back via RegBbOperatorDecisionBinder.
        String status = statusForDisposition(outcomeJson, "SUBSIDY_2025", appliedProgramme);
        if (status != null) patch.setProperty("status", status);

        FormRowSet rs = new FormRowSet();
        rs.add(patch);
        // Task #235: route through RowWriter so dateModified + modifiedBy
        // get populated. Direct dao.saveOrUpdate skipped Joget's metadata
        // logic and left timestamps NULL on every subsidy_app_2025 row.
        RowWriter.save(formDefId, tableName, rs);
    }

    /** Map an eligibility outcome JSON to the initial application status.
     *
     *  <p>Per ADR-027 ({@code initial_status_assignment} scope), the mapping
     *  is rule-driven: {@link StatusPolicyResolver#resolveInitialStatus}
     *  iterates {@code mm_determinant} rows in priority order
     *  (programme-specific → service-wide → global) and returns the first
     *  matching rule's {@code targetValue}. If no rule matches in any tier,
     *  the hardcoded fallback below applies — preserves backwards
     *  compatibility when seed rules are missing.
     *
     *  <p>Tiny string scan to extract the disposition; keeps this method
     *  self-contained and the JSON shape is fully under our control.
     */
    static String statusForDisposition(String outcomeJson) {
        return statusForDisposition(outcomeJson, null, null);
    }

    /** Variant with explicit service / programme codes for tier-1 / tier-2
     *  rule lookup. Public callers (REST endpoints, the binder above)
     *  should call this overload when they know the codes. */
    static String statusForDisposition(String outcomeJson,
                                       String serviceCode,
                                       String registrationCode) {
        if (outcomeJson == null) return null;
        String disposition = extractDisposition(outcomeJson);
        if (disposition == null) return null;

        // Try rule-driven resolution first (ADR-027)
        String fromRules = StatusPolicyResolver.resolveInitialStatus(
                disposition, serviceCode, registrationCode);
        if (fromRules != null) return fromRules;

        // Hardcoded fallback (defensive — used when seed rules are absent)
        switch (disposition) {
            case "eligibility_passed":           return "auto_approved";
            case "eligibility_failed_mandatory": return "auto_rejected";
            case "eligibility_pending_review":   return "pending_operator_review";
            case "indeterminate":                return "pending_data_clarification";
            default: return null;
        }
    }

    private static String extractDisposition(String outcomeJson) {
        if (outcomeJson == null) return null;
        // Tiny string scan — the JSON shape is under our control.
        if (outcomeJson.contains("\"disposition\":\"eligibility_passed\""))           return "eligibility_passed";
        if (outcomeJson.contains("\"disposition\":\"eligibility_failed_mandatory\"")) return "eligibility_failed_mandatory";
        if (outcomeJson.contains("\"disposition\":\"eligibility_pending_review\""))   return "eligibility_pending_review";
        if (outcomeJson.contains("\"disposition\":\"indeterminate\""))                return "indeterminate";
        return null;
    }

    static String indeterminateOutcome(String reason) {
        return jsonObject(
                "disposition", "indeterminate",
                "reason", reason
        );
    }

    /** Tiny JSON object writer — alternating (key,value,...) varargs. */
    static String jsonObject(Object... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(String.valueOf(kv[i]))).append('"').append(':');
            Object v = kv[i + 1];
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else sb.append('"').append(escape(String.valueOf(v))).append('"');
        }
        return sb.append("}").toString();
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    static String prop(FormRow row, String key) {
        if (row == null || key == null) return null;
        Object v = row.get(key);
        if (v == null) v = row.get(key.toLowerCase());
        return v == null ? null : v.toString();
    }

    static String nz(String s, String dflt) { return (s == null || s.isEmpty()) ? dflt : s; }

    static String safe(String s) { return s == null ? "" : s.replaceAll("[\\r\\n]+", " "); }

    /** Current username — looked up reflectively to avoid hard dep on
     *  wflow-directory's WorkflowUserManager class at compile time.
     *  Slice 1B+ promotes this to a typed lookup. */
    static String currentUsername() {
        try {
            Object wum = AppUtil.getApplicationContext().getBean("workflowUserManager");
            if (wum == null) return null;
            java.lang.reflect.Method m = wum.getClass().getMethod("getCurrentUsername");
            Object u = m.invoke(wum);
            return u == null ? null : u.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    static Form findRootForm(Element element) {
        Element el = element;
        while (el != null) {
            if (el instanceof Form) return (Form) el;
            el = el.getParent();
        }
        return null;
    }

    /** Direct instantiation per ADR-002 r2 — same bundle, no cross-bundle
     *  service mediation needed. As of slice 1B-b the binder talks to the
     *  {@link RoutingEvaluator}, which fronts both fast-path and SQL-path
     *  evaluators and routes per-determinant by static AST analysis. The
     *  evaluator is stateless modulo its ThreadLocal L1 cache and an
     *  in-process routing-decision map, so a fresh instance per save is
     *  fine — promote to a singleton when slice 1B-d's L2 cache lands. */
    private DeterminantEvaluator evaluator() {
        return new RoutingEvaluator();
    }
}
