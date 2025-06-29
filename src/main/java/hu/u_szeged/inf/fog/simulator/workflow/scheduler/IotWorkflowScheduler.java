package hu.u_szeged.inf.fog.simulator.workflow.scheduler;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;
import org.apache.commons.lang3.tuple.Pair;

public class IotWorkflowScheduler extends WorkflowScheduler {

    class NoOperationComperator implements Comparator<WorkflowJob> {

        @Override
        public int compare(WorkflowJob o1, WorkflowJob o2) {
            return 0;
        }
    }

    public IotWorkflowScheduler(ArrayList<WorkflowComputingAppliance> computeArchitecture, Instance instance, 
            ArrayList<Actuator> actuatorArchitecture, Pair<String, ArrayList<WorkflowJob>> jobs, int latency) {
        this.computeArchitecture = computeArchitecture;
        this.actuatorArchitecture = actuatorArchitecture;
        this.instance = instance;
        this.jobs = jobs.getRight();
        this.appName = jobs.getLeft();
        this.defaultLatency = latency;
        WorkflowScheduler.schedulers.add(this);
    }

    @Override
    public void init() {
        for (WorkflowComputingAppliance ca : this.computeArchitecture) {
            ca.workflowQueue = new PriorityQueue<WorkflowJob>(new NoOperationComperator());
            ca.iaas.repositories.get(0).registerObject(this.instance.va);
            try {
                ca.workflowVms.add(ca.iaas.requestVM(this.instance.va, this.instance.arc, ca.iaas.repositories.get(0), 1)[0]);
            } catch (VMManagementException e) {
                e.printStackTrace();
            }
        }
        
        for (Actuator a : this.actuatorArchitecture) {
            a.actuatorWorkflowQueue = new PriorityQueue<WorkflowJob>(new NoOperationComperator());
        }
        
        for (WorkflowJob workflowJob : this.jobs) {
            if (workflowJob.id.contains("service")) {
                workflowJob.ca = this.computeArchitecture.get(SeedSyncer.centralRnd.nextInt(this.computeArchitecture.size()));
                if (workflowJob.inputs.get(0).amount == 0) {
                    workflowJob.ca.workflowQueue.add(workflowJob);
                }
            }
            if (workflowJob.id.contains("actuator")) {
                workflowJob.actuator = this.actuatorArchitecture.get(SeedSyncer.centralRnd.nextInt(this.actuatorArchitecture.size()));
                if (workflowJob.inputs.get(0).amount == 0) {
                    workflowJob.actuator.actuatorWorkflowQueue.add(workflowJob);
                }
            }
        }
    }

    @Override
    public void schedule(WorkflowJob workflowJob) {
        if (workflowJob.id.contains("service") && workflowJob.inputs.get(0).amount == 0) {
            workflowJob.ca.workflowQueue.add(workflowJob);
        }
        if (workflowJob.id.contains("actuator") && workflowJob.inputs.get(0).amount == 0) {
            workflowJob.actuator.actuatorWorkflowQueue.add(workflowJob);
        }
    }
}