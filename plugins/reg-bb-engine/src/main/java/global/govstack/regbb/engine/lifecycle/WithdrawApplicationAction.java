package global.govstack.regbb.engine.lifecycle;

import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;

/**
 * Citizen-facing DataListAction on {@code list_my_applications}.
 * Lets the applicant cancel one of their own applications — transitions
 * the application's lifecycle to {@link AppLifecycleStatus#WITHDRAWN}.
 *
 * <p>Allowed from any non-terminal lifecycle state (DRAFT, SUBMITTED,
 * UNDER_REVIEW, PENDING_REVIEW) per the transition map registered in
 * {@link global.govstack.regbb.engine.Activator}. The action calls
 * {@link AppAudit#transition} which goes through
 * {@link global.govstack.statusframework.core.StatusFramework} — each
 * withdrawal writes one row to {@code app_fd_audit_log} with the
 * triggering citizen identified in the {@code triggered_by} column.
 *
 * <p>Per CLAUDE.md "Every plugin class needs an Activator registration":
 * this class is registered in {@link global.govstack.regbb.engine.Activator}.
 * The {@code list_my_applications} datalist references it by class name
 * in its {@code actions} array.
 */
public class WithdrawApplicationAction extends DataListActionDefault {

    private static final String CLASS_NAME = WithdrawApplicationAction.class.getName();

    @Override public String getName()        { return "Withdraw Application"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getDescription() { return "Citizen-initiated application withdrawal; transitions lifecycle to WITHDRAWN"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getLabel()       { return "Withdraw Application"; }

    @Override
    public String getLinkLabel() {
        String pLabel = getPropertyString("label");
        return (pLabel != null && !pLabel.isEmpty()) ? pLabel : "Withdraw";
    }

    @Override public String getHref()        { return getPropertyString("href"); }
    @Override public String getTarget()      { return "post"; }
    @Override public String getHrefParam()   { return getPropertyString("hrefParam"); }
    @Override public String getHrefColumn()  { return getPropertyString("hrefColumn"); }
    @Override public String getConfirmation(){
        return "Withdraw the selected application(s)? This is final — withdrawn applications cannot be re-opened. If you need to apply again, you'll have to start a new application from scratch.";
    }

    @Override
    public String getPropertyOptions() {
        return "[ { \"title\":\"Withdraw Application\", \"properties\":[ "
             + "{ \"name\":\"label\", \"label\":\"Button Label\", \"type\":\"textfield\", \"value\":\"Withdraw\" } "
             + "] } ]";
    }

    @Override
    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        if (rowKeys == null || rowKeys.length == 0) {
            DataListActionResult r = new DataListActionResult();
            r.setMessage("No applications selected.");
            r.setType(DataListActionResult.TYPE_REDIRECT);
            return r;
        }

        String triggeredBy = "citizen:" + currentUsername();
        int ok = 0, skipped = 0;
        StringBuilder log = new StringBuilder();
        for (String rowKey : rowKeys) {
            if (rowKey == null || rowKey.isEmpty()) continue;
            try {
                AppLifecycleStatus cur = AppAudit.currentState(rowKey);
                if (cur == AppLifecycleStatus.WITHDRAWN
                 || cur == AppLifecycleStatus.APPROVED
                 || cur == AppLifecycleStatus.REJECTED) {
                    // Terminal — can't withdraw something that's already
                    // approved, rejected, or already withdrawn.
                    skipped++;
                    log.append(rowKey.substring(0, Math.min(8, rowKey.length())))
                       .append("=skipped(state=").append(cur).append("); ");
                    continue;
                }
                AppAudit.transition(rowKey, AppLifecycleStatus.WITHDRAWN, triggeredBy,
                        "citizen-initiated withdrawal");
                ok++;
            } catch (Throwable t) {
                LogUtil.warn(CLASS_NAME, "withdraw failed for " + rowKey + ": "
                        + t.getClass().getSimpleName() + ":" + t.getMessage());
                skipped++;
            }
        }

        LogUtil.info(CLASS_NAME, "Withdraw: " + ok + " withdrawn, " + skipped
                + " skipped by " + triggeredBy + " — " + log);

        DataListActionResult r = new DataListActionResult();
        r.setMessage("Withdrew " + ok + " application(s)"
                + (skipped > 0 ? " (skipped " + skipped + " — already in a terminal state)" : "")
                + ".");
        r.setType(DataListActionResult.TYPE_REDIRECT);
        r.setUrl("REFERER");
        return r;
    }

    private static String currentUsername() {
        try {
            String u = WorkflowUtil.getCurrentUsername();
            return (u == null || u.isEmpty()) ? "anonymous" : u;
        } catch (Throwable ignore) {
            return "anonymous";
        }
    }
}
