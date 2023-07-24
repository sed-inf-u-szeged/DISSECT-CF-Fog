package hu.u_szeged.inf.fog.simulator.iot.strategy;

import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import java.util.ArrayList;

public class LoadBalancedDeviceStrategy extends DeviceStrategy {

    @Override
    public void findApplication() {
        this.chosenApplication = null;
        ArrayList<Application> availableApplications = this.getAvailableApplications();

        if (availableApplications.size() > 0) {
            double min = Double.MAX_VALUE;
            this.chosenApplication = null;
            for (Application app : availableApplications) {
                if (app.computingAppliance.getLoadOfResource() < min) {
                    min = app.computingAppliance.getLoadOfResource();
                    this.chosenApplication = app;
                }
            }
        }
        MobilityEvent.refresh(this.device, this.chosenApplication);

    }

}
