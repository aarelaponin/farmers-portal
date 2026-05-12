package global.govstack.identity;

import global.govstack.identity.element.IdentityResolverElement;

import org.joget.commons.util.LogUtil;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for the identity-resolver-runtime plugin.
 *
 * <p>Phase 2.2 (this build): registers no Joget plugins yet — just logs the
 * build stamp at startup so the live JAR's identity is visible.
 *
 * <p>Phase 2.3 will register {@code IdentityResolverElement} (Joget form
 * element). Phase 2.4 will register {@code IdentityResolverApi} (REST endpoint).
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<>();

        LogUtil.info(Activator.class.getName(),
                "identity-resolver-runtime starting — " + Build.STAMP);

        // Form element + REST endpoint in one (PluginWebSupport-implementing element).
        // The REST endpoint is exposed by Joget at:
        //   /jw/web/json/plugin/global.govstack.identity.element.IdentityResolverElement/service
        registrationList.add(context.registerService(
                IdentityResolverElement.class.getName(),
                new IdentityResolverElement(),
                null));
    }

    @Override
    public void stop(BundleContext context) {
        if (registrationList != null) {
            for (ServiceRegistration registration : registrationList) {
                registration.unregister();
            }
        }
    }
}
