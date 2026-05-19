package hu.u_szeged.inf.fog.simulator.application.strategy;

import hu.u_szeged.inf.fog.simulator.iot.Task;
import java.util.Set;

/**
 * The default application strategy does not support
 * offloading any data to the neighbors.
 */
public class DefaultApplicationStrategy extends ApplicationStrategy {

    @Override
    public void findApplication(long dataForTransfer) { 
        /* The strategy does not support offloading. */ 
    }

    @Override
    public void findApplication(Set<Task> tasksForTransfer) {

    }

}