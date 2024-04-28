package hu.u_szeged.inf.fog.simulator.workflow.scheduler;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.workflow.DecentralizedWorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;

import java.util.*;


public class DecentralizedWorkflowScheduler extends WorkflowScheduler {

    public int defaultLatency;
    public HashMap<ComputingAppliance, Instance> workflowArchitecture;
    public ArrayList<Actuator> actuatorArchitecture;
    public ArrayList<WorkflowJob> workflowJobs = new ArrayList<>();

    public DecentralizedWorkflowScheduler(LinkedHashMap<ComputingAppliance, Instance> workflowArchitecture,
                                          ArrayList<Actuator> actuatorArchitecutre, int defaultLatency) {
        this.workflowArchitecture = workflowArchitecture;
        this.actuatorArchitecture = actuatorArchitecutre;
        this.defaultLatency = defaultLatency;
    }

    @Override
    public void schedule(WorkflowJob workflowJob) {
        ArrayList<ComputingAppliance> allComputingAppliances = new ArrayList<>(workflowArchitecture.keySet());
        if (workflowJob.id.contains("service") || workflowJob.id.contains("ID")) {
            if (workflowJob.ca == null) {
                Random r = new Random();
                workflowJob.ca = allComputingAppliances.get(r.nextInt(allComputingAppliances.size()));
            }
            if (workflowJob.inputs.get(0).amount == 0) {
                workflowJob.ca.workflowQueue.add(workflowJob);
            }
        }
        if (workflowJob.id.contains("actuator")) {
            if (workflowJob.actuator == null) {
                Random r = new Random();
                workflowJob.actuator = actuatorArchitecture.get(r.nextInt(actuatorArchitecture.size()));
            }
            if (workflowJob.inputs.get(0).amount == 0) {
                workflowJob.actuator.actuatorWorkflowQueue.add(workflowJob);
            }

            if (Timed.getFireCount() > 0) {
                // this.actuatorReAssign(workflowJob, Actuator.allActuator.get(0));
            }
        }

    }
    public void schedule() {
        ArrayList<ComputingAppliance> allComputingAppliances = new ArrayList<>(workflowArchitecture.keySet());
        ArrayList<WorkflowJob> wjs = new ArrayList<>();
        for(WorkflowJob wj : workflowJobs){
            if(wj.id.contains("service") || wj.id.contains("actuator") || wj.id.contains("ID")){
                wjs.add(wj);
            }
        }
        wjs.sort(new WorkflowComperator());
        for(WorkflowJob workflowJob : workflowJobs){
            if (workflowJob.id.contains("service") || workflowJob.id.contains("ID")) {
                if (workflowJob.ca == null) {
                    Random r = new Random();
                    workflowJob.ca = allComputingAppliances.get(r.nextInt(allComputingAppliances.size()));
                }
                if (workflowJob.inputs.get(0).amount == 0) {
                    workflowJob.ca.workflowQueue.add(workflowJob);
                }
            }
            if (workflowJob.id.contains("actuator")) {
                if (workflowJob.actuator == null) {
                    Random r = new Random();
                    workflowJob.actuator = actuatorArchitecture.get(r.nextInt(actuatorArchitecture.size()));
                }
                if (workflowJob.inputs.get(0).amount == 0) {
                    workflowJob.actuator.actuatorWorkflowQueue.add(workflowJob);
                }

                if (Timed.getFireCount() > 0) {
                    // this.actuatorReAssign(workflowJob, Actuator.allActuator.get(0));
                }
            }
        }
    }

    @Override
    public void init() {
        for (ComputingAppliance ca : workflowArchitecture.keySet()) {
            Instance i = workflowArchitecture.get(ca);
            ca.iaas.repositories.get(0).registerObject(i.va);
            try {
                ca.workflowVMs.add(ca.iaas.requestVM(i.va, i.arc, ca.iaas.repositories.get(0), 1)[0]);
            } catch (VMManagementException e) {
                e.printStackTrace();
            }
        }

        for (ComputingAppliance ca : workflowArchitecture.keySet()) {
            ca.workflowQueue = new PriorityQueue<WorkflowJob>(new WorkflowComperator());
        }
        for (Actuator a : actuatorArchitecture) {
            a.actuatorWorkflowQueue = new PriorityQueue<WorkflowJob>(new WorkflowComperator());
        }
    }

    public void jobReAssign(WorkflowJob workflowJob, ComputingAppliance futureAppliance) {

        if (workflowJob.state.equals(WorkflowJob.State.SUBMITTED) && workflowJob.ca != futureAppliance
                && workflowJob.underRecieving == 0 && workflowJob.inputs.get(0).amount == 0) {

            workflowJob.state = WorkflowJob.State.REASSIGNING;
            workflowJob.FileRecievedByAssigning = 0;

            for (StorageObject so : workflowJob.filesRecieved) {
                System.out.println(so.size + " " + Timed.getFireCount() + " " + " asdasdadssd");
                try {
                    workflowJob.ca.iaas.repositories.get(0).requestContentDelivery(so.id,
                            futureAppliance.iaas.repositories.get(0), new ConsumptionEventAdapter() {

                                @Override
                                public void conComplete() {
                                    workflowJob.ca.iaas.repositories.get(0).deregisterObject(so.id);
                                    workflowJob.FileRecievedByAssigning += so.size;
                                    if (workflowJob.FileRecievedByAssigning == workflowJob.fileRecieved) {
                                        workflowJob.ca = futureAppliance;
                                        workflowJob.state = WorkflowJob.State.SUBMITTED;
                                        //schedule(workflowJob);
                                        if (DecentralizedWorkflowExecutor.jobReassigns.get(workflowJob) == null) {
                                            DecentralizedWorkflowExecutor.jobReassigns.put(workflowJob, 1);
                                        } else {
                                            DecentralizedWorkflowExecutor.jobReassigns.put(workflowJob,
                                                    DecentralizedWorkflowExecutor.jobReassigns.get(workflowJob) + 1);
                                        }
                                    }
                                }
                            });
                } catch (NetworkNode.NetworkException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class WorkflowComperator implements Comparator<WorkflowJob> {
        @Override
        public int compare(WorkflowJob o1, WorkflowJob o2) {
            return Integer.compare(o1.inputs.get(0).amount,o2.inputs.get(0).amount);
        }
    }
    class ComputingApplianceComperator implements Comparator<ComputingAppliance> {
        @Override
        public int compare(ComputingAppliance ca1, ComputingAppliance ca2) {
            return Integer.compare(ca1.workflowQueue.size(), ca2.workflowQueue.size());
        }
    }
}
