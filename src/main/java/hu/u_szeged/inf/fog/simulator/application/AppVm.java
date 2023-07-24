package hu.u_szeged.inf.fog.simulator.application;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;

public class AppVm {

    public int id;

    PhysicalMachine pm;

    public VirtualMachine vm;

    boolean isWorking;

    int restartCounter;

    public int taskCounter;

    long creationTime;

    long runningPeriod;

    public long workTime;

    public AppVm(VirtualMachine vm) {
        this.vm = vm;
        this.creationTime = Timed.getFireCount();
        this.runningPeriod = Timed.getFireCount();
        this.id = this.vm.hashCode();
    }

    @Override
    public String toString() {
        return "AppVm [id=" + id + ", isWorking=" + isWorking + ", restartCounter=" + restartCounter + ", taskCounter="
                + taskCounter + ", creationTime=" + creationTime + ", workTime=" + workTime + "]";
    }
}