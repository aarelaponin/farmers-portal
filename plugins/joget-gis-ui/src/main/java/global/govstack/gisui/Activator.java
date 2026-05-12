package global.govstack.gisui;

import global.govstack.gisui.element.GisPolygonCaptureElement;
import global.govstack.gisui.element.GisResourcesPlugin;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for Joget GIS UI Plugin.
 *
 * Registers:
 * - GisResourcesPlugin: Static file serving for Leaflet.js and GIS assets
 * - GisPolygonCaptureElement: Form element for polygon capture (Walk/Draw modes)
 *
 * Note: Backend API services are provided by the separate joget-gis-api plugin.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        // Register the GIS Resources plugin (serves static files)
        registrationList.add(context.registerService(
            GisResourcesPlugin.class.getName(),
            new GisResourcesPlugin(),
            null
        ));

        // Register the GIS Polygon Capture Form Element
        registrationList.add(context.registerService(
            GisPolygonCaptureElement.class.getName(),
            new GisPolygonCaptureElement(),
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
