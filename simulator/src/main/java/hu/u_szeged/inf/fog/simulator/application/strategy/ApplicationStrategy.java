package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption.ConsumptionEvent;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import java.util.ArrayList;

/**
 * This is an abstract class to implement arbitrary offloading logic 
 * for IoT applications by overriding the findApplication method.
 */
public abstract class ApplicationStrategy {

    /**
     * The application that will apply the strategy.
     */
    public Application application;

    /**
     * If the ratio of the application's unprocessed data to
     * the task size is greater than this value, 
     * the implemented offloading algorithm is triggered.
     */
    public double activationRatio;

    /**
     * The unprocessed data is divided by this value to 
     * determine how much data is transmitted. 
     * This allows both full and partial offloading.
     */
    public double transferDivider;

    /**
     * The method to be overridden, which defines the offloading strategy.
     *
     * @param dataForTransfer the amount of data to be transmitted
     */
    public abstract void findApplication(long dataForTransfer);

    /**
     * This returns the available nodes (physical resources) connected to
     * the physical nodes running this application.
     */
    public ArrayList<ComputingAppliance> getComputingAppliances() {
        ArrayList<ComputingAppliance> availableComputingAppliance = new ArrayList<ComputingAppliance>();
        
        availableComputingAppliance.addAll(this.application.computingAppliance.neighbors);
        if (this.application.computingAppliance.parent != null) {
            availableComputingAppliance.add(this.application.computingAppliance.parent);
        }
        return availableComputingAppliance;
    }

    /**
     * Initiates a data transfer process between the current application and the chosen application.
     *
     * @param chosenApplication the application to transfer data to
     * @param dataForTransfer   the amount of data to be transferred
     */
    protected void startDataTranfer(Application chosenApplication, long dataForTransfer) {
        if (chosenApplication != null) {
            try {
                this.application.receivedData -= dataForTransfer;
                chosenApplication.incomingData++;
                NetworkNode.initTransfer(dataForTransfer, ResourceConsumption.unlimitedProcessing,
                        this.application.computingAppliance.iaas.repositories.get(0),
                        chosenApplication.computingAppliance.iaas.repositories.get(0), new ConsumptionEvent() {
                            long onNetwork = Timed.getFireCount();

                            @Override
                            public void conComplete() {
                                if (!chosenApplication.isSubscribed()) {
                                    chosenApplication.subscribeApplication();
                                }
                                chosenApplication.receivedData += dataForTransfer;
                                chosenApplication.incomingData--;
                                Application.totalTimeOnNetwork += (Timed.getFireCount() - onNetwork);
                                Application.totalBytesOnNetwork += dataForTransfer;
                            }

                            @Override
                            public void conCancelled(ResourceConsumption problematic) {
                                System.err.println("WARNING: File transfer between the applications is unsuccessful!");
                                System.exit(0); 
                            }
                        });
            } catch (NetworkException e) {
                e.printStackTrace();
            }
        }
    }
}