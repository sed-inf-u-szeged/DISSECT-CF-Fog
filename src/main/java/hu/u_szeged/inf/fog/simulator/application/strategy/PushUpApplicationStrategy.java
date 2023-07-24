package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;

public class PushUpApplicationStrategy extends ApplicationStrategy {

    public PushUpApplicationStrategy(double activationRatio, double transferDivider) {
        this.activationRatio = activationRatio;
        this.transferDivider = transferDivider;
    }

    @Override
    public void findApplication(long dataForTransfer) {
        if (this.application.computingAppliance.parent != null) {
            Application chosenApplication = this.application.computingAppliance.applications
                    .get(SeedSyncer.centralRnd.nextInt(this.application.computingAppliance.parent.applications.size()));
            this.startDataTranfer(chosenApplication, dataForTransfer);
        }
    }

}
