package hu.u_szeged.inf.fog.simulator.application.strategy;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Task;
import hu.u_szeged.inf.fog.simulator.iot.TaskType;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.OffloadingStatistics;

import java.util.Set;

/** * The strategy considers both resource load, latency and processable task type to ensure efficient data transfer. */
public class RuntimeAndTypeAwareApplicationStrategy extends ApplicationStrategy {

    /** * Constructs a new strategy with the specified activation ratio and transfer divider.
     * @param activationRatio triggers offloading if it is larger than the unprocessed data / tasksize ratio
     * @param transferDivider determining the ratio of the data to be transferred
    */
    public RuntimeAndTypeAwareApplicationStrategy(double activationRatio, double transferDivider) {
        this.activationRatio = activationRatio;
        this.transferDivider = transferDivider;
    }


    /** * Finds a suitable application from the available computing appliances based on load and latency,
     * and starts a data transfer to the chosen application.
     * @param tasksForTransfer the tasks to be transmitted, they are of the same type */
    @Override public void findApplication(Set<Task> tasksForTransfer) {
        if (tasksForTransfer.isEmpty()) {
            return;
        }

        TaskType taskType = tasksForTransfer.iterator().next().type;

        Application bestApplication = null;
        //heurisztikaszerű érték, ami főleg a loadot (legyen minél kisebb, mert ha nagyobb ahova küldjük az nem okos)
        //és másodlagosan a latencyt veszi figyelembe (minél kisebb annál jobb, de nem annyira fontos)

        double bestScore = Double.MAX_VALUE;

        for (ComputingAppliance ca : this.getComputingAppliances()) {
            Integer latency = this.application.computingAppliance.iaas.repositories.get(0)
                    .getLatencies()
                    .get(ca.iaas.repositories.get(0).getName());
            //igy hogy az előző képest nem int hanem Integer a latency lehet null is az érték és néhány bug le lesz kezelve (parent neighbor rossz beállítása)

            if (latency == null) {
                continue;
            }

            for (Application app : ca.applications) {
                if (!app.types.contains(taskType)) {
                    continue;
                }

                double score = ca.getLoadOfResource() + latency * 0.1;

                if (score < bestScore) {
                    bestScore = score;
                    bestApplication = app;
                }
            }
        }

        if (bestApplication != null) {
            OffloadingStatistics.registerDecision(tasksForTransfer.iterator().next(), "OFFLOAD", bestApplication.computingAppliance.name.contains("cloud") ? "CLOUD" : "FOG", "Device"+this.hashCode());
            this.startDataTranfer(bestApplication, tasksForTransfer);
        }
    }

    //unused
    @Override public void findApplication(long dataForTransfer) { }
}
