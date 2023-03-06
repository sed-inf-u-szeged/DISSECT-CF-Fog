package hu.u_szeged.inf.fog.simulator.iot.strategy;

import java.util.ArrayList;

import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;

public class PliantDeviceStrategy extends DeviceStrategy{

	@Override
	public void findApplication() {
		this.chosenApplication = null;
		ArrayList <Application> availableApplications = this.getAvailableApplications();

        if (availableApplications.size() > 0) {
            
        } 
        
        MobilityEvent.refresh(this.device, this.chosenApplication);
		
	}

}
