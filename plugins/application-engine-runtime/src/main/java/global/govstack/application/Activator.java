package global.govstack.application;

import global.govstack.application.binder.SeedingTabLoadBinder;
import global.govstack.application.hook.ApplicationSeedingTool;

import org.joget.commons.util.LogUtil;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for the application-engine-runtime plugin.
 *
 * <p>Phase 3.2 (this build): registers {@link ApplicationSeedingTool} — the
 * post-processor wired on {@code spApplication} that seeds the application's
 * eligibility / benefit child rows from the chosen programme's spec.
 *
 * <p>Phase 3.3 will register {@code SnapshotTransitionTool}.
 * Phase 4 will register decision tools (separate plugin).
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<>();

        LogUtil.info(Activator.class.getName(),
                "application-engine-runtime starting — " + Build.STAMP);

        registrationList.add(context.registerService(
                ApplicationSeedingTool.class.getName(),
                new ApplicationSeedingTool(),
                null));
        registrationList.add(context.registerService(
                SeedingTabLoadBinder.class.getName(),
                new SeedingTabLoadBinder(),
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
