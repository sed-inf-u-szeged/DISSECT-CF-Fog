package hu.u_szeged.inf.fog.simulator.iot.strategy;

import java.util.ArrayList;

import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
 
public class DistanceBasedDeviceStrategy extends DeviceStrategy{

	@Override
	public void findApplication() {
		this.chosenApplication = null;
		ArrayList <Application> availableApplications = this.getAvailableApplications();

        if (availableApplications.size() > 0) {
        	double min = Double.MAX_VALUE;
        	this.chosenApplication = null;
            for (Application app : availableApplications) {
                if(this.device.geoLocation.calculateDistance(app.computingAppliance.geoLocation)<min) {
                	min = this.device.geoLocation.calculateDistance(app.computingAppliance.geoLocation);
                	this.chosenApplication = app;
                }
            }
        } 
        MobilityEvent.refresh(this.device, this.chosenApplication);	
		
	}

}
