package global.govstack.concatfield;

import global.govstack.concatfield.element.ConcatFieldElement;
import global.govstack.concatfield.element.ConcatFieldResources;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for Joget Concatenation Field Plugin.
 *
 * Registers:
 * - ConcatFieldResources: Static file serving for JS/CSS assets
 * - ConcatFieldElement: Form element for field concatenation
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        // Register the Resources plugin (serves static files)
        registrationList.add(context.registerService(
            ConcatFieldResources.class.getName(),
            new ConcatFieldResources(),
            null
        ));

        // Register the Concatenation Field Form Element
        registrationList.add(context.registerService(
            ConcatFieldElement.class.getName(),
            new ConcatFieldElement(),
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
