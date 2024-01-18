package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;

import java.util.ArrayList;

public class RuntimeAwareApplicationStrategy extends ApplicationStrategy {

    public RuntimeAwareApplicationStrategy(double activationRatio, double transferDivider) {
        this.activationRatio = activationRatio;
        this.transferDivider = transferDivider;
    }

    @Override
    public void findApplication(long dataForTransfer) {

        ArrayList<ComputingAppliance> availableComputingAppliances = this.getComputingAppliances();
        if (availableComputingAppliances.size() > 0) {
            ComputingAppliance selectedCa = availableComputingAppliances.get(0);
            for (ComputingAppliance ca : this.getComputingAppliances()) {
                int lat1 = selectedCa.iaas.repositories.get(0).getLatencies()
                        .get(ca.iaas.repositories.get(0).getName());
                int lat2 = ca.iaas.repositories.get(0).getLatencies().get(ca.iaas.repositories.get(0).getName());
                if (selectedCa.getLoadOfResource() > ca.getLoadOfResource() && lat1 > lat2) {
                    selectedCa = ca;
                }
            }
            Application chosenApplication = selectedCa.applications
                    .get(SeedSyncer.centralRnd.nextInt(selectedCa.applications.size()));
            this.startDataTranfer(chosenApplication, dataForTransfer);
        }

    }

}
