package global.govstack.decision.binder;

import global.govstack.decision.Build;
import global.govstack.decision.service.DecisionRunner;

import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

/**
 * Wired as the {@code storeBinder} on {@code spApplicationDecision}. Why a
 * store-side hook instead of a postProcessor? Joget's {@code MultiPagedForm}
 * partial-store path calls {@code storeFormData} (which DOES invoke the
 * storeBinder) but skips {@code processFormSubmission} (which would have
 * fired the postProcessor). So a custom store binder is the only place we
 * can reliably catch a tab save inside a wizard.
 *
 * <p>Behaviour: delegate to the standard {@link WorkflowFormBinder} for the
 * actual DB write, then call {@link DecisionRunner} on the just-saved row's
 * id to update the wrapper status, write audit, and issue entitlement on
 * APPROVE.
 */
public class DecisionStoreBinder extends WorkflowFormBinder {

    private static final String CLASS_NAME = DecisionStoreBinder.class.getName();

    @Override
    public String getName()        { return "Decision Store Binder"; }
    @Override
    public String getDescription() {
        return "Persists the operator's decision and dispatches the runner "
             + "(audit + status + entitlement on Approve). [" + Build.STAMP + "]";
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
    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
        FormRowSet result = super.store(element, rows, formData);
        try {
            // Decision tab is single-row per application; pick the row's id.
            if (result != null && !result.isEmpty()) {
                String tabId = null;
                FormRow row = result.get(0);
                if (row != null) tabId = row.getId();
                if (tabId == null || tabId.isEmpty()) {
                    // Fall back to the input rowset.
                    if (rows != null && !rows.isEmpty()) tabId = rows.get(0).getId();
                }
                LogUtil.info(CLASS_NAME, "[" + Build.STAMP + "] store fired tabId=" + tabId);
                new DecisionRunner().run(tabId);
            }
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t,
                    "DecisionStoreBinder hook failed: " + t.getMessage());
        }
        return result;
    }
}
