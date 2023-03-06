package hu.u_szeged.inf.fog.simulator.iot.strategy;

import java.util.ArrayList;
import java.util.stream.Collectors;

import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Device;

public abstract class DeviceStrategy {
	
	public Device device;

	public Application chosenApplication;

	public abstract void findApplication();
	
	public ArrayList<Application> getAvailableApplications(){
		
		ArrayList<Application> availableApplications = Application.allApplications.stream()
								.filter(app -> app.serviceable && this.device.geoLocation.calculateDistance(app.computingAppliance.geoLocation) <= app.computingAppliance.range*1000)
								.collect(Collectors
			                     .toCollection(ArrayList::new));
		
		return availableApplications;
	}
	
}
