package hu.u_szeged.inf.fog.simulator.workflow.scheduler;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.StateChangeException;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import java.util.ArrayList;
import java.util.TreeMap;

public abstract class WorkflowScheduler {
    
    public static ArrayList<WorkflowScheduler> schedulers = new ArrayList<WorkflowScheduler>();
    
    public TreeMap<String, Integer> vmTaskLogger = new TreeMap<>();

    public ArrayList<WorkflowComputingAppliance> computeArchitecture;

    public ArrayList<Actuator> actuatorArchitecture;
    
    public ArrayList<WorkflowJob> jobs;

    public Instance instance;
    
    public long startTime;
    
    public long stopTime;

    public String appName;
    
    public long timeOnNetwork;

    public long bytesOnNetwork;

    WorkflowExecutor workflowExecutor;

    public int defaultLatency;

    public abstract void schedule(WorkflowJob workflowJob);

    public abstract void init();
    
    public void addVm(WorkflowComputingAppliance ca) {
        try {
            ca.workflowVms.add(ca.iaas.requestVM(this.instance.va, this.instance.arc, ca.iaas.repositories.get(0), 1)[0]);
        } catch (VMManagementException e) {
            e.printStackTrace();
        }
    }

    public void shutdownVm(WorkflowComputingAppliance ca) {
        for (VirtualMachine vm : ca.iaas.listVMs()) {
            if (vm.getState().equals(VirtualMachine.State.RUNNING) && vm.underProcessing.isEmpty()) {
                try {
                    vm.switchoff(false);
                } catch (StateChangeException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
    }
    
    /*
    public void jobReAssign(WorkflowJob workflowJob, ComputingAppliance futureAppliance) {

        if (workflowJob.state.equals(WorkflowJob.State.SUBMITTED) && workflowJob.ca != futureAppliance
                && workflowJob.underRecieving == 0 && workflowJob.inputs.get(0).amount > 0) {

            workflowJob.state = WorkflowJob.State.REASSIGNING;
            workflowJob.fileRecievedByAssigning = 0;

            for (StorageObject so : workflowJob.filesRecieved) {
                System.out.println(so.size + " " + Timed.getFireCount() + " " + " asdasdadssd");
                try {
                    workflowJob.ca.iaas.repositories.get(0).requestContentDelivery(so.id,
                            futureAppliance.iaas.repositories.get(0), new ConsumptionEventAdapter() {

                                @Override
                                public void conComplete() {
                                    workflowJob.ca.iaas.repositories.get(0).deregisterObject(so.id);
                                    workflowJob.fileRecievedByAssigning += so.size;
                                    if (workflowJob.fileRecievedByAssigning == workflowJob.fileRecieved) {
                                        workflowJob.ca = (WorkflowComputingAppliance) futureAppliance;
                                        workflowJob.state = WorkflowJob.State.SUBMITTED;
                                        // schedule(workflowJob); ?
                                        if (WorkflowExecutor.jobReassigns.get(workflowJob) == null) {
                                            WorkflowExecutor.jobReassigns.put(workflowJob, 1);
                                        } else {
                                            WorkflowExecutor.jobReassigns.put(workflowJob,
                                                    WorkflowExecutor.jobReassigns.get(workflowJob) + 1);
                                        }
                                    }
                                }
                            });
                } catch (NetworkException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void actuatorReAssign(WorkflowJob workflowJob, Actuator futureActuator) {
        if (workflowJob.state.equals(WorkflowJob.State.SUBMITTED) && workflowJob.actuator != futureActuator) {
            if (workflowJob.actuator.actuatorWorkflowQueue.remove(workflowJob)) {
                futureActuator.actuatorWorkflowQueue.add(workflowJob);
            }
            workflowJob.actuator = futureActuator;
            if (WorkflowExecutor.actuatorReassigns.get(workflowJob) == null) {
                WorkflowExecutor.actuatorReassigns.put(workflowJob, 1);
            } else {
                WorkflowExecutor.actuatorReassigns.put(workflowJob,
                        WorkflowExecutor.actuatorReassigns.get(workflowJob) + 1);
            }
        }
    }
     */
}