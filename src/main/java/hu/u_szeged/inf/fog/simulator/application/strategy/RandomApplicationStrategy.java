package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import java.util.ArrayList;

/**
 * A strategy for randomly selecting an application to transfer data to.
 */
public class RandomApplicationStrategy extends ApplicationStrategy {

    /**
     * Constructs a new strategy with the specified activation ratio and transfer divider.
     *
     * @param activationRatio triggers offloading if it is larger than the unprocessed data / tasksize ratio 
     * @param transferDivider determining the ratio of the data to be transferred
     */
    public RandomApplicationStrategy(double activationRatio, double transferDivider) {
        this.activationRatio = activationRatio;
        this.transferDivider = transferDivider;
    }

    /**
     * Finds an application to transfer data to randomly from the available applications.
     *
     * @param dataForTransfer the amount of data to be transferred
     */
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
