package hu.u_szeged.inf.fog.simulator.workflow.scheduler;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.energyprovider.Provider;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.node.RenewableWorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

public class RenewableScheduler extends WorkflowScheduler {

    public Provider provider;
    int ratio;
    boolean requirement;
    ArrayList<RenewableWorkflowComputingAppliance> renewableComputeArchitecture;

    public static class RenewableComperator implements Comparator<WorkflowJob> {

        @Override
        public int compare(WorkflowJob o1, WorkflowJob o2) {
            float o1cost = (float) (o1.consumption * o1.runtime);
            float o2cost = (float) (o2.consumption * o2.runtime);
            if (o1cost > o2cost) {
                return 1;
            }
            if (o1cost < o2cost) {
                return -1;
            }
            return 0;
        }
    }

    public RenewableScheduler(ArrayList<WorkflowComputingAppliance> computeArchitecture, Instance instance,
                           ArrayList<Actuator> actuatorArchitecture, Pair<String, ArrayList<WorkflowJob>> jobs,
                           Provider provider, int ratio, boolean requirement) throws Exception {
        this.computeArchitecture = computeArchitecture;
        this.instance = instance;
        this.jobs = jobs.getRight();
        this.appName = jobs.getLeft();
        WorkflowScheduler.schedulers.add(this);
        this.ratio = ratio;
        this.requirement = requirement;
        this.provider = provider;
        this.renewableComputeArchitecture = convertToRenewabelAppliance(computeArchitecture);
        this.jobs = assignConsumptionToOwnJobs(jobs.getRight());
    }

    @Override
    public void init() {

        if (this.provider.renewableBattery.getBatteryLevel() < 3020) {
            new DeferredEvent(1001) {
                @Override
                protected void eventAction() {
                    init();
                }
            };
        } else {

            for (RenewableWorkflowComputingAppliance ca : this.renewableComputeArchitecture) {
                ca.workflowQueue = new PriorityQueue<WorkflowJob>(new RenewableComperator());
                ca.iaas.repositories.get(0).registerObject(this.instance.va);
                try {
                    ca.workflowVms.add(ca.iaas.requestVM(this.instance.va, this.instance.arc, ca.iaas.repositories.get(0), 1)[0]);
                } catch (VMManager.VMManagementException e) {
                    e.printStackTrace();
                }
            }

            int nodeIndex = 0;
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
    }

    @Override
    public void schedule(WorkflowJob workflowJob) {

        if (this.provider.renewableBattery.getBatteryLevel() < 3060) {
            new DeferredEvent(1001) {
                @Override
                protected void eventAction() {
                    schedule(workflowJob);
                }
            };
        } else {

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
            WorkflowExecutor.execute(this);
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

    private ArrayList<WorkflowJob> assignConsumptionToOwnJobs(ArrayList<WorkflowJob> oldJobs) {

        ArrayList<WorkflowJob> newJobs = new ArrayList<WorkflowJob>();

        for (WorkflowJob job : oldJobs) {
            job.consumption = 10;
            newJobs.add(job);
        }

        return newJobs;
    }

    public float getJobConsumption(WorkflowJob job) {
        float hours = (float) (job.runtime / 3600);
        return hours * job.consumption;
    }

    private ArrayList<RenewableWorkflowComputingAppliance> convertToRenewabelAppliance(ArrayList<WorkflowComputingAppliance> cas) throws Exception {
        ArrayList<RenewableWorkflowComputingAppliance> rcas= new ArrayList<RenewableWorkflowComputingAppliance>();
        for (WorkflowComputingAppliance ca : cas) {

            RenewableWorkflowComputingAppliance rca = (RenewableWorkflowComputingAppliance) ca;

            rcas.add(rca);
        }
        return rcas;
    }

    boolean doesProviderHaveEnoughEnergy(WorkflowJob job) {
        return true;
    }
}
