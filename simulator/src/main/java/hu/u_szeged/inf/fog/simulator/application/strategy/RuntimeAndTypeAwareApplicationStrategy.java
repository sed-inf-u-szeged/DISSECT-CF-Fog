package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Task;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.util.ArrayList;
import java.util.Set;

/**
 * The strategy considers both resource load, latency and processable task type to ensure efficient data transfer.
 */
public class RuntimeAndTypeAwareApplicationStrategy extends ApplicationStrategy {

    /**
     * Constructs a new strategy with the specified activation ratio and transfer divider.
     *
     * @param activationRatio triggers offloading if it is larger than the unprocessed data / tasksize ratio
     * @param transferDivider determining the ratio of the data to be transferred
     */
    public RuntimeAndTypeAwareApplicationStrategy(double activationRatio, double transferDivider) {
        this.activationRatio = activationRatio;
        this.transferDivider = transferDivider;
    }

    /**
     * Finds a suitable application from the available computing appliances based on load and latency,
     * and starts a data transfer to the chosen application.
     *
     * @param tasksForTransfer the tasks to be transmitted, they are of the same type
     */
    @Override
    public void findApplication(Set<Task> tasksForTransfer) {

        ArrayList<ComputingAppliance> availableComputingAppliances = this.getComputingAppliances();
        Application chosenApplication = null;
        if (!availableComputingAppliances.isEmpty()) {
            ComputingAppliance selectedCa = availableComputingAppliances.get(0);
            for (ComputingAppliance ca : this.getComputingAppliances()) {
                int lat1 = selectedCa.iaas.repositories.get(0).getLatencies().get(ca.iaas.repositories.get(0).getName());
                int lat2 = ca.iaas.repositories.get(0).getLatencies().get(ca.iaas.repositories.get(0).getName());
                if (selectedCa.getLoadOfResource() > ca.getLoadOfResource() && lat1 > lat2) {
                    selectedCa = ca;
                    for (Application app : selectedCa.applications){
                        //mivel a task setbe csak egyforma taskok vannak, ezért elég a legelső application ami feltudja őket dolgozni (ha van)
                        if(app.types.contains(tasksForTransfer.iterator().next().type)){
                            chosenApplication = app;
                            break;
                        }
                        chosenApplication = null;
                    }
                }
            }

            if(chosenApplication!=null){
                this.startDataTranfer(chosenApplication, tasksForTransfer);
            }

        }
    }


    //unused
    @Override
    public void findApplication(long dataForTransfer) {

    }
}
