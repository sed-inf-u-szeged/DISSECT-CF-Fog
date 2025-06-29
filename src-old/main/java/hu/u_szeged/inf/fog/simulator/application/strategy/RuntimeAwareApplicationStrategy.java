package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import java.util.ArrayList;

/**
 * The strategy considers both resource load and latency to ensure efficient data transfer.
 */
public class RuntimeAwareApplicationStrategy extends ApplicationStrategy {

    /**
     * Constructs a new strategy with the specified activation ratio and transfer divider.
     *
     * @param activationRatio triggers offloading if it is larger than the unprocessed data / tasksize ratio 
     * @param transferDivider determining the ratio of the data to be transferred
     */
    public RuntimeAwareApplicationStrategy(double activationRatio, double transferDivider) {
        this.activationRatio = activationRatio;
        this.transferDivider = transferDivider;
    }

    /**
     * Finds a suitable application from the available computing appliances based on load and latency,
     * and starts a data transfer to the chosen application.
     *
     * @param dataForTransfer the amount of data to be transferred
     */
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
