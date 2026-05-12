package global.govstack.regbb.publisher;

import global.govstack.regbb.publisher.menu.PublishUserviewMenu;

import org.joget.commons.util.LogUtil;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for the reg-bb-publisher plugin.
 *
 * <p>Phase 1 Week 1 scaffolding: registers no Joget plugins yet — just logs
 * the build stamp at startup.
 *
 * <p>Phase 1 milestones for this Activator:
 * <ul>
 *   <li>Week 1: scaffolding (this).
 *   <li>Week 5: register {@code PublishMenuAction} (custom userview menu),
 *       {@code ServiceValidator} (validation logic), and the cache-invalidate
 *       hook into {@code DeterminantEvaluator} (per spec §5.1).
 *   <li>Week 6: register the citizen userview generation hook (D17 citizen surface).
 *   <li>Week 8: register the operator userview generation hook + applications-list
 *       datalist generation (D17 operator surface).
 * </ul>
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<>();

        LogUtil.info(Activator.class.getName(),
                "reg-bb-publisher starting — " + Build.STAMP);

        // Phase 1 Week 1: register PublishUserviewMenu so the bundle appears
        // in Manage Plugins with its build stamp visible. The menu is a
        // scaffolding stub; Week 5 wires the validation + publish flow.
        registrationList.add(context.registerService(
                PublishUserviewMenu.class.getName(),
                new PublishUserviewMenu(),
                null));

        LogUtil.info(Activator.class.getName(),
                "reg-bb-publisher registered PublishUserviewMenu (stub) — " + Build.STAMP);
    }

    @Override
    public void stop(BundleContext context) {
        if (registrationList != null) {
            for (ServiceRegistration registration : registrationList) {
                registration.unregister();
            }
        }
        LogUtil.info(Activator.class.getName(),
                "reg-bb-publisher stopping — " + Build.STAMP);
    }
}
