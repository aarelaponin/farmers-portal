package global.govstack.parcelzonecentring;

import global.govstack.parcelzonecentring.binder.ParcelGeometryAutoCenterLoadBinder;
import global.govstack.parcelzonecentring.binder.ParcelLocationAutoCenterBinder;
import global.govstack.parcelzonecentring.element.AutoCenterBootstrapElement;
import global.govstack.parcelzonecentring.hook.ParcelZoneCentroidPostProcessor;

import org.joget.commons.util.LogUtil;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * OSGi Bundle Activator for the parcel-zone-centring plugin.
 *
 * <p>Registers two services that share the same goal — populating the GIS
 * auto-centre fields on parcelGeometry from the (district, agroEcologicalZone)
 * pair found on parcelLocation, using the customer-supplied md_district_eco_zone
 * master data. Operators choose ONE of these to wire on parcelLocation:
 *
 * <ul>
 *   <li><b>{@link ParcelLocationAutoCenterBinder}</b> (recommended) — a
 *       custom storeBinder. Runs inline on every parcelLocation commit
 *       (per-tab Next clicks under {@code partiallyStore=true} or wizard
 *       Save). Same transaction as the parent save, no race.</li>
 *   <li><b>{@link ParcelZoneCentroidPostProcessor}</b> (legacy) — a form
 *       post-processor. Only fires at wizard-level top submit, so blocked
 *       in any setup where the wizard's post-processor slot is already
 *       occupied. Kept for completeness and for non-wizard contexts.</li>
 * </ul>
 *
 * <p>Per CLAUDE.md "Every plugin class needs an Activator registration",
 * both classes are registered here even though only one is wired in the
 * live form definitions at any given time.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<>();

        LogUtil.info(Activator.class.getName(),
                "parcel-zone-centring starting — " + Build.STAMP);

        registrationList.add(context.registerService(
                ParcelLocationAutoCenterBinder.class.getName(),
                new ParcelLocationAutoCenterBinder(),
                null));

        registrationList.add(context.registerService(
                ParcelGeometryAutoCenterLoadBinder.class.getName(),
                new ParcelGeometryAutoCenterLoadBinder(),
                null));

        registrationList.add(context.registerService(
                AutoCenterBootstrapElement.class.getName(),
                new AutoCenterBootstrapElement(),
                null));

        registrationList.add(context.registerService(
                ParcelZoneCentroidPostProcessor.class.getName(),
                new ParcelZoneCentroidPostProcessor(),
                null));
    }

    @Override
    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
        LogUtil.info(Activator.class.getName(),
                "parcel-zone-centring stopped");
    }
}
