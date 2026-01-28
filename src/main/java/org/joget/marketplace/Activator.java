package org.joget.marketplace;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {
    public final static String VERSION = "8.0.10";
    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(DMSOpenKMFileUpload.class.getName(), new DMSOpenKMFileUpload(), null));
        registrationList.add(context.registerService(DMSOpenKMFormOptionsBinder.class.getName(), new DMSOpenKMFormOptionsBinder(), null));
        registrationList.add(context.registerService(DMSOpenKMDatalistFormatter.class.getName(), new DMSOpenKMDatalistFormatter(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}