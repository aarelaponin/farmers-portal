package global.govstack.regbb.engine.notification;

import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;

/**
 * Operator-facing DataListAction: bulk-mark FAILED notifications as
 * DEAD_LETTER (terminal — operator gives up on retrying).
 *
 * <p>Use when an operator decides a batch of failed sends are not worth
 * retrying (e.g., the SMTP backend was misconfigured for an hour and the
 * underlying applications were already manually contacted by phone).
 *
 * <p>Per CLAUDE.md, registered in {@link global.govstack.regbb.engine.Activator}.
 */
public class MarkDeadLetterAction extends DataListActionDefault {

    private static final String CLASS_NAME = MarkDeadLetterAction.class.getName();

    @Override public String getName()        { return "Mark Dead-Letter"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getDescription() { return "Mark selected FAILED notifications as DEAD_LETTER — operator gives up on retrying"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getLabel()       { return "Mark Dead-Letter"; }

    @Override
    public String getLinkLabel() {
        return getPropertyString("label") != null && !getPropertyString("label").isEmpty()
                ? getPropertyString("label") : "Mark Dead-Letter";
    }

    @Override public String getHref()        { return getPropertyString("href"); }
    @Override public String getTarget()      { return "post"; }
    @Override public String getHrefParam()   { return getPropertyString("hrefParam"); }
    @Override public String getHrefColumn()  { return getPropertyString("hrefColumn"); }
    @Override public String getConfirmation(){
        return "Mark selected notifications as DEAD_LETTER? This is terminal — no further automatic retries. Use only when manual contact has been made out-of-band.";
    }

    @Override
    public String getPropertyOptions() {
        return "[ { \"title\":\"Mark Dead-Letter\", \"properties\":[ "
             + "{ \"name\":\"label\", \"label\":\"Button Label\", \"type\":\"textfield\", \"value\":\"Mark Dead-Letter\" } "
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

        int marked = 0, skipped = 0;
        for (String id : rowKeys) {
            try {
                NotifStatus cur = NotifAudit.currentStatus(id);
                if (cur == NotifStatus.FAILED) {
                    NotifAudit.markDeadLetter(id, "operator " + actor + " marked dead");
                    marked++;
                } else {
                    LogUtil.info(CLASS_NAME, "Skipped " + id
                            + " — current status " + (cur == null ? "null" : cur.getCode())
                            + " is not FAILED");
                    skipped++;
                }
            } catch (Throwable t) {
                LogUtil.error(CLASS_NAME, t, "Mark dead-letter failed for " + id);
                skipped++;
            }
        }
        DataListActionResult r = new DataListActionResult();
        r.setMessage("Marked: " + marked + " moved to DEAD_LETTER, " + skipped + " skipped (not in FAILED state).");
        r.setType(DataListActionResult.TYPE_REDIRECT);
        r.setUrl("REFERER");
        return r;
    }
}
