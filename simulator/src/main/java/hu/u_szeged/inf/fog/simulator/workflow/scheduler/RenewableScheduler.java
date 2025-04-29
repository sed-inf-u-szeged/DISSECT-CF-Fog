package hu.u_szeged.inf.fog.simulator.workflow.scheduler;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.energyprovider.Provider;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.node.RenewableWorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

public class RenewableScheduler extends WorkflowScheduler {

    public Provider provider;
    int ratio;
    boolean requirement;
    ArrayList<RenewableWorkflowComputingAppliance> renewableComputeArchitecture;
    float startingPriceRenewableSum;
    public float renewableConsumed = 0;
    public float fossilConsumed = 0;
    public float fossilPriceSum = 0;
    public float renewablePriceSum = 0;
    public float totalMoneySpent = 0;

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
        this.startingPriceRenewableSum = calculateStarterTaskRenewablePrice();
    }

    @Override
    public void init() {

        provider.calculatePrice();

        if (this.requirement) {
            if (this.provider.renewableBattery.getBatteryLevel() < startingPriceRenewableSum) {
                new DeferredEvent(1000) {
                    @Override
                    protected void eventAction() {
                        provider.calculatePrice();
                        init();
                    }
                };
            } else {
                processStartingJobsWithRenewable();
            }
        }
        else {
            if ( (this.provider.renewablePrice <= this.provider.fossilBasePrice) && (this.provider.renewableBattery.getBatteryLevel() >= startingPriceRenewableSum) ) {
                processStartingJobsWithRenewable();
            } else {
                processStartingJobsWithFossil();
            }
        }
    }

    private void processStartingJobsWithRenewable() {

        assignStartingVMs();

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
                this.totalMoneySpent += getJobRenewableConsumptionPrice(workflowJob);
                this.totalMoneySpent += getJobFossilConsumptionPrice(workflowJob);
                this.fossilConsumed += getJobFossilConsumption(workflowJob);
                this.renewableConsumed += getJobRenewableConsumption(workflowJob);
            }
        }
        this.provider.renewableBattery.removeBatteryLevel(startingPriceRenewableSum);
        provider.calculatePrice();
    }

    private void processStartingJobsWithFossil() {

        assignStartingVMs();

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
                this.totalMoneySpent += getJobFullFossilConsumptionPrice(workflowJob);
                this.fossilConsumed += getJobFullFossilConsumption(workflowJob);
            }
        }
    }

    private void assignStartingVMs() {
        for (RenewableWorkflowComputingAppliance ca : this.renewableComputeArchitecture) {
            ca.workflowQueue = new PriorityQueue<WorkflowJob>(new RenewableComperator());
            ca.iaas.repositories.get(0).registerObject(this.instance.va);
            try {
                ca.workflowVms.add(ca.iaas.requestVM(this.instance.va, this.instance.arc, ca.iaas.repositories.get(0), 1)[0]);
            } catch (VMManager.VMManagementException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void schedule(WorkflowJob workflowJob) {

        provider.calculatePrice();

        if (this.requirement) {
            if (!doesProviderHaveEnoughEnergy(workflowJob)) {
                new DeferredEvent(1000) {
                    @Override
                    protected void eventAction() {
                        schedule(workflowJob);
                    }
                };
            } else {
                scheduleTaskWithRenewable(workflowJob);

                WorkflowExecutor.execute(this);
            }

        }
        else {
            if ((this.provider.renewablePrice <= this.provider.fossilBasePrice) && doesProviderHaveEnoughEnergy(workflowJob)) {
                scheduleTaskWithRenewable(workflowJob);

                WorkflowExecutor.execute(this);
            }
            else {
                if (workflowJob.inputs.get(0).amount == 0) {
                    workflowJob.ca.workflowQueue.add(workflowJob);
                    this.totalMoneySpent += getJobFullFossilConsumptionPrice(workflowJob);
                    this.fossilConsumed += getJobFullFossilConsumption(workflowJob);
                    WorkflowExecutor.execute(this);
                }
            }
        }
    }

    private void scheduleTaskWithRenewable(WorkflowJob workflowJob) {
        provider.renewableBattery.removeBatteryLevel(getJobRenewableConsumption(workflowJob));
        if (workflowJob.inputs.get(0).amount == 0) {
            workflowJob.ca.workflowQueue.add(workflowJob);
            this.fossilConsumed += getJobFossilConsumption(workflowJob);
            this.renewableConsumed += getJobRenewableConsumption(workflowJob);
            provider.calculatePrice();
            this.totalMoneySpent += getJobRenewableConsumptionPrice(workflowJob);
            this.totalMoneySpent += getJobFossilConsumptionPrice(workflowJob);
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

    private ArrayList<WorkflowJob> assignConsumptionToOwnJobs(ArrayList<WorkflowJob> oldJobs) {

        ArrayList<WorkflowJob> newJobs = new ArrayList<WorkflowJob>();

        for (WorkflowJob job : oldJobs) {
            job.consumption = 15;
            newJobs.add(job);
        }

        return newJobs;
    }

    public float getJobRenewableConsumption(WorkflowJob job) {
        float hours = (float) (job.runtime / 3600);
        return hours * job.consumption * ratio/100;
    }

    public float getJobRenewableConsumptionPrice(WorkflowJob job) {
        float hours = (float) (job.runtime / 3600);
        return hours * job.consumption * ratio/100 * provider.renewablePrice;
    }

    public float getJobFossilConsumption(WorkflowJob job) {
        float hours = (float) (job.runtime / 3600);
        float fossilRatio = (float) (100 - ratio) / 100;
        return (hours * job.consumption * fossilRatio);
    }

    public float getJobFullFossilConsumption(WorkflowJob job) {
        float hours = (float) (job.runtime / 3600);
        return (hours * job.consumption);
    }

    public float getJobFossilConsumptionPrice(WorkflowJob job) {
        float hours = (float) (job.runtime / 3600);
        float fossilRatio = (float) (100 - ratio) / 100;
        return (hours * job.consumption * fossilRatio) * provider.fossilBasePrice;
    }

    public float getJobFullFossilConsumptionPrice(WorkflowJob job) {
        float hours = (float) (job.runtime / 3600);
        return hours * job.consumption * provider.fossilBasePrice;
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
        try {
            new File(new File(System.getProperty("user.dir")).getParentFile().getAbsolutePath());
            PrintStream out = new PrintStream(
                    new FileOutputStream(ScenarioBase.resultDirectory +"/bruv.txt", true), true);
            System.setOut(out);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        System.out.print(this.getJobRenewableConsumption(job));
        System.out.print("   ------------------   " +this.getJobFossilConsumption(job));
        System.out.print("   ------------------   " + job.id);
        System.out.println("   ------------------   " + Timed.getFireCount());
        if (this.provider.renewableBattery.getBatteryLevel() >= this.getJobRenewableConsumption(job)) {
            return true;
        }
        return false;
    }

    float calculateStarterTaskRenewablePrice() {
        float sum = 0;
        for (WorkflowJob workflowJob : this.jobs) {
            if (workflowJob.inputs.get(0).amount == 0) {
                sum += getJobRenewableConsumption(workflowJob);
            }
        }
        return sum;
    }

}
