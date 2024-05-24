package hu.u_szeged.inf.fog.simulator.workflow.scheduler;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;



public class MaxMinScheduler extends WorkflowScheduler {
    int cloudIndex;

    public MaxMinScheduler(HashMap<ComputingAppliance, Instance> workflowArchitecture) {
        WorkflowScheduler.workflowArchitecture = workflowArchitecture;
    }

    @Override
    public void schedule(WorkflowJob workflowJob) {

        if (workflowJob.ca == null) {
            workflowJob.ca = ComputingAppliance.allComputingAppliances.get(cloudIndex);
            if (cloudIndex == WorkflowScheduler.workflowArchitecture.keySet().size() - 1) {
                cloudIndex = 0;
            } else {
                cloudIndex++;
            }
        }
        if (workflowJob.inputs.get(0).amount == 0) {
            workflowJob.ca.workflowQueue.add(workflowJob);
        }

        if (Timed.getFireCount() > 0) {
            this.jobReAssign(workflowJob, ComputingAppliance.allComputingAppliances.get(0));

            if (workflowJob.ca.workflowVms.size() < 2) {
                addVm(workflowJob.ca);
            }
        }
    }

    @Override
    public void init() {
        for (ComputingAppliance ca : WorkflowScheduler.workflowArchitecture.keySet()) {
            Instance i = WorkflowScheduler.workflowArchitecture.get(ca);
            ca.iaas.repositories.get(0).registerObject(i.va);
            try {
                ca.workflowVms.add(ca.iaas.requestVM(i.va, i.arc, ca.iaas.repositories.get(0), 1)[0]);
            } catch (VMManagementException e) {
                e.printStackTrace();
            }
        }

        for (ComputingAppliance ca : WorkflowScheduler.workflowArchitecture.keySet()) {
            ca.workflowQueue = new PriorityQueue<WorkflowJob>(new MaxMinComperator());
        }
    }

}

class MaxMinComperator implements Comparator<WorkflowJob> {

    @Override
    public int compare(WorkflowJob o1, WorkflowJob o2) {
        return (int) Math.round(o2.runtime - o1.runtime);
    }
}