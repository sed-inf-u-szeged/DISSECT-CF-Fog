package hu.u_szeged.inf.fog.simulator.workflow.scheduler;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;

public class MaxMinScheduler extends WorkflowScheduler {
    
    static class MaxMinComperator implements Comparator<WorkflowJob> {

        @Override
        public int compare(WorkflowJob o1, WorkflowJob o2) {
            return (int) Math.round(o2.runtime - o1.runtime);
        }
    }

    public MaxMinScheduler(ArrayList<WorkflowComputingAppliance> computeArchitecture, Instance instance, 
            ArrayList<Actuator> actuatorArchitecture, Pair<String, ArrayList<WorkflowJob>> jobs) {
        this.computeArchitecture = computeArchitecture;
        this.instance = instance;
        this.jobs = jobs.getRight();
        this.appName = jobs.getLeft();
        WorkflowScheduler.schedulers.add(this);
    }
    
    @Override
    public void init() {
        for (WorkflowComputingAppliance ca : this.computeArchitecture) {
            ca.workflowQueue = new PriorityQueue<WorkflowJob>(new MaxMinComperator());
            ca.iaas.repositories.get(0).registerObject(this.instance.va);
            try {
                ca.workflowVms.add(ca.iaas.requestVM(this.instance.va, this.instance.arc, ca.iaas.repositories.get(0), 1)[0]);
            } catch (VMManagementException e) {
                e.printStackTrace();
            }
        }
        
        int nodeIndex = 0;
        Collections.shuffle(this.jobs);
        for (WorkflowJob workflowJob : this.jobs) {
            workflowJob.ca = this.computeArchitecture.get(nodeIndex);
            if (nodeIndex == this.computeArchitecture.size() - 1) {
                nodeIndex = 0;
            } else {
                nodeIndex++;
            }
            
            if (workflowJob.inputs.get(0).amount == 0) {
                workflowJob.ca.workflowQueue.add(workflowJob);
            }
        }
    }

    @Override
    public void schedule(WorkflowJob workflowJob) {
        if (workflowJob.inputs.get(0).amount == 0) {
            workflowJob.ca.workflowQueue.add(workflowJob);
        }
        
        for (WorkflowComputingAppliance wca : this.computeArchitecture) {
            int vmCount = 0;
            int jobCount = 0;
            for (VirtualMachine vm : wca.iaas.listVMs()) {
                jobCount += vm.underProcessing.size();
                vmCount++;
            }
            if (jobCount / vmCount > 1) {
                this.addVm(wca);
            } else if (countRunningVms(wca) > 1) {
                this.shutdownVm(wca);
            }
        }
    }
    
    private int countRunningVms(WorkflowComputingAppliance wca) {
        int count = 0;
        for (VirtualMachine vm : wca.iaas.listVMs()) {
            if (vm.getState().equals(VirtualMachine.State.RUNNING)) {
                count++;
            }
        }
        return count;
    }
}