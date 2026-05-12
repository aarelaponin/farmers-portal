package global.govstack.regbb.engine.budget;

import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import java.util.Map;

/**
 * ADR-030 Step 5 — refresh the budget projection materialised views on a
 * schedule, decoupled from the dispatch hot path.
 *
 * <p>Before ADR-030: {@link BudgetEngine#dispatch} and
 * {@link BudgetEngine#dispatchDirect} called {@code refreshProjection()}
 * inline at the end of every event posting. With dozens of events per
 * application submission, that turned each submit into a multi-second
 * operation and made concurrent submits queue.
 *
 * <p>After: dispatch returns as soon as the journal lines are written.
 * This job runs every 30 seconds (configurable in the workflow process
 * tool step) and refreshes both materialised views with
 * {@code CONCURRENTLY} so dashboards never see a window of unavailability.
 *
 * <p>Trade-off: dashboards see at most ~30s of lag between event posting
 * and projection update. The operator decision form's budget-hint widget
 * mitigates this for its specific envelope by triggering a per-envelope
 * refresh on render (cheap — single envelope, ~50ms).
 *
 * <p>Wire as a tool step in any Joget workflow process and schedule the
 * process every 30 seconds. Or invoke on demand via
 * {@code POST /budget/run-projection-refresh} (sibling endpoint added in
 * Step 5).
 */
public class BudgetProjectionRefreshJob extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = BudgetProjectionRefreshJob.class.getName();

    @Override public String getName()        { return "Budget Projection Refresh Job"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getLabel()       { return "Budget Projection Refresh Job"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getDescription() { return "ADR-030 Step 5 — refreshes budget_projection + budget_projection_by_source materialised views (CONCURRENTLY) on a schedule."; }
    @Override public String getPropertyOptions() {
        return "[ { \"title\":\"Projection Refresh\", \"properties\":[] } ]";
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object execute(Map properties) {
        long started = System.currentTimeMillis();
        try {
            new BudgetEngine().refreshProjection();
            long elapsed = System.currentTimeMillis() - started;
            String summary = "BudgetProjectionRefreshJob: refreshed in " + elapsed + "ms";
            LogUtil.info(CLASS_NAME, summary);
            return summary;
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "BudgetProjectionRefreshJob failed");
            return "BudgetProjectionRefreshJob: ERROR " + t.getClass().getSimpleName()
                    + ":" + (t.getMessage() == null ? "" : t.getMessage());
        }
    }
}
