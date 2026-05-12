package global.govstack.subsidy;

import java.util.ArrayList;
import java.util.Collection;

import global.govstack.subsidy.lib.EligibilityRuntime;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * OSGi Bundle Activator for the EligibilityRuntime plugin.
 *
 * Registers EligibilityRuntime as a Joget ApplicationPlugin (workflow tool plugin).
 * The runtime calls the joget-rules-api REST endpoints to load + compile rulesets
 * for one applicant; no compile-time coupling to that bundle is required.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<>();

        registrationList.add(context.registerService(
                EligibilityRuntime.class.getName(),
                new EligibilityRuntime(),
                null));
    }

    @Override
    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
