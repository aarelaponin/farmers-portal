package global.govstack.formquality;

import global.govstack.formquality.element.QualityBannerElement;
import global.govstack.formquality.hook.FormQualityPostProcessor;
import global.govstack.formquality.status.QualityEntityType;
import global.govstack.formquality.status.QualityStatus;
import global.govstack.statusframework.api.Status;
import global.govstack.statusframework.core.StatusFramework;

import org.joget.commons.util.LogUtil;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.*;

/**
 * OSGi Bundle Activator for the form-quality-runtime plugin.
 * <p>
 * Two responsibilities at startup:
 * <ol>
 *   <li><b>Register the quality lifecycle</b> with the shared
 *       {@link StatusFramework}, so {@code FORM_QUALITY_ISSUE} entities can be
 *       transitioned through the canonical valid-transition map and audited.</li>
 *   <li><b>Register Joget plugins</b> exposed by this bundle (post-processor,
 *       process tool, REST API, etc.) — added in subsequent days as the
 *       components land.</li>
 * </ol>
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<>();

        // Print build stamp at startup so the live JAR's identity is visible
        // in the log. Pair this with FormQualityPostProcessor.getDescription()
        // / getVersion() which carry the same stamp into the App Composer UI.
        LogUtil.info(Activator.class.getName(),
                "form-quality-runtime starting — " + Build.STAMP);

        registerQualityLifecycle();

        // Joget plugins exposed by this bundle.
        registrationList.add(context.registerService(
                FormQualityPostProcessor.class.getName(),
                new FormQualityPostProcessor(),
                null));

        // Reusable form element — drag-and-drop banner + issues panel.
        registrationList.add(context.registerService(
                QualityBannerElement.class.getName(),
                new QualityBannerElement(),
                null));

        // Day 4 will register here:
        //   - QualityValidationProcessTool (DefaultApplicationPlugin, used in workflows)
        //   - FormQualityApi (ApiPluginAbstract with @Operation methods)
    }

    @Override
    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }

    /**
     * Registers the four-state quality lifecycle for FORM_QUALITY_ISSUE.
     * See {@link QualityStatus} for the diagram.
     */
    private void registerQualityLifecycle() {
        Map<Status, Set<Status>> tx = new LinkedHashMap<>();

        tx.put(QualityStatus.NOT_VALIDATED,
                set(QualityStatus.VERIFIED, QualityStatus.ISSUES_DETECTED));

        tx.put(QualityStatus.ISSUES_DETECTED,
                set(QualityStatus.VERIFIED,           // re-run with issues fixed
                    QualityStatus.NOT_VALIDATED,      // force re-evaluate (admin)
                    QualityStatus.BLOCKED_FROM_PUBLISH)); // gated transition tried

        tx.put(QualityStatus.VERIFIED,
                set(QualityStatus.ISSUES_DETECTED,    // a later edit re-introduced issues
                    QualityStatus.NOT_VALIDATED));    // force re-evaluate

        tx.put(QualityStatus.BLOCKED_FROM_PUBLISH,
                set(QualityStatus.ISSUES_DETECTED,    // unblock by going back to fix
                    QualityStatus.NOT_VALIDATED));    // force re-evaluate

        StatusFramework.register(
                QualityEntityType.FORM_QUALITY_ISSUE,
                tx,
                set(QualityStatus.NOT_VALIDATED));
    }

    private static Set<Status> set(Status... statuses) {
        return new LinkedHashSet<Status>(Arrays.asList(statuses));
    }
}
