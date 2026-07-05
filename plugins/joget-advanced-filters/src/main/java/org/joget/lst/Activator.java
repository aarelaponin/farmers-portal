package org.joget.lst;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<>();
        registrationList.add(context.registerService(MdmSelectFilterType.class.getName(), new MdmSelectFilterType(), null));
        registrationList.add(context.registerService(FilterPanelDecorator.class.getName(), new FilterPanelDecorator(), null));
        registrationList.add(context.registerService(DateRangeFilterType.class.getName(), new DateRangeFilterType(), null));
        registrationList.add(context.registerService(CascadingMdmSelectFilterType.class.getName(), new CascadingMdmSelectFilterType(), null));
        registrationList.add(context.registerService(ReportFilterPanel.class.getName(), new ReportFilterPanel(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
