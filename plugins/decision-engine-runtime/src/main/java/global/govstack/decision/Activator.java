package global.govstack.decision;

import global.govstack.decision.binder.DecisionStoreBinder;
import global.govstack.decision.hook.DecisionDispatchTool;

import org.joget.commons.util.LogUtil;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for the decision-engine-runtime plugin.
 *
 * <p>Phase 4.1 (this build): registers {@link DecisionDispatchTool} — a
 * single post-processor wired on the {@code spApplicationDecision} tab
 * subform that dispatches to Approve / Reject / Request-info logic based
 * on the selected decision. Approve creates the {@code imEntitlement}
 * record + items; Reject / Request-info update the application status and
 * write to {@code app_fd_audit_log}.
 *
 * <p>A separate Snapshot tool (Phase 4.2) and XPDL-based workflow process
 * (Phase 4.3 / Block 5) are deferred — they aren't needed for the demo
 * loop a farmer applies → operator decides → entitlement issued.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<>();

        LogUtil.info(Activator.class.getName(),
                "decision-engine-runtime starting — " + Build.STAMP);

        registrationList.add(context.registerService(
                DecisionDispatchTool.class.getName(),
                new DecisionDispatchTool(),
                null));
        registrationList.add(context.registerService(
                DecisionStoreBinder.class.getName(),
                new DecisionStoreBinder(),
                null));
    }

    @Override
    public void stop(BundleContext context) {
        if (registrationList != null) {
            for (ServiceRegistration registration : registrationList) {
                registration.unregister();
            }
        }
    }
}
