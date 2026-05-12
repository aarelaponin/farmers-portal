package global.govstack.regbb.engine.binder;

import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import global.govstack.regbb.engine.audit.AuditWriter;
import global.govstack.regbb.engine.workflow.WorkflowDispatcher;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 2-a operator decision binder. Wired as the {@code storeBinder} on
 * {@code subsidyApplicationOperator2025}. When an operator saves the Decision
 * tab, this binder:
 *
 * <ol>
 *   <li>Runs the standard {@link WorkflowFormBinder} persistence (tx 1) so
 *       the operator-supplied {@code decision}, {@code decision_score},
 *       {@code decision_comment}, {@code decided_at} columns hit the row.</li>
 *   <li>Reads back {@code decision} and transitions the application
 *       {@code status} accordingly: {@code approve → approved},
 *       {@code reject → rejected}, {@code send_back → sent_back} (tx 2).</li>
 *   <li>Writes a synthetic audit row tagged {@code operator_decision} to
 *       {@code reg_bb_eval_audit} so the operator's decision shows up in the
 *       same audit list as eligibility evaluations — single source of truth
 *       for "what happened to this application" forensics.</li>
 * </ol>
 *
 * <p>Per ADR-007 never-null discipline: tx 2 is wrapped in {@code try/catch};
 * if the status transition or audit write fails, the operator's decision
 * still persisted in tx 1, so we never lose their input.
 *
 * <p>Phase 2-b will replace this direct-transition pattern with a Joget XPDL
 * workflow process that fires on operator decision and orchestrates
 * notification + downstream tasks. The state machine semantics are
 * forward-compatible.
 */
public class RegBbOperatorDecisionBinder extends WorkflowFormBinder {

    private static final String CLASS_NAME = RegBbOperatorDecisionBinder.class.getName();
    private static final SimpleDateFormat ISO_UTC =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Override public String getName()        { return "RegBB Operator Decision Binder"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getLabel()       { return "RegBB Operator Decision Binder"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getDescription() { return "Persists the operator's decision and transitions the application status (Phase 2-a state machine)."; }

    @Override
    public FormRowSet store(Element element, FormRowSet rowSet, FormData formData) {
        // tx 1 — standard persistence of decision/score/comment/decided_at.
        FormRowSet stored = super.store(element, rowSet, formData);

        try {
            transitionStatus(element, stored);
        } catch (Throwable t) {
            // Operator's decision stays persisted; only the status transition
            // failed. Logged for ops; never propagates.
            LogUtil.error(CLASS_NAME, t, "RegBbOperatorDecisionBinder transition failed");
        }
        return stored;
    }

    private void transitionStatus(Element element, FormRowSet stored) {
        if (stored == null || stored.isEmpty()) {
            LogUtil.warn(CLASS_NAME, "stored rowSet empty; skipping status transition");
            return;
        }
        FormRow row = stored.get(0);
        String applicationId    = row.getId();
        String decision         = prop(row, "decision");
        String registrationCode = prop(row, "applied_programme");
        // Per ADR-028 (decision_to_status scope), the mapping is rule-driven;
        // programme-specific or service-specific rules can override the default.
        // Hardcoded fallback (statusForDecision below) preserves prior behaviour
        // when seed rules are missing.
        String newStatus = statusForDecision(decision, "SUBSIDY_2025", registrationCode);
        if (newStatus == null) {
            LogUtil.warn(CLASS_NAME, "no decision recognised on save (decision='" + decision
                    + "'); status unchanged for applicationId=" + applicationId);
            return;
        }

        Form form = findRootForm(element);
        if (form == null) {
            LogUtil.warn(CLASS_NAME, "rootForm not found; skipping status transition");
            return;
        }
        // Operator form points at the same underlying table (subsidy_app_2025)
        // as the citizen form, so we update it via the citizen formDefId so
        // the standard subsidyApplication2025 metadata applies.
        String formDefId  = "subsidyApplication2025";
        String tableName  = form.getPropertyString("tableName");
        if (tableName == null || tableName.isEmpty()) tableName = "subsidy_app_2025";

        FormRow patch = new FormRow();
        patch.setId(applicationId);
        patch.setProperty("status", newStatus);
        FormRowSet rs = new FormRowSet();
        rs.add(patch);
        try {
            // Task #235: route through RowWriter so dateModified + actor
            // attribution land on the row. Direct dao.saveOrUpdate bypassed
            // Joget's metadata logic and left timestamps NULL.
            global.govstack.regbb.engine.support.RowWriter.save(formDefId, tableName, rs);
            LogUtil.info(CLASS_NAME, "applicationId=" + applicationId
                    + " transitioned status → " + newStatus + " (decision=" + decision + ")");
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "status patch failed for applicationId=" + applicationId);
        }

        // Audit row — same table as evaluation audit. Tagged with a synthetic
        // determinantCode so operators can filter for decision events. The
        // full saved row goes into the audit context so applicant identifier
        // fields (full_name, national_id, applied_programme) get snapshotted
        // onto the audit row alongside the decision metadata.
        try {
            writeDecisionAudit(applicationId, decision, newStatus, row);
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "decision audit write failed: " + t.getMessage());
        }

        // W3.1 — transition the application's lifecycle state via the shared
        // joget-status-framework. The fine-grained newStatus (auto_approved,
        // approved, rejected, pending_data_clarification) gets mapped to a
        // coarse lifecycle state (APPROVED / REJECTED / PENDING_REVIEW) and
        // written to c_lifecycleState; audit_log gets one row per transition.
        try {
            global.govstack.regbb.engine.lifecycle.AppLifecycleStatus target =
                global.govstack.regbb.engine.lifecycle.AppLifecycleMapper.fromStatus(newStatus);
            String actor = "operator:" + currentUsername();
            global.govstack.regbb.engine.lifecycle.AppAudit.transition(
                    applicationId, target, actor,
                    "operator decision=" + decision + " → c_status=" + newStatus);
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "AppAudit.transition failed for "
                    + applicationId + ": " + t.getMessage());
        }

