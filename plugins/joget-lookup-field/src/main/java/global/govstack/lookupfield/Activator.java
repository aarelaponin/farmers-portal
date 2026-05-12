package global.govstack.lookupfield;

import global.govstack.lookupfield.element.LookupFieldElement;
import global.govstack.lookupfield.element.LookupFieldResources;
import global.govstack.lookupfield.element.LookupFieldWebService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for Joget Lookup Field Plugin.
 *
 * Registers:
 * - LookupFieldResources: Static file serving for CSS assets
 * - LookupFieldElement: Form element for cross-form field lookup
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        // Register the Resources plugin (serves static files)
        registrationList.add(context.registerService(
            LookupFieldResources.class.getName(),
            new LookupFieldResources(),
            null
        ));

        // Register the Lookup Field Form Element
        registrationList.add(context.registerService(
            LookupFieldElement.class.getName(),
            new LookupFieldElement(),
            null
        ));

        // Register the Web Service (AJAX lookup endpoint)
        registrationList.add(context.registerService(
            LookupFieldWebService.class.getName(),
            new LookupFieldWebService(),
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
