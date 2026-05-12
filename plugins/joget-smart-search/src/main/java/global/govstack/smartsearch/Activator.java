package global.govstack.smartsearch;

import global.govstack.smartsearch.api.SmartSearchApiPlugin;
import global.govstack.smartsearch.element.SmartSearchElement;
import global.govstack.smartsearch.element.SmartSearchResources;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for Smart Farmer Search Plugin.
 *
 * Registers:
 * - SmartSearchResources: Static file serving for CSS/JS
 * - SmartSearchElement: Form element for farmer search
 * - SmartSearchApiPlugin: REST API endpoints for search
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        // Register the Smart Search Resources plugin (serves static files)
        registrationList.add(context.registerService(
            SmartSearchResources.class.getName(),
            new SmartSearchResources(),
            null
        ));

        // Register the Smart Search Form Element
        registrationList.add(context.registerService(
            SmartSearchElement.class.getName(),
            new SmartSearchElement(),
            null
        ));
        
        // Register the Smart Search API Plugin
        registrationList.add(context.registerService(
            SmartSearchApiPlugin.class.getName(),
            new SmartSearchApiPlugin(),
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
