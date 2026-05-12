package global.govstack.regbb.engine.binder;

import global.govstack.regbb.engine.lifecycle.AppAudit;
import global.govstack.regbb.engine.lifecycle.AppLifecycleStatus;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;

/**
 * Load binder for the operator-side application review form
 * ({@code subsidyApplicationOperator2025}). Delegates the row load to
 * {@link WorkflowFormBinder} (the default), then fires a one-shot
 * SUBMITTED → UNDER_REVIEW transition the first time an operator
 * opens an application — captures "this operator just started reviewing
 * this application" as an auditable event.
 *
 * <p>Per W3.1 Phase 2 (May 2026). The transition is idempotent:
 * {@link AppAudit#transition} silently no-ops if the row is already in
 * UNDER_REVIEW or further along (the state-machine rejects same-state
 * and backwards transitions). So re-opening a row that's already been
 * reviewed doesn't write spurious audit rows.
 *
 * <p>Three guards: (1) primaryKeyValue must be non-empty (skip add-mode
 * loads), (2) current state must be SUBMITTED (skip already-progressed
 * rows), (3) wraps the transition in a try/catch so an audit-layer
 * failure never breaks the form render.
 */
public class ApplicationOpenLoadBinder extends WorkflowFormBinder {

    private static final String CLASS_NAME = ApplicationOpenLoadBinder.class.getName();

    @Override public String getName()        { return "RegBB Application Open Load Binder"; }
    @Override public String getVersion()     { return "1.0.0"; }
    @Override public String getLabel()       { return "RegBB Application Open Load Binder"; }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getDescription() { return "Loads the application row and transitions lifecycle SUBMITTED → UNDER_REVIEW on first operator open."; }

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        FormRowSet rs = super.load(element, primaryKey, formData);

        try {
            if (primaryKey != null && !primaryKey.isEmpty()) {
                AppLifecycleStatus current = AppAudit.currentState(primaryKey);
                if (current == AppLifecycleStatus.SUBMITTED) {
                    String actor = "operator:" + currentUsername();
                    AppAudit.transition(primaryKey, AppLifecycleStatus.UNDER_REVIEW,
                            actor, "operator opened application for review");
                    LogUtil.info(CLASS_NAME, "Lifecycle: " + primaryKey
                            + " SUBMITTED → UNDER_REVIEW (" + actor + ")");
                }
            }
        } catch (Throwable t) {
            // Audit failures must never block form render.
            LogUtil.warn(CLASS_NAME, "UNDER_REVIEW transition failed for "
                    + primaryKey + ": " + t.getClass().getSimpleName() + ":" + t.getMessage());
        }

        return rs;
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
