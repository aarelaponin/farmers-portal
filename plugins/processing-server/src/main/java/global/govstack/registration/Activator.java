package global.govstack.registration;

import java.util.ArrayList;
import java.util.Collection;

import global.govstack.registration.receiver.lib.RegistrationServiceProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * OSGi Bundle Activator for GovStack Processing Server Plugin
 *
 * Registers service providers when the bundle starts and unregisters them when it stops.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    /**
     * Called when the OSGi bundle starts.
     * Registers all service provider plugins.
     *
     * @param context The bundle context
     */
    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        // Register the Registration Service Provider (farmer registration)
        registrationList.add(context.registerService(
                RegistrationServiceProvider.class.getName(),
                new RegistrationServiceProvider(),
                null
        ));
    }

    /**
     * Called when the OSGi bundle stops.
     * Unregisters all service providers.
     *
     * @param context The bundle context
     */
    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}