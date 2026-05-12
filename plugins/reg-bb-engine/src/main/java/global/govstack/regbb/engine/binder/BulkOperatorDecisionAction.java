package global.govstack.regbb.engine.binder;

import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import global.govstack.regbb.engine.audit.AuditWriter;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Operator-side bulk action for the application inbox.
 *
 * <p>Wired as a list-level action on {@code dl_subsidy_app_operator_2025}
 * (or whichever datalist is the operator inbox). Operators tick rows in
 * the list, click "Approve Selected" or "Reject Selected", and this
 * action processes each selected application through the same lifecycle
 * the per-application decision form uses:
 *
 * <ol>
 *   <li>For each selected application id:</li>
 *   <li>  Read the current row.</li>
 *   <li>  If status is not {@code pending_review} (or already terminal),
 *         skip with a note in the result summary.</li>
 *   <li>  Patch the row with the new status, decision, decision_comment,
 *         decided_at, decided_by.</li>
 *   <li>  Write a {@code reg_bb_eval_audit} row tagged
 *         {@code OPERATOR_DECISION:&lt;decision&gt;}.</li>
 *   <li>  Fire {@code BudgetEngine.fireForLifecycle} for the new status
 *         (PRE_COMMITMENT on approve, RELEASE_RESERVATION on reject).</li>
 * </ol>
 *
 * <p>Mirrors {@link RegBbOperatorDecisionBinder#transitionStatus} so the
 * per-row outcome is identical to opening each application individually
 * and clicking the decision button.
 *
 * <p>Configuration properties (set on the datalist's action JSON):
 * <ul>
 *   <li>{@code decision} — {@code approve} or {@code reject} (required)</li>
 *   <li>{@code reason} — default reason text written into
 *       {@code decision_comment}; operator may rely on this or supply a
 *       custom value via the confirmation dialog (future enhancement)</li>
 *   <li>{@code label} — button label override</li>
 * </ul>
 *
 * <p>Skipped applications: rows whose current status is not
 * {@code pending_review} are not processed but counted in the summary
 * so the operator knows what was actually changed. Statuses that
 * legitimately stay in the inbox view but aren't decision-ready
 * (already auto_approved, already auto_rejected) are gracefully skipped.
 *
 * <p>Failures: per-row exceptions are logged but don't abort the batch.
 * The summary distinguishes processed / skipped / failed.
 */
public class BulkOperatorDecisionAction extends DataListActionDefault {

    private static final String CLASS_NAME = BulkOperatorDecisionAction.class.getName();
    private static final SimpleDateFormat ISO_DATE_TIME =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static { ISO_DATE_TIME.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private static final String FORM_DEF_ID  = "subsidyApplication2025";
    private static final String TABLE_NAME   = "subsidy_app_2025";

    @Override public String getName()        { return "Bulk Operator Decision"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getDescription() { return "Bulk approve or reject pending applications from the inbox (B2)."; }
    @Override public String getLabel()       { return "Bulk Operator Decision"; }
    @Override public String getClassName()   { return getClass().getName(); }

    @Override
    public String getPropertyOptions() {
        return "[ { \"title\":\"Bulk Decision\", \"properties\":["
             + "  {\"name\":\"decision\",\"label\":\"Decision\",\"type\":\"selectbox\","
             + "   \"options\":[{\"value\":\"approve\",\"label\":\"Approve\"},"
             + "                {\"value\":\"reject\",\"label\":\"Reject\"}],"
             + "   \"required\":\"true\"},"
             + "  {\"name\":\"label\",\"label\":\"Button label\",\"type\":\"textfield\","
             + "   \"value\":\"Process Selected\"},"
             + "  {\"name\":\"reason\",\"label\":\"Default reason (decision_comment)\","
             + "   \"type\":\"textfield\","
             + "   \"value\":\"Bulk operator decision\"},"
             + "  {\"name\":\"confirmation\",\"label\":\"Confirmation message\","
             + "   \"type\":\"textfield\","
             + "   \"value\":\"Process the selected applications?\"}"
             + "]} ]";
    }

    @Override
    public String getLinkLabel() {
        String label = getPropertyString("label");
        return (label == null || label.isEmpty()) ? "Process Selected" : label;
    }

    @Override public String getHref()           { return null; }
    @Override public String getTarget()         { return "post"; }
    @Override public String getHrefParam()      { return null; }
    @Override public String getHrefColumn()     { return null; }

    @Override
    public String getConfirmation() {
        String c = getPropertyString("confirmation");
        return (c == null || c.isEmpty())
                ? "Process the selected applications?"
                : c;
    }

    /** This is a list-action only; not a per-row or per-column button. */
    @Override public Boolean supportRow()    { return false; }
    @Override public Boolean supportColumn() { return false; }
    @Override public Boolean supportList()   { return true; }

    @Override
    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        DataListActionResult result = new DataListActionResult();
        result.setType(DataListActionResult.TYPE_REDIRECT);
        result.setUrl("REFERER");  // refresh the datalist

        if (rowKeys == null || rowKeys.length == 0) {
            result.setMessage("No rows selected.");
            return result;
        }

        String decision = getPropertyString("decision");
        if (decision == null || decision.isEmpty()) {
            result.setMessage("Configuration error: decision property not set.");
            return result;
        }
        decision = decision.trim().toLowerCase();
        String newStatus = RegBbOperatorDecisionBinder.statusForDecision(decision, "SUBSIDY_2025", null);
        if (newStatus == null) {
            result.setMessage("Configuration error: unknown decision '" + decision + "'.");
            return result;
        }

        String reason = getPropertyString("reason");
        if (reason == null) reason = "";
        String actor = RegBbOperatorDecisionBinder.currentUsername();
        if (actor == null || actor.isEmpty()) actor = "operator";

        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

        int processed = 0;
        int skippedNotPending = 0;
        int failed = 0;

        for (String appId : rowKeys) {
            if (appId == null || appId.isEmpty()) continue;
            try {
                FormRow row = dao.load(FORM_DEF_ID, TABLE_NAME, appId);
                if (row == null) {
                    LogUtil.warn(CLASS_NAME, "appId=" + appId + ": row not found, skipped");
                    failed++;
                    continue;
                }
                String currentStatus = nz(row.getProperty("status"));
                if (!"pending_review".equalsIgnoreCase(currentStatus)
                        && !"pending_data_clarification".equalsIgnoreCase(currentStatus)
                        && !"pending_operator_review".equalsIgnoreCase(currentStatus)) {
                    skippedNotPending++;
                    LogUtil.info(CLASS_NAME, "appId=" + appId + ": status='" + currentStatus
                            + "' is not a pending state, skipped");
                    continue;
                }

                String registrationCode = nz(row.getProperty("applied_programme"));
                String now = ISO_DATE_TIME.format(new Date());

                // Build patch — only the fields we mutate. Joget's UPDATE will
                // leave everything else intact (it only updates fields present
                // in the rowSet).
                FormRow patch = new FormRow();
                patch.setId(appId);
                patch.setProperty("status",          newStatus);
                patch.setProperty("decision",        decision);
                patch.setProperty("decision_comment", reason);
                patch.setProperty("decided_at",      now);
                patch.setProperty("decided_by",      actor);
                FormRowSet rs = new FormRowSet();
                rs.add(patch);
                global.govstack.regbb.engine.support.RowWriter.save(FORM_DEF_ID, TABLE_NAME, rs);

                // Audit (mirror RegBbOperatorDecisionBinder.writeDecisionAudit)
                try {
                    EvalResult fakeResult = new EvalResult(EvalResult.Outcome.TRUE,
                            null, newStatus, "operator-bulk", null);
                    Map<String, Object> data = new HashMap<>();
                    for (Object k : row.keySet()) data.put(k.toString(), row.get(k));
                    data.put("decision", decision);
                    data.put("decision_comment", reason);
                    EvalContext ctx = EvalContext.builder()
                            .applicationId(appId)
                            .data(data)
                            .currentUsername(actor)
                            .build();
                    long t = System.currentTimeMillis();
                    AuditWriter.write("OPERATOR_DECISION:" + decision + ":bulk",
                            ctx, fakeResult, "n/a — bulk operator decision", t, t);
                } catch (Throwable auditEx) {
                    LogUtil.warn(CLASS_NAME, "audit write failed for " + appId
                            + ": " + auditEx.getMessage());
                }

                // Budget lifecycle (mirror RegBbOperatorDecisionBinder)
                try {
                    Map<String, Object> appData = new HashMap<>();
                    for (Object k : row.keySet()) appData.put(k.toString(), row.get(k));
                    global.govstack.regbb.engine.budget.BudgetEngine.fireForLifecycle(
                            appId, registrationCode, newStatus, appData, actor);
                } catch (Throwable budgetEx) {
                    LogUtil.warn(CLASS_NAME, "budget lifecycle dispatch failed for "
                            + appId + ": " + budgetEx.getMessage());
                }

                processed++;
                LogUtil.info(CLASS_NAME, "appId=" + appId + " bulk-" + decision
                        + " by " + actor + " → status=" + newStatus);
            } catch (Throwable t) {
                failed++;
                LogUtil.error(CLASS_NAME, t, "Bulk decision failed for appId=" + appId);
            }
        }

        StringBuilder msg = new StringBuilder();
        msg.append("Bulk ").append(decision).append(": ");
        msg.append(processed).append(" processed");
        if (skippedNotPending > 0) msg.append(", ").append(skippedNotPending).append(" skipped (not pending)");
        if (failed > 0)            msg.append(", ").append(failed).append(" failed");
        msg.append(".");
        result.setMessage(msg.toString());
        LogUtil.info(CLASS_NAME, msg.toString() + " (rowKeys=" + rowKeys.length + ")");
        return result;
    }

    private static String nz(Object o) { return o == null ? "" : String.valueOf(o); }
}
