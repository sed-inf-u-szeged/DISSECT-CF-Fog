package hu.u_szeged.inf.fog.simulator.iot.strategy;

import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import java.util.ArrayList;

/**
 * This class represents a device strategy for finding the least loaded application.
 */
public class LoadBalancedDeviceStrategy extends DeviceStrategy {

    /**
     * If there are available applications, then the least loaded one will be chosen.
     * As it can cause a node change, the latency must be updated. 
     */
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