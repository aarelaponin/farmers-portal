package com.fiscaladmin.gam;

import java.util.ArrayList;
import java.util.Collection;

import com.fiscaladmin.gam.appdefinitionprovider.lib.AppDefinitionProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * OSGi Bundle Activator for AppDefinitionProvider Plugin
 *
 * Registers the AppDefinitionProvider API plugin when the bundle starts
 * and unregisters it when the bundle stops.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    /**
     * Called when the OSGi bundle starts.
     * Registers the AppDefinitionProvider plugin.
     *
     * @param context The bundle context
     */
    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        // Register the App Definition Provider (export API)
        registrationList.add(context.registerService(
                AppDefinitionProvider.class.getName(),
                new AppDefinitionProvider(),
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