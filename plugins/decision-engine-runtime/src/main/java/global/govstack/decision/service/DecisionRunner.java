package global.govstack.decision.service;

import global.govstack.decision.Build;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;

/**
 * Runs the decision dispatch logic. Called by
 * {@link global.govstack.decision.binder.DecisionStoreBinder} after the
 * Decision tab's row has been persisted (the store-binder approach
 * sidesteps Joget's MultiPagedForm partial-store skipping postProcessors).
 *
 * <p>Reads the decision tab row → resolves the wrapper → updates wrapper
 * status → writes audit row → on APPROVE, calls {@link EntitlementGenerator}.
 */
public class DecisionRunner {

    private static final String CLASS_NAME = DecisionRunner.class.getName();

    private static final String F_DECISION_TAB = "spApplicationDecision";
    private static final String T_DECISION_TAB = "sp_application_decision";
    private static final String F_APPLICATION  = "spApplication";
    private static final String T_APPLICATION  = "sp_application";

    public void run(String decisionTabId) {
        if (decisionTabId == null || decisionTabId.isEmpty()) return;
        try {
            FormDataDao dao = (FormDataDao)
                    AppUtil.getApplicationContext().getBean("formDataDao");

            FormRow decisionRow = dao.load(F_DECISION_TAB, T_DECISION_TAB, decisionTabId);
            if (decisionRow == null) {
                LogUtil.info(CLASS_NAME, "decision tab " + decisionTabId
                        + " not found yet — skipping.");
                return;
            }

            String wrapperId = decisionRow.getProperty("parent_id");
            if (wrapperId == null || wrapperId.isEmpty()) {
                LogUtil.info(CLASS_NAME, "decision tab " + decisionTabId
                        + " has no parent_id — skipping.");
                return;
            }

            String decision = decisionRow.getProperty("decision");
            String reason   = decisionRow.getProperty("decision_reason");
            String signoff  = decisionRow.getProperty("decision_signoff");
            if (decision == null || decision.isEmpty()) {
                LogUtil.info(CLASS_NAME, "no decision selected yet on tab " + decisionTabId);
                return;
            }

            String newStatus;
            switch (decision.toUpperCase()) {
                case "APPROVE":      newStatus = "APPROVED";    break;
                case "REJECT":       newStatus = "REJECTED";    break;
                case "REQUEST_INFO": newStatus = "NEEDS_INFO";  break;
                default:
                    LogUtil.warn(CLASS_NAME, "unknown decision value '" + decision
                            + "' — skipping.");
                    return;
            }

            FormRow application = dao.load(F_APPLICATION, T_APPLICATION, wrapperId);
            if (application == null) {
                LogUtil.warn(CLASS_NAME, "application " + wrapperId + " not found — skipping.");
                return;
            }
            String oldStatus = application.getProperty("status");

            // Idempotency: if status is already at the target, just refresh
            // audit (cheap) and skip status update + entitlement.
            boolean alreadyAtTarget = newStatus.equals(oldStatus);

            if (!alreadyAtTarget) {
                // Joget-native write through the form's storeBinder. Requires
                // that `spApplication` declares a `status` field (the wrapper
                // has a HiddenField for it). If the field is missing, the
                // Hibernate mapping won't include it and the property is
                // silently dropped — same trap CLAUDE.md documents for
                // `national_id`.
                application.setProperty("status", newStatus);
                FormRowSet batch = new FormRowSet();
                batch.add(application);
                dao.saveOrUpdate(F_APPLICATION, T_APPLICATION, batch);
                LogUtil.info(CLASS_NAME, "[" + Build.STAMP + "] application "
                        + wrapperId + " status: " + oldStatus + " -> " + newStatus);
            }

            String actor = currentUsername();

            new AuditLogger(dao).logDecision(wrapperId, oldStatus, newStatus, actor,
                    "[" + decision + "] " + nz(reason)
                    + (signoff != null && !signoff.isEmpty() ? " (signed: " + signoff + ")" : ""));

            if ("APPROVED".equals(newStatus) && !alreadyAtTarget) {
                String code = new EntitlementGenerator(dao).generate(wrapperId, actor);
                if (code != null) {
                    LogUtil.info(CLASS_NAME, "entitlement " + code
                            + " issued for application " + wrapperId);
                }
            }
        } catch (Throwable t) {
            // Belt-and-braces — never block the user's save because of a decision bug.
            LogUtil.error(CLASS_NAME, t,
                    "DecisionRunner failed for tab " + decisionTabId + ": " + t.getMessage());
        }
    }

    private static String currentUsername() {
        try {
            String n = WorkflowUtil.getCurrentUsername();
            if (n != null && !n.isEmpty()) return n;
        } catch (Throwable ignore) {}
        return "system";
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
