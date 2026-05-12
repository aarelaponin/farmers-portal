package org.joget.marketplace;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for Embedded Datalist Form Element Plugin.
 *
 * Registers:
 * - EmbeddedDatalist: Form element that displays a read-only datalist inline within a form
 *
 * Use Case: Display related records (e.g., farmer's land parcels) in a parent form tab,
 * solving the limitation where ListGrid + JDBC Datalist Binder don't work together.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        // Register the Embedded Datalist Form Element
        registrationList.add(context.registerService(
            EmbeddedDatalist.class.getName(),
            new EmbeddedDatalist(),
            null
        ));
    }

    @Override
    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
