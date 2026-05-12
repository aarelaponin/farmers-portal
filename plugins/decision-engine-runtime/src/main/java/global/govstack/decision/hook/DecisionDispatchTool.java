package global.govstack.decision.hook;

import global.govstack.decision.Build;
import global.govstack.decision.service.AuditLogger;
import global.govstack.decision.service.EntitlementGenerator;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import java.util.Map;

/**
 * Wired as the form post-processor on {@code spApplicationDecision}. Reads
 * the operator's selected decision + reason, then:
 * <ol>
 *   <li>updates the wrapper application's {@code status} to one of
 *       APPROVED / REJECTED / NEEDS_INFO,</li>
 *   <li>writes a row to {@code app_fd_audit_log} with actor + reason,</li>
 *   <li>on APPROVE only — invokes {@link EntitlementGenerator} to issue
 *       the entitlement record.</li>
 * </ol>
 *
 * <p>Idempotent. If the application is already at the same terminal status,
 * the second invocation only updates audit (no duplicate entitlement).
 */
public class DecisionDispatchTool extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = DecisionDispatchTool.class.getName();

    private static final String F_DECISION_TAB = "spApplicationDecision";
    private static final String T_DECISION_TAB = "sp_application_decision";
    private static final String F_APPLICATION  = "spApplication";
    private static final String T_APPLICATION  = "sp_application";

    @Override
    public String getName()        { return "Application Decision Dispatcher"; }
    @Override
    public String getDescription() {
        return "Operator decision on a subsidy application — Approve / Reject / "
             + "Request Info. Approve issues an entitlement; all branches "
             + "write to audit_log. [" + Build.STAMP + "]";
    }
    @Override
    public String getVersion()     { return "8.1-SNAPSHOT (" + Build.STAMP + ")"; }
    @Override
    public String getLabel()       { return getName(); }
    @Override
    public String getClassName()   { return getClass().getName(); }

    @Override
    public String getPropertyOptions() { return "[]"; }

    @Override
    public Object execute(Map properties) {
        try {
            String recordId = stringProp(properties, "recordId");
            LogUtil.info(CLASS_NAME, "[" + Build.STAMP + "] execute() recordId=" + recordId);
            if (recordId == null || recordId.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "no recordId — skipping.");
                return null;
            }

            FormDataDao dao = (FormDataDao)
                    AppUtil.getApplicationContext().getBean("formDataDao");

            // Resolve the decision-tab record (recordId from the post-processor)
            // and walk up to the wrapper application via parent_id.
            FormRow decisionRow = dao.load(F_DECISION_TAB, T_DECISION_TAB, recordId);
            if (decisionRow == null) {
                LogUtil.warn(CLASS_NAME, "decision tab " + recordId
                        + " not found — skipping.");
                return null;
            }
            String wrapperId = decisionRow.getProperty("parent_id");
            if (wrapperId == null || wrapperId.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "decision tab " + recordId
                        + " has no parent_id — skipping.");
                return null;
            }

            String decision = decisionRow.getProperty("decision");
            String reason   = decisionRow.getProperty("decision_reason");
            String signoff  = decisionRow.getProperty("decision_signoff");
            if (decision == null || decision.isEmpty()) {
                LogUtil.info(CLASS_NAME, "no decision selected yet for application "
                        + wrapperId + " — skipping.");
                return null;
            }

            String newStatus;
            switch (decision.toUpperCase()) {
                case "APPROVE":      newStatus = "APPROVED";    break;
                case "REJECT":       newStatus = "REJECTED";    break;
                case "REQUEST_INFO": newStatus = "NEEDS_INFO";  break;
                default:
                    LogUtil.warn(CLASS_NAME, "unknown decision value '" + decision
                            + "' — skipping.");
                    return null;
            }

            FormRow application = dao.load(F_APPLICATION, T_APPLICATION, wrapperId);
            if (application == null) {
                LogUtil.warn(CLASS_NAME, "application " + wrapperId
                        + " not found — skipping.");
                return null;
            }
            String oldStatus = application.getProperty("status");

            // Update wrapper status
            if (!newStatus.equals(oldStatus)) {
                application.setProperty("status", newStatus);
                FormRowSet batch = new FormRowSet();
                batch.add(application);
                dao.saveOrUpdate(F_APPLICATION, T_APPLICATION, batch);
                LogUtil.info(CLASS_NAME, "application " + wrapperId
                        + " status: " + oldStatus + " -> " + newStatus);
            }

            String actor = currentUsername();

            // Audit write — happens on every decision, including idempotent re-runs.
            new AuditLogger(dao).logDecision(wrapperId, oldStatus, newStatus, actor,
                    "[" + decision + "] " + nz(reason)
                    + (signoff != null && !signoff.isEmpty() ? " (signed: " + signoff + ")" : ""));

            // Approve branch — generate entitlement.
            if ("APPROVED".equals(newStatus)) {
                String entitlementCode =
                        new EntitlementGenerator(dao).generate(wrapperId, actor);
                if (entitlementCode != null) {
                    LogUtil.info(CLASS_NAME, "entitlement " + entitlementCode
                            + " issued for application " + wrapperId);
                }
            }
        } catch (Throwable t) {
            // Belt-and-braces — never block a save because of a decision bug.
            LogUtil.error(CLASS_NAME, t,
                    "decision dispatch failed: " + t.getMessage());
        }
        return null;
    }

    private static String stringProp(Map properties, String key) {
        Object v = properties.get(key);
        return v == null ? null : v.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String currentUsername() {
        try {
            String n = WorkflowUtil.getCurrentUsername();
            if (n != null && !n.isEmpty()) return n;
        } catch (Throwable ignore) {}
        return "system";
    }
}
