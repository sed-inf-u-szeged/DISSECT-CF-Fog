package hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;

import java.util.List;

/**
 * The main PM control mechanisms are implemented in this class
 * <p>
 * The class basically controls a single PM. It switches off its PM when the
 * PM's free resources reach its complete resource set and there are no VM
 * requests queuing at the IaaS service's VM scheduler.
 *
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 * of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory of Parallel and
 * Distributed Systems, MTA SZTAKI (c) 2012"
 */
class CapacityChangeManager
        implements VMManager.CapacityChangeEvent<ResourceConstraints>, PhysicalMachine.StateChangeListener {

    private final SchedulingDependentMachines controller;
    /**
     * the physical machine that this capacity manager will target with its
     * operations
     */
    private final PhysicalMachine observed;

    /**
     * This constructor is expected to be used once a PM is registered to the parent
     * IaaSService. From that point on the newly registered PM will be considered
     * for switching off/turning on.
     *
     * @param pm the PM to be managed to follow the demand patterns of the VM
     *           requests to the IaaSService.
     */
    public CapacityChangeManager(SchedulingDependentMachines controller, final PhysicalMachine pm) {
        this.controller = controller;
        observed = pm;
        observed.subscribeToIncreasingFreeapacityChanges(this);
        observed.subscribeStateChangeEvents(this);
    }

    /**
     * if the PM gets dropped from the parent IaaSService then we no longer need to
     * control its behavior. So the class's active behavior is disabled by calling
     * this function.
     */
    void cancelEvents() {
        observed.unsubscribeFromIncreasingFreeCapacityChanges(this);
        observed.unsubscribeStateChangeEvents(this);
    }

    /**
     * Allows the observed PM to be switched off and its exceptions handled
     */
    private void switchoffmachine() {
        try {
            observed.switchoff(null);
            // These exceptions below are only relevant if migration
            // is requested now they will never come.
        } catch (VMManager.VMManagementException | NetworkNode.NetworkException e) {
            // ignore
        }
    }

    /**
     * This function is called when the observed PM has newly freed up resources. It
     * ensures that the PM is switched off if the PM is completely free and there
     * are no more VMs queuing at the VM scheduler (i.e., there will be no chance to
     * receive a new VM for the capacities of this PM).
     */
    @Override
    public void capacityChanged(final ResourceConstraints newCapacity,
                                final List<ResourceConstraints> newlyFreeCapacities) {
        if (observed.getCapacities().compareTo(newCapacity) <= 0
                && controller.parent.sched.getTotalQueued().getRequiredCPUs() == 0) {
            // Totally free machine and nothing queued
            switchoffmachine();
        }
    }

    /**
     * Keeps a just started PM on for a short while to allow some new VMs to arrive, otherwise it seems like
     * we just started the PM for no reason
     */
    class MachineSwitchoffDelayer extends DeferredEvent {
        public MachineSwitchoffDelayer() {
            super(observed.getCurrentOnOffDelay());
        }

        @Override
        protected void eventAction() {
            if (!observed.isHostingVMs() && observed.isRunning()) {
                switchoffmachine();
            }
        }
    }

    /**
     * This function is called when the PM's power state changes. This event handler
     * manages situations when the PM is turned on but there are no longer tasks for
     * it. Also, it initiates a new PM's switchon if the newly switched on machine
     * will not be enough to host the queued VM requests found at the VM scheduler
     * of the parent IaaS service.
     */
    @Override
    public void stateChanged(PhysicalMachine pm, PhysicalMachine.State oldState, PhysicalMachine.State newState) {
        if (PhysicalMachine.State.RUNNING.equals(newState)) {
            controller.currentlyStartingPM = null;
            if (controller.parent.sched.getQueueLength() == 0) {
                new MachineSwitchoffDelayer();
            } else {
                var runningCapacities = controller.parent.getRunningCapacities();
                if (!controller.parent.runningMachines.contains(observed)) {
                    // parent have not recognized this PM's startup yet
                    runningCapacities = new AlterableResourceConstraints(runningCapacities).singleAddCont(observed.getCapacities());
                }
                if (runningCapacities.compareTo(controller.parent.sched.getTotalQueued()) < 0) {
                    // no capacities to handle the currently queued jobs, so
                    // we need to start up further machines
                    controller.getQueueingEvent().queueingStarted();
                }
            }
        }
    }
}
