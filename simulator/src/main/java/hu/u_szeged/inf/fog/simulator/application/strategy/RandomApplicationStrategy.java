package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import java.util.ArrayList;

public class RandomApplicationStrategy extends ApplicationStrategy {

    public RandomApplicationStrategy(double activationRatio, double transferDivider) {
        this.activationRatio = activationRatio;
        this.transferDivider = transferDivider;
    }

    @Override
    public void findApplication(long dataForTransfer) {
        Application chosenApplication;
        ArrayList<Application> availableApplications = new ArrayList<>();
        for (ComputingAppliance ca : this.getComputingAppliances()) {
            availableApplications.addAll(ca.applications);
        }

        if (availableApplications.size() > 0) {
            int rnd = SeedSyncer.centralRnd.nextInt(availableApplications.size());
            chosenApplication = availableApplications.get(rnd);
            this.startDataTranfer(chosenApplication, dataForTransfer);
        }
    }
}
