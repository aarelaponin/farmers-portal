package global.govstack.farmer;

import java.util.ArrayList;
import java.util.Collection;

import global.govstack.farmer.lib.FarmerDerivedRefresh;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * OSGi Bundle Activator for the FarmerDerivedRefresh plugin.
 *
 * Registers FarmerDerivedRefresh as a Joget ApplicationPlugin (workflow tool plugin).
 * Pattern follows the gs-plugins house style: collect ServiceRegistrations on
 * start(), unregister all on stop().
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<>();

        // Register the workflow tool plugin
        registrationList.add(context.registerService(
                FarmerDerivedRefresh.class.getName(),
                new FarmerDerivedRefresh(),
                null));
    }

    @Override
    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
