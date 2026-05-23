package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.iot.Task;
import hu.u_szeged.inf.fog.simulator.iot.TaskType;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.util.Set;

/**
 * The Minimum Completion Time with Communication heuristic assigns each task to the surrogate with the minimum expected completion time.
 */
public class MCTCommApplicationStrategy extends ApplicationStrategy {

    /**
     * Constructs a new strategy with the specified activation ratio and transfer divider.
     *
     * @param activationRatio triggers offloading if it is larger than the unprocessed data / tasksize ratio
     * @param transferDivider determining the ratio of the data to be transferred
     */
    public MCTCommApplicationStrategy(double activationRatio, double transferDivider) {
        this.activationRatio = activationRatio;
        this.transferDivider = transferDivider;
    }

    /**
     * Finds a suitable application from the available computing appliances based on heuristics,
     * and starts a data transfer to the chosen application.
     *
     * @param tasksForTransfer the tasks to be transmitted, they are of the same type
     */
    @Override
    public void findApplication(Set<Task> tasksForTransfer) {
        if (tasksForTransfer.isEmpty()) {
            return;
        }

        TaskType taskType = tasksForTransfer.iterator().next().type;

        Application bestApplication = null;
        double bestCompletionTime = Double.MAX_VALUE;

        //a tick amikor befejeződne a feladat a jelenlegi appon, az estimatewaiting time elég általánosan van számolva
        double localCompletionTime = estimateExecutionTime(this.application,tasksForTransfer) + estimateWaitingTime(this.application);

        for (ComputingAppliance ca : this.getComputingAppliances()) {

            Integer latency = this.application.computingAppliance.iaas.repositories.get(0)
                    .getLatencies()
                    .get(ca.iaas.repositories.get(0).getName());

            if (latency == null) {
                continue;
            }

            for (Application app : ca.applications) {
                if (!app.types.contains(taskType)) {
                    continue;
                }

                // a tick amikor befejeződne a feladat ez az elküldés (csak latencyt vettem figyelembe, ami lehet nem a legjobb de megteszi),
                // feldolgozás és várakozásból jön össze kb,
                // ezek közül a várakozás a legkevésbé átgondolt, mint fentebb írtam
                double remoteCompletionTime = latency
                                              + estimateWaitingTime(app)
                                              + estimateExecutionTime(app, tasksForTransfer);


//                SimLogger.logRun(
//                        "LocalCT=" + localCompletionTime +
//                                " RemoteCT=" + remoteCompletionTime +
//                                " Candidate=" + app.name
//                );

                //ha nem lenne kész deadlineig akkor nincs értelme elküldeni
                if (!meetsDeadline(tasksForTransfer, remoteCompletionTime)) {
                    continue;
                }



                //heurisztika alapján megkeressük a legjobb appot ahova offloadolhatnánk, ez nem mindig lesz jobb a localnál
                if (remoteCompletionTime < bestCompletionTime) {
                    bestCompletionTime = remoteCompletionTime;
                    bestApplication = app;
                }
            }
        }

        //ha jobb a localnál akkor lehet offloadolni
        if (bestApplication != null && bestCompletionTime < localCompletionTime) {
//            SimLogger.logRun(
//                    "Offloaded to " + bestApplication.name +
//                            " localCT=" + localCompletionTime +
//                            " remoteCT=" + bestCompletionTime
//            );
            this.startDataTranfer(bestApplication, tasksForTransfer);
        }
    }


    //unused
    @Override
    public void findApplication(long dataForTransfer) {

    }

    //becsüljük a feldolgozási időt, elvileg elég pontosnak kéne legyen, mert a VM instancek tulajdonságai alapján megy
    private double estimateExecutionTime(Application app, Set<Task> tasks) {
        double totalExecutionTime = 0;

        for (Task task : tasks) {
            double taskInstructions = app.instructions * task.size / app.tasksize;
            double processingPower = app.instance.arc.getTotalProcessingPower();

            totalExecutionTime += taskInstructions / processingPower;
        }

        int runningVms = Math.max(1, app.countRunningVms());

        return totalExecutionTime / runningVms;
    }

    //volt egy durvább verzió ami az app frekvenciát nézte, ez igy jobban hasonlit az execution time számításra
    private double estimateWaitingTime(Application app) {
        if (app.tasks.isEmpty()) {
            return 0;
        }

        double queuedExecutionTime = 0;

        for (Task task : app.tasks) {
            double taskInstructions = app.instructions * task.size / app.tasksize;
            double processingPower = app.instance.arc.getTotalProcessingPower();

            queuedExecutionTime += taskInstructions / processingPower;
        }

        int runningVms = Math.max(1, app.countRunningVms());

        return queuedExecutionTime / runningVms;
    }

    private boolean meetsDeadline(Set<Task> tasks, double estimatedCompletionTime) {
        long totalSize = getTotalTaskSize(tasks);

        if (totalSize == 0) {
            return true;
        }

        long now = Timed.getFireCount();

        for (Task task : tasks) {
            double estimatedTaskTime = estimatedCompletionTime * ((double) task.size / totalSize);

            //hamár határidőn túli task van akkor mindenféleképpen megengedjük az offloadolást
            if (now < task.getDeadline() && now + estimatedTaskTime > task.getDeadline()) {
                return false;
            }

            //ebben az esetben deadlineon túli task csak hyelben dolgozható fel, nagyon lassú
//            if (now + estimatedTaskTime > task.getDeadline()) {
//                return false;
//            }
        }

        return true;
    }

    private long getTotalTaskSize(Set<Task> tasks) {
        long totalSize = 0;

        for (Task task : tasks) {
            totalSize += task.size;
        }

        return totalSize;
    }
}