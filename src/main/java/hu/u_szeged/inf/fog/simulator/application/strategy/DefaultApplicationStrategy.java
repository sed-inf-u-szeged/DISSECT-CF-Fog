package hu.u_szeged.inf.fog.simulator.application.strategy;

/**
 * The default application strategy does not support
 * offloading any data to the neighbors.
 */
public class DefaultApplicationStrategy extends ApplicationStrategy {

    @Override
    public void findApplication(long dataForTransfer) { 
        /* The strategy does not support offloading. */ 
    }
}