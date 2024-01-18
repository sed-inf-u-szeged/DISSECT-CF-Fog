package hu.u_szeged.inf.fog.simulator.iot.strategy;


import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;

import java.util.ArrayList;

public class RandomDeviceStrategy extends DeviceStrategy {

    @Override
    public void findApplication() {
        this.chosenApplication = null;
        ArrayList<Application> availableApplications = this.getAvailableApplications();
     
        if (availableApplications.size() > 0) {
            int rnd = SeedSyncer.centralRnd.nextInt(availableApplications.size());
            this.chosenApplication = availableApplications.get(rnd);
        }

        MobilityEvent.refresh(this.device, this.chosenApplication);
    }

}