        // L3-1 1B-ii — fire budget lifecycle events for the operator decision.
        // Idempotent on (action+app+eventType) — the citizen-side store
        // binder already fired RESERVATION at submit, so the operator's
        // approve/reject re-fire of RESERVATION is a no-op; only the
        // PRE_COMMITMENT (or RELEASE_RESERVATION) is a fresh posting.
        // Failures are logged but don't break the decision flow.
        try {
            Map<String, Object> appData = new java.util.HashMap<>();
            for (Object k : row.keySet()) appData.put(k.toString(), row.get(k));
            global.govstack.regbb.engine.budget.BudgetEngine.fireForLifecycle(
                    applicationId, registrationCode, newStatus, appData, currentUsername());
        } catch (Throwable budgetFailure) {
            LogUtil.error(CLASS_NAME, budgetFailure,
                    "Budget lifecycle dispatch failed for applicationId=" + applicationId
                  + " decision=" + decision + " (non-fatal)");
        }

        // Phase 2-b: dispatch matching mm_action workflows. Sysadmin
        // declares which Joget process fires for which (service, status)
        // pair via mm_action rows; the dispatcher resolves and invokes them.
        // Failures are swallowed inside the dispatcher and audited as
        // WORKFLOW_DISPATCH rows — operator's decision stays persisted
        // regardless of whether downstream workflows succeed.
        try {
            String serviceId      = prop(row, "service_id");
            String registrationId = prop(row, "applied_programme");
            // service_id isn't always on the row; fall back to looking it up
            // from the applied_programme's mm_registration if unset.
            if ((serviceId == null || serviceId.isEmpty()) && registrationId != null && !registrationId.isEmpty()) {
                serviceId = lookupServiceIdForRegistration(registrationId);
            }

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("onStatus", newStatus);
            if (decision != null) eventData.put("decision", decision);

            Map<String, Object> appData = new HashMap<>();
            for (Object k : row.keySet()) appData.put(k.toString(), row.get(k));

            int started = WorkflowDispatcher.dispatch(
                    serviceId, registrationId, "status_change",
                    applicationId, eventData, appData, currentUsername());
            if (started > 0) {
                LogUtil.info(CLASS_NAME, "WorkflowDispatcher started " + started
                        + " workflow(s) for applicationId=" + applicationId
                        + " status=" + newStatus);
            }
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "workflow dispatch failed: " + t.getMessage());
        }
    }

    /** Resolve {@code mm_registration.serviceId} given the registration code.
     *  Returns null if not found. */
    private static String lookupServiceIdForRegistration(String registrationCode) {
        try {
            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            FormRowSet rs = dao.find("mm_registration", "mm_registration",
                    "WHERE e.customProperties.code = ?",
                    new Object[]{registrationCode}, null, false, null, null);
            if (rs == null || rs.isEmpty()) return null;
            return rs.get(0).getProperty("serviceId");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Map operator decision dropdown to target status.
     *
     *  <p>Per ADR-028 ({@code decision_to_status} scope) the mapping is
     *  rule-driven: {@link StatusPolicyResolver#resolveDecisionStatus}
     *  iterates {@code mm_determinant} rows in priority order
     *  (programme-specific → service-wide → global). Hardcoded fallback
     *  below preserves backwards compatibility when seed rules are missing. */
    static String statusForDecision(String decision) {
        return statusForDecision(decision, null, null);
    }

    /** Variant with explicit service / programme codes. */
    static String statusForDecision(String decision, String serviceCode, String registrationCode) {
        if (decision == null) return null;
        String norm = decision.trim().toLowerCase();

        // Try rule-driven resolution first (ADR-028)
        String fromRules = StatusPolicyResolver.resolveDecisionStatus(
                norm, serviceCode, registrationCode);
        if (fromRules != null) return fromRules;

        // Hardcoded fallback (defensive — used when seed rules are absent)
        switch (norm) {
            case "approve":   return "approved";
            case "reject":    return "rejected";
            case "send_back": return "sent_back";
            default: return null;
        }
    }

    /** Write a row into {@code reg_bb_eval_audit} so the operator decision
     *  shows up alongside eligibility events. The full saved row goes into
     *  ctx.data so applicant identifiers (full_name, national_id,
     *  applied_programme) are snapshotted onto the audit row. We synthesise
     *  a fake determinant_code prefixed {@code OPERATOR_DECISION:} so it's
     *  visually distinct in the audit datalist. */
    private void writeDecisionAudit(String applicationId, String decision, String newStatus, FormRow row) {
        EvalResult fakeResult = new EvalResult(EvalResult.Outcome.TRUE, null, newStatus,
                "operator", null);

        // Bring everything from the saved row into ctx.data; AuditWriter
        // pulls full_name / national_id / applied_programme from there.
        Map<String, Object> data = new HashMap<>();
        if (row != null) {
            for (Object k : row.keySet()) data.put(k.toString(), row.get(k));
        }
        // Override / ensure decision metadata is present even if it wasn't
        // on the row (defensive).
        data.put("decision",          decision);
        String comment = prop(row, "decision_comment");
        String score   = prop(row, "decision_score");
        if (comment != null && !comment.isEmpty()) data.put("decision_comment", comment);
        if (score != null && !score.isEmpty())     data.put("decision_score",   score);

        EvalContext ctx = EvalContext.builder()
                .applicationId(applicationId)
                .data(data)
                .currentUsername(currentUsername())
                .build();

        long now = System.currentTimeMillis();
        String rule = "OPERATOR_DECISION:" + (decision == null ? "?" : decision);
        AuditWriter.write(rule, ctx, fakeResult, "n/a — operator decision", now, now);
    }

    static String currentUsername() {
        try {
            Object wum = AppUtil.getApplicationContext().getBean("workflowUserManager");
            if (wum == null) return null;
            java.lang.reflect.Method m = wum.getClass().getMethod("getCurrentUsername");
            Object u = m.invoke(wum);
            return u == null ? null : u.toString();
        } catch (Throwable t) { return null; }
    }

    static Form findRootForm(Element element) {
        Element el = element;
        while (el != null) {
            if (el instanceof Form) return (Form) el;
            el = el.getParent();
        }
        return null;
    }

    static String prop(FormRow row, String key) {
        if (row == null || key == null) return null;
        Object v = row.get(key);
        if (v == null) v = row.get(key.toLowerCase());
        return v == null ? null : v.toString();
    }
}
