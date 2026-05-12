package global.govstack.formcreator;

import global.govstack.formcreator.lib.FormCreatorServiceProvider;
import org.joget.commons.util.LogUtil;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for Form Creator API Plugin.
 *
 * Registers the FormCreatorServiceProvider as an API plugin when the bundle starts.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        // Register the Form Creator API plugin
        registrationList.add(context.registerService(
            FormCreatorServiceProvider.class.getName(),
            new FormCreatorServiceProvider(),
            null
        ));

        LogUtil.info(Activator.class.getName(),
            "form-creator-api started: " + Build.STAMP);
    }

    @Override
    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
