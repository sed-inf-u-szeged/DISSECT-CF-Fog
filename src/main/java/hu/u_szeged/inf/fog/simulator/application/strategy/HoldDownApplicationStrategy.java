package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

/**
 * The strategy selects an application from the neighboring computing appliance with the least resource load.
 * This strategy helps in balancing the load among neighboring appliances.
 */
public class HoldDownApplicationStrategy extends ApplicationStrategy {

    /**
     * Constructs a new strategy with the specified activation ratio and transfer divider.
     *
     * @param activationRatio triggers offloading if it is larger than the unprocessed data / tasksize ratio 
     * @param transferDivider determining the ratio of the data to be transferred
     */
    public HoldDownApplicationStrategy(double activationRatio, double transferDivider) {
        this.activationRatio = activationRatio;
        this.transferDivider = transferDivider;
    }

    /**
     * Finds a suitable application from the neighboring computing appliances with the least load and 
     * starts a data transfer to the chosen application.
     *
     * @param dataForTransfer the amount of data to be transferred
     */
    @Override
    public void findApplication(long dataForTransfer) {
        if (this.application.computingAppliance.neighbors.size() > 0) {
            double min = Double.MAX_VALUE;
            ComputingAppliance selectedCa = null;
            for (ComputingAppliance ca : this.application.computingAppliance.neighbors) {
                if (ca.getLoadOfResource() < min) {
                    min = ca.getLoadOfResource();
                    selectedCa = ca;
                }
            }
            Application chosenApplication = selectedCa.applications
                    .get(SeedSyncer.centralRnd.nextInt(selectedCa.applications.size()));
            this.startDataTranfer(chosenApplication, dataForTransfer);
        }
    }
}
