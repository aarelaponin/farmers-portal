package global.govstack.regbb.engine.notification;

import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;

/**
 * Operator-facing DataListAction on {@code list_notification_queue}.
 * Bulk-transitions selected FAILED notifications to PENDING so the
 * {@link NotificationQueueWorker} picks them up on its next poll.
 *
 * <p>Per CLAUDE.md "Every plugin class needs an Activator registration":
 * this class is registered in {@link global.govstack.regbb.engine.Activator}.
 * The {@code list_notification_queue} datalist references it by class name
 * in its {@code actions} array.
 *
 * <p>Wired via {@link NotifAudit#markPendingRetry(String, String)} which
 * goes through {@link global.govstack.statusframework.core.StatusFramework} —
 * each retry writes one row to {@code app_fd_audit_log}.
 */
public class RetryNotificationAction extends DataListActionDefault {

    private static final String CLASS_NAME = RetryNotificationAction.class.getName();

    @Override public String getName()        { return "Retry Notification"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getDescription() { return "Bulk-flip FAILED notifications back to PENDING for retry by the queue worker"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getLabel()       { return "Retry Notification"; }

    @Override
    public String getLinkLabel() {
        return getPropertyString("label") != null && !getPropertyString("label").isEmpty()
                ? getPropertyString("label") : "Retry";
    }

    @Override public String getHref()        { return getPropertyString("href"); }
    @Override public String getTarget()      { return "post"; }
    @Override public String getHrefParam()   { return getPropertyString("hrefParam"); }
    @Override public String getHrefColumn()  { return getPropertyString("hrefColumn"); }
    @Override public String getConfirmation(){
        return "Re-queue the selected notifications? Each one transitions FAILED → PENDING and will be re-attempted by the queue worker on its next poll.";
    }

    @Override
    public String getPropertyOptions() {
        return "[ { \"title\":\"Retry Notification\", \"properties\":[ "
             + "{ \"name\":\"label\", \"label\":\"Button Label\", \"type\":\"textfield\", \"value\":\"Retry\" } "
             + "] } ]";
    }

    @Override
    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        if (rowKeys == null || rowKeys.length == 0) {
            DataListActionResult r = new DataListActionResult();
            r.setMessage("No rows selected.");
            r.setType(DataListActionResult.TYPE_REDIRECT);
            return r;
        }
        String actor = WorkflowUtil.getCurrentUsername();
        if (actor == null || actor.isEmpty()) actor = "unknown";

        int retried = 0, failed = 0;
        for (String id : rowKeys) {
            try {
                NotifStatus cur = NotifAudit.currentStatus(id);
                if (cur == NotifStatus.FAILED) {
                    NotifAudit.markPendingRetry(id, actor);
                    retried++;
                } else {
                    LogUtil.info(CLASS_NAME, "Skipped " + id
                            + " — current status " + (cur == null ? "null" : cur.getCode())
                            + " is not FAILED");
                    failed++;
                }
            } catch (Throwable t) {
                LogUtil.error(CLASS_NAME, t, "Retry failed for " + id);
                failed++;
            }
        }
        DataListActionResult r = new DataListActionResult();
        r.setMessage("Retry: " + retried + " re-queued, " + failed + " skipped (not in FAILED state).");
        r.setType(DataListActionResult.TYPE_REDIRECT);
        r.setUrl("REFERER");
        return r;
    }
}
