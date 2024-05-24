package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption.ConsumptionEvent;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import java.util.ArrayList;

public abstract class ApplicationStrategy {

    public Application application;

    public double activationRatio;

    public double transferDivider;

    public abstract void findApplication(long dataForTransfer);

    public ArrayList<ComputingAppliance> getComputingAppliances() {

        ArrayList<ComputingAppliance> availableComputingAppliance = new ArrayList<ComputingAppliance>();

        availableComputingAppliance.addAll(this.application.computingAppliance.neighbors);

        if (this.application.computingAppliance.parent != null) {
            availableComputingAppliance.add(this.application.computingAppliance.parent);
        }

        return availableComputingAppliance;
    }

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
                                System.exit(0); // TODO: it should not be an error.
                            }
                        });
            } catch (NetworkException e) {
                e.printStackTrace();
            }
        }
    }

}