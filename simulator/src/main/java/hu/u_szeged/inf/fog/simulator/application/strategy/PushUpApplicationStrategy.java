package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;

/**
 * The strategy selects a random application from the parent computing appliance to initiate a data transfer to it.
 * This strategy is useful for distributing data to higher-level applications within a hierarchical structure.
 */
public class PushUpApplicationStrategy extends ApplicationStrategy {

    /**
     * Constructs a new strategy with the specified activation ratio and transfer divider.
     *
     * @param activationRatio triggers offloading if it is larger than the unprocessed data / tasksize ratio 
     * @param transferDivider determining the ratio of the data to be transferred
     */
    public PushUpApplicationStrategy(double activationRatio, double transferDivider) {
        this.activationRatio = activationRatio;
        this.transferDivider = transferDivider;
    }

    /**
     * Finds a suitable application from the parent computing appliance's list of applications and 
     * starts a data transfer to the chosen application. 
     *
     * @param dataForTransfer the amount of data to be transferred
     */
    @Override
    public void findApplication(long dataForTransfer) {
        if (this.application.computingAppliance.parent != null) {
            Application chosenApplication = this.application.computingAppliance.parent.applications
                    .get(SeedSyncer.centralRnd.nextInt(this.application.computingAppliance.parent.applications.size()));
            this.startDataTranfer(chosenApplication, dataForTransfer);
        }
    }
}
