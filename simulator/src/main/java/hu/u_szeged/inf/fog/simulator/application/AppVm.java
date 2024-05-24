package hu.u_szeged.inf.fog.simulator.application;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;

/**
 * This class holds information about a virtual machine
 * utilized by an Application instance.
 */
public class AppVm {

    /**
     * The identifier for this VM instance.
     */
    public int id;

    /**
     * The physical machine that hosts this VM.
     */
    PhysicalMachine pm;

    /**
     * The virtual machine utilized by the application instance.
     */
    public VirtualMachine vm;

    /**
     * Indicates whether the VM is currently processing a task.
     */
    boolean isWorking;

    /**
     * The number of times this VM has been restarted.
     */
    int restartCounter;

    /**
     * The number of tasks this VM has processed.
     */
    public int taskCounter;

    /**
     * The timestamp when this VM was created.
     */
    long creationTime;

    /**
     * Measures the time elapsed since the last start of the VM.
     */
    long runningPeriod;

    /**
     * The total work time of this VM.
     */
    public long workTime;

    /**
     * Constructs a new AppVm instance with the specified virtual machine.
     *
     * @param vm the virtual machine associated with this instance
     */
    public AppVm(VirtualMachine vm) {
        this.vm = vm;
        this.creationTime = Timed.getFireCount();
        this.runningPeriod = Timed.getFireCount();
        this.id = this.vm.hashCode();
    }

    /**
     * Returns a string representation of this AppVm instance.
     */
    @Override
    public String toString() {
        return "AppVm [id=" + id + ", isWorking=" + isWorking + ", restartCounter=" + restartCounter + ", taskCounter="
                + taskCounter + ", creationTime=" + creationTime + ", workTime=" + workTime + "]";
    }    
}