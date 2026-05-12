package global.govstack.ruleeditor;

import global.govstack.ruleeditor.element.RuleEditorElement;
import global.govstack.ruleeditor.element.RuleEditorResources;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for Joget Rule Editor Plugin.
 *
 * Registers:
 * - RuleEditorResources: Static file serving for CodeMirror
 * - RuleEditorElement: Form element for Rules Script editing
 *
 * Note: API endpoints are provided by the separate joget-rules-api plugin.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        // Register the Rule Editor Resources plugin (serves static files)
        registrationList.add(context.registerService(
            RuleEditorResources.class.getName(),
            new RuleEditorResources(),
            null
        ));

        // Register the Rule Editor Form Element
        registrationList.add(context.registerService(
            RuleEditorElement.class.getName(),
            new RuleEditorElement(),
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
