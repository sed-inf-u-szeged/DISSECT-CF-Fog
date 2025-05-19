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
import java.util.*;

public class RenewableScheduler extends WorkflowScheduler {

    public ArrayList<Provider> providers;
    int ratio;
    boolean requirement;
    ArrayList<RenewableWorkflowComputingAppliance> renewableComputeArchitecture;
    Map<Provider, Float> startingCosts = new HashMap<>();
    public List<int[]> consumptions = new ArrayList<>();
    public long totalWaitingTime = 0;
    boolean hasJobsBeenAssigned = false;

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
                           ArrayList<Provider> providers, int ratio, boolean requirement) throws Exception {
        createConsumptionValues();
        this.computeArchitecture = computeArchitecture;
        this.instance = instance;
        this.jobs = jobs.getRight();
        this.appName = jobs.getLeft();
        WorkflowScheduler.schedulers.add(this);
        this.ratio = ratio;
        this.requirement = requirement;
        this.providers = providers;
        this.renewableComputeArchitecture = convertToRenewabelAppliance(computeArchitecture);
        this.jobs = assignConsumptionToOwnJobs(jobs.getRight());
    }

    @Override
    public void init() {

        if (!hasJobsBeenAssigned) {
            hasJobsBeenAssigned = true;
            assignJobsToNodes();
            calculateStarterTaskRenewablePrice();
        }

        recalculateProviderPrices();

        if (this.requirement) {
            if (!allProviderHasEnoughToStart()) {
                this.totalWaitingTime += 1000;
                new DeferredEvent(1000) {
                    @Override
                    protected void eventAction() {
                        recalculateProviderPrices();
                        init();
                    }
                };
            } else {
                processStartingJobsWithRenewable();
            }
        }
        else {
            assignStartingVMs();
            for (Provider provider : providers) {
                if (provider.renewableBattery.getBatteryLevel() >= startingCosts.get(provider) && provider.getRenewablePrice() <= provider.getFossilPrice()) {

                    provider.renewableBattery.removeBatteryLevel(startingCosts.get(provider));
                    assignStartingVMs();

                    for (WorkflowJob workflowJob : this.jobs) {
                        RenewableWorkflowComputingAppliance ca = (RenewableWorkflowComputingAppliance) workflowJob.ca;
                        if (workflowJob.inputs.get(0).amount == 0 && Objects.equals(ca.provider.id, provider.id)) {
                            workflowJob.ca.workflowQueue.add(workflowJob);
                            logJobRenewableCunsumption(workflowJob);
                        }
                    }
                } else {

                    for (WorkflowJob workflowJob : this.jobs) {

                        RenewableWorkflowComputingAppliance ca = (RenewableWorkflowComputingAppliance) workflowJob.ca;
                        if (workflowJob.inputs.get(0).amount == 0 && Objects.equals(ca.provider.id, provider.id)) {
                            workflowJob.ca.workflowQueue.add(workflowJob);
                            logJobFossilCunsumption(workflowJob);
                        }
                    }

                }
            }
        }
    }

    private void logJobRenewableCunsumption(WorkflowJob workflowJob) {
        Provider provider = getProviderOfJob(workflowJob);
        provider.totalRenewableUsed += getJobRenewableConsumption(workflowJob);
        provider.totalFossilUsed += getJobFossilConsumption(workflowJob);
        provider.moneySpentOnRenewable += getJobRenewableConsumptionPrice(workflowJob);
        provider.moneySpentOnFossil += getJobFossilConsumptionPrice(workflowJob);
    }

    private void logJobFossilCunsumption(WorkflowJob workflowJob) {
        Provider provider = getProviderOfJob(workflowJob);
        provider.totalFossilUsed += getJobFullFossilConsumption(workflowJob);
        provider.moneySpentOnFossil += getJobFullFossilConsumptionPrice(workflowJob);
    }

    private void processStartingJobsWithRenewable() {

        assignStartingVMs();

        for (WorkflowJob workflowJob : this.jobs) {

            if (workflowJob.inputs.get(0).amount == 0) {
                workflowJob.ca.workflowQueue.add(workflowJob);
                logJobRenewableCunsumption(workflowJob);
            }
        }

        for (Map.Entry entry: startingCosts.entrySet()) {
            Provider provider = (Provider) entry.getKey();
            float value = (float) entry.getValue();
            provider.renewableBattery.removeBatteryLevel(value);
        }

        recalculateProviderPrices();
    }

    private void assignJobsToNodes() {
        int nodeIndex = 0;
        for (WorkflowJob workflowJob : this.jobs) {
            workflowJob.ca = this.computeArchitecture.get(nodeIndex);
            if (nodeIndex == this.computeArchitecture.size() - 1) {
                nodeIndex = 0;
            } else {
                nodeIndex++;
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

        recalculateProviderPrices();

        if (this.requirement) {
            if (!doesProviderHaveEnoughEnergy(workflowJob)) {
                this.totalWaitingTime += 1000;
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
            if ((getProviderOfJob(workflowJob).getRenewablePrice() <= getProviderOfJob(workflowJob).getFossilPrice()) && doesProviderHaveEnoughEnergy(workflowJob)) {
                scheduleTaskWithRenewable(workflowJob);

                WorkflowExecutor.execute(this);
            }
            else {
                if (workflowJob.inputs.get(0).amount == 0) {
                    workflowJob.ca.workflowQueue.add(workflowJob);
                    logJobFossilCunsumption(workflowJob);
                    WorkflowExecutor.execute(this);
                }
            }
        }
    }

    private void scheduleTaskWithRenewable(WorkflowJob workflowJob) {
        getProviderOfJob(workflowJob).renewableBattery.removeBatteryLevel(getJobRenewableConsumption(workflowJob));
        if (workflowJob.inputs.get(0).amount == 0) {
            workflowJob.ca.workflowQueue.add(workflowJob);
            recalculateProviderPrices();
            logJobRenewableCunsumption(workflowJob);
        }

        for (WorkflowComputingAppliance wca : this.computeArchitecture) {
            int vmCount = 0;
            int jobCount = 0;
            for (VirtualMachine vm : wca.iaas.listVMs()) {
                jobCount += vm.underProcessing.size();
                vmCount++;
            }
            if (jobCount / vmCount > 1) {
                this.addVm(wca, 1);
            } else if (countRunningVms(wca) > 1) {
                this.shutdownVm(wca, 1);
            }
        }
    }

    private void recalculateProviderPrices() {
        for (Provider provider : this.providers) {
            provider.calculatePrice();
        }
    }

    private Provider getProviderOfJob(WorkflowJob workflowJob) {
        RenewableWorkflowComputingAppliance ca = (RenewableWorkflowComputingAppliance) workflowJob.ca;

        return ca.provider;
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
        int randomArray = 0;
        int randomElement = 0;

        for (WorkflowJob job : oldJobs) {
            randomArray = (int)(Math.random() * consumptions.size());
            randomElement = (int)(Math.random() * consumptions.get(randomArray).length);
            job.consumption = consumptions.get(randomArray)[randomElement];
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
        return hours * job.consumption * ratio/100 * getProviderOfJob(job).getRenewablePrice();
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
        return (hours * job.consumption * fossilRatio) * getProviderOfJob(job).getFossilPrice();
    }

    public float getJobFullFossilConsumptionPrice(WorkflowJob job) {
        float hours = (float) (job.runtime / 3600);
        return hours * job.consumption * getProviderOfJob(job).getFossilPrice();
    }

    private ArrayList<RenewableWorkflowComputingAppliance> convertToRenewabelAppliance(ArrayList<WorkflowComputingAppliance> cas) throws Exception {
        ArrayList<RenewableWorkflowComputingAppliance> rcas= new ArrayList<>();
        for (WorkflowComputingAppliance ca : cas) {

            RenewableWorkflowComputingAppliance rca = (RenewableWorkflowComputingAppliance) ca;

            rcas.add(rca);
        }
        return rcas;
    }

    boolean doesProviderHaveEnoughEnergy(WorkflowJob job) {
        //logJobConsumptions(job);

        RenewableWorkflowComputingAppliance ca = (RenewableWorkflowComputingAppliance) job.ca;

        return ca.provider.renewableBattery.getBatteryLevel() >= getJobRenewableConsumption(job);
    }

    void logJobConsumptions(WorkflowJob job) {
        try {
            new File(new File(System.getProperty("user.dir")).getParentFile().getAbsolutePath());
            PrintStream out = new PrintStream(
                    new FileOutputStream(ScenarioBase.resultDirectory +"/consumption.txt", true), true);
            System.setOut(out);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        System.out.print(this.getJobRenewableConsumption(job));
        System.out.print("   ------------------   " +this.getJobFossilConsumption(job));
        System.out.print("   ------------------   " + job.id);
        System.out.println("   ------------------   " + Timed.getFireCount());
    }

    private void calculateStarterTaskRenewablePrice() {

        for (Provider provider : this.providers) {
            this.startingCosts.put(provider, 0F);
        }

        for (WorkflowJob workflowJob : this.jobs) {
            RenewableWorkflowComputingAppliance renewableca = (RenewableWorkflowComputingAppliance) workflowJob.ca;
            Provider key = renewableca.provider;
            this.startingCosts.put(key, this.startingCosts.get(key) + getJobRenewableConsumption(workflowJob));
        }

    }

    private boolean allProviderHasEnoughToStart () {

        boolean hasEnough = true;
        for (Map.Entry element : this.startingCosts.entrySet()) {
            Provider key = (Provider) element.getKey();
            float value = (Float) element.getValue();
            if (key.renewableBattery.getBatteryLevel() < value) {
                hasEnough = false;
            }
        }
        return hasEnough;
    }

    void createConsumptionValues() {
        consumptions.add(new int[]{2090, 2450});
        consumptions.add(new int[]{800, 1600, 2300, 1200, 1500, 2400});
        consumptions.add(new int[]{1600, 760, 2772});
        consumptions.add(new int[]{370, 25, 245});
        consumptions.add(new int[]{27, 33, 36});
        consumptions.add(new int[]{271, 700, 613});
        consumptions.add(new int[]{38, 45, 5, 33});
        consumptions.add(new int[]{7, 10, 9});
        consumptions.add(new int[]{590, 420, 560, 730});
        consumptions.add(new int[]{800, 700, 1000});
        consumptions.add(new int[]{66});
    }

}
