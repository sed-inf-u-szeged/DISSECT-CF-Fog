package hu.u_szeged.inf.fog.simulator.workflow;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.iot.Sensor;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob.Uses;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.WorkflowScheduler;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;



public class WorkflowExecutor {

    public static WorkflowScheduler workflowScheduler;

    public static HashMap<Integer, Integer> vmTaskLogger = new HashMap<Integer, Integer>();

    public static HashMap<WorkflowJob, Integer> actuatorReassigns = new HashMap<WorkflowJob, Integer>();

    public static HashMap<WorkflowJob, Integer> jobReassigns = new HashMap<WorkflowJob, Integer>();

    public static long realStartTime = 0;

    public WorkflowExecutor(WorkflowScheduler workflowScheduler) {
        WorkflowExecutor.workflowScheduler = workflowScheduler;

        WorkflowExecutor.workflowScheduler.init();

        for (WorkflowJob workflowJob : WorkflowJob.workflowJobs) {
            WorkflowExecutor.workflowScheduler.schedule(workflowJob);
        }

        this.checkFirstVmState();

    }

    private void checkFirstVmState() {
        new DeferredEvent(1) {

            @Override
            protected void eventAction() {
                if (checkComputingAppliances()) {
                    startSensors();
                    execute();
                    WorkflowExecutor.realStartTime = Timed.getFireCount();

                } else {
                    checkFirstVmState();
                }
            }

        };
    }

    public static void execute() {
        if (WorkflowScheduler.actuatorArchitecture != null) {
            for (Actuator a : WorkflowScheduler.actuatorArchitecture) {
                WorkflowJob workflowJob = a.actuatorWorkflowQueue.poll();
                if (workflowJob != null && a.isWorking == false) {
                    workflowJob.state = WorkflowJob.State.STARTED;
                    a.isWorking = true;
                    long actuatorStartTime = Timed.getFireCount();
                    new DeferredEvent(a.delay) {

                        @Override
                        protected void eventAction() {
                            workflowJob.state = WorkflowJob.State.COMPLETED;
                            a.isWorking = false;
                            a.actuatorEventList
                                    .add(new TimelineEntry(actuatorStartTime, Timed.getFireCount(), workflowJob.id));
                            execute();
                        }
                    };
                } else if (workflowJob != null && a.isWorking) {
                    a.actuatorWorkflowQueue.add(workflowJob);
                }
            }
        }

        for (WorkflowComputingAppliance ca : WorkflowScheduler.workflowArchitecture.keySet()) {
            int size = ca.workflowQueue.size();
            for (int i = 0; i < size; i++) {
                WorkflowJob workflowJob = ca.workflowQueue.poll();
                System.out.println(workflowJob.id + " is peeked at " + Timed.getFireCount());

                if (workflowJob.inputs.get(0).amount == 0 && workflowJob.state.equals(WorkflowJob.State.SUBMITTED)) {
                    workflowJob.state = WorkflowJob.State.STARTED;
                    VirtualMachine vm = findVm(workflowJob);

                    try {
                        WorkflowJob.numberOfStartedWorkflowJobs++;
                        long vmStartTime = Timed.getFireCount();
                        System.out.println(workflowJob.id + " is running at " + Timed.getFireCount() + " on "
                                + workflowJob.ca.name);
                        double noi;
                        if (workflowJob.runtime == 0) {
                            noi = workflowJob.fileRecieved
                                    * WorkflowScheduler.workflowArchitecture.get(ca).processingRatio;
                        } else {
                            noi = 1000 * workflowJob.runtime * vm.getResourceAllocation().allocated.getRequiredCPUs()
                                    * vm.getResourceAllocation().allocated.getRequiredProcessingPower();
                        }
                        long vmTimeStart = Timed.getFireCount();
                        vm.newComputeTask(noi, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {

                            @Override
                            public void conComplete() {
                                workflowJob.ca.vmTime += Timed.getFireCount() - vmTimeStart;
                                workflowJob.ca.timelineList.add(new TimelineEntry(vmStartTime, Timed.getFireCount(),
                                        Integer.toString(vm.hashCode()) + "-" + workflowJob.id));
                                workflowJob.state = WorkflowJob.State.COMPLETED;
                                System.out.println(workflowJob.id + " is finished at " + Timed.getFireCount() + " on "
                                        + workflowJob.ca.name);
                                sendFileToChildren(workflowJob);
                                if (vmTaskLogger.get(vm.hashCode()) == null) {
                                    vmTaskLogger.put(vm.hashCode(), 1);
                                } else {
                                    vmTaskLogger.put(vm.hashCode(), vmTaskLogger.get(vm.hashCode()) + 1);
                                }
                            }
                        });

                    } catch (NetworkException e) {
                        e.printStackTrace();
                    }
                }

            }
        }
    }

    private static WorkflowJob findWorkflowJob(String id) {
        for (WorkflowJob workflowJob : WorkflowJob.workflowJobs) {
            if (workflowJob.id.equals(id)) {
                return workflowJob;
            }
        }
        return null;
    }

    private static VirtualMachine findVm(WorkflowJob workflowJob) {
        VirtualMachine virtualMachine = null;
        int min = Integer.MAX_VALUE;
        for (VirtualMachine vm : workflowJob.ca.iaas.listVMs()) {
            if (vm.getState().equals(VirtualMachine.State.RUNNING) && vm.underProcessing.size() < min) {
                virtualMachine = vm;
                min = vm.underProcessing.size();
            }
        }

        return virtualMachine;
    }

    private boolean checkComputingAppliances() {
        int vmCount = 0;
        for (WorkflowComputingAppliance ca : WorkflowScheduler.workflowArchitecture.keySet()) {
            for (VirtualMachine vm : ca.workflowVms) {
                if (vm.getState().equals(VirtualMachine.State.RUNNING)) {
                    vmCount++;
                    break;
                }
            }
        }
        if (vmCount == WorkflowScheduler.workflowArchitecture.size()) {
            return true;
        }
        return false;
    }

    private static void sendFileToChildren(WorkflowJob currentJob) {
        for (Uses uses : currentJob.outputs) {
            if (uses.type.equals(Uses.Type.ACTUATE)) {
                for (WorkflowJob wj : WorkflowJob.workflowJobs) {
                    if (wj.id.equals(uses.id)) {
                        wj.inputs.get(0).amount--;
                        workflowScheduler.schedule(wj);
                        execute();
                    }
                    continue;
                }
            } else if (uses.type.equals(Uses.Type.DATA)) {

                for (WorkflowJob wj : WorkflowJob.workflowJobs) {
                    if (wj.id.equals(uses.id)) {
                        StorageObject so = new StorageObject(uses.id + "-" + currentJob.id, uses.size, false);
                        currentJob.ca.iaas.repositories.get(0).registerObject(so);
                        WorkflowJob childWorkflowJob = wj;
                        System.out.println(currentJob.id + " sends " + uses.size + " bytes to " + childWorkflowJob.id
                                + " at " + Timed.getFireCount());
                        if (childWorkflowJob.ca == currentJob.ca) {
                            childWorkflowJob.inputs.get(0).amount--;
                            childWorkflowJob.fileRecieved += uses.size;
                            childWorkflowJob.filesRecieved.add(so);
                            System.out
                                    .println(childWorkflowJob.id + " amount: " + childWorkflowJob.inputs.get(0).amount);
                            workflowScheduler.schedule(childWorkflowJob);
                            execute();
                        } else {
                            try {
                                childWorkflowJob.underRecieving++;
                                currentJob.ca.iaas.repositories.get(0).requestContentDelivery(so.id,
                                        childWorkflowJob.ca.iaas.repositories.get(0), new ConsumptionEventAdapter() {

                                            @Override
                                            public void conComplete() {
                                                childWorkflowJob.underRecieving--;
                                                childWorkflowJob.inputs.get(0).amount--;
                                                childWorkflowJob.fileRecieved += uses.size;
                                                childWorkflowJob.filesRecieved.add(so);
                                                System.out.println(childWorkflowJob.id + " amount: "
                                                        + childWorkflowJob.inputs.get(0).amount);
                                                workflowScheduler.schedule(childWorkflowJob);
                                                execute();
                                            }
                                        });
                            } catch (NetworkException e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                    }
                }
            }

        }
    }

    private void startSensors() {
        for (WorkflowJob workflowJob : WorkflowJob.workflowJobs) {
            if (workflowJob.id.contains("sensor")) {

                HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();
                EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = null;

                transitions = PowerTransitionGenerator.generateTransitions(0, 0, 0, 0, 0);
                final Map<String, PowerState> stTransitions = transitions
                        .get(PowerTransitionGenerator.PowerStateKind.storage);
                final Map<String, PowerState> nwTransitions = transitions
                        .get(PowerTransitionGenerator.PowerStateKind.network);

                // TODO: refactor!
                Repository repo = new Repository(1024, "repo" + workflowJob.id, 1024, 1024, 1024, latencyMap,
                        stTransitions, nwTransitions);
                try {
                    repo.setState(NetworkNode.State.RUNNING);
                } catch (NetworkException e1) {
                    e1.printStackTrace();
                }
                for (Uses uses : workflowJob.outputs) {
                    workflowJob.state = WorkflowJob.State.STARTED;
                    new DeferredEvent(uses.activate) {

                        @Override
                        protected void eventAction() {
                            StorageObject so = new StorageObject(workflowJob.id + "_" + uses.id, uses.size, false);

                            repo.registerObject(so);
                            WorkflowJob childWorkflowJob = findWorkflowJob(uses.id);
                            GeoLocation gl = new GeoLocation(workflowJob.latitude, workflowJob.longitude);
                            int finalLatency = (int) (WorkflowExecutor.workflowScheduler.defaultLatency
                                    + gl.calculateDistance(childWorkflowJob.ca.geoLocation) / 1000);

                            latencyMap.put(childWorkflowJob.ca.iaas.repositories.get(0).getName(), finalLatency);
                            try {

                                long sensorStartTime = Timed.getFireCount();
                                repo.requestContentDelivery(workflowJob.id + "_" + uses.id,
                                        childWorkflowJob.ca.iaas.repositories.get(0), new ConsumptionEventAdapter() {

                                            @Override
                                            public void conComplete() {
                                                Sensor.sensorEventList.add(new TimelineEntry(sensorStartTime,
                                                        Timed.getFireCount(), workflowJob.id));
                                                workflowJob.state = WorkflowJob.State.COMPLETED;
                                                childWorkflowJob.inputs.get(0).amount--;
                                                childWorkflowJob.fileRecieved += uses.size;
                                                childWorkflowJob.filesRecieved.add(so);
                                                System.out.println(childWorkflowJob.id + " amount: "
                                                        + childWorkflowJob.inputs.get(0).amount);
                                                WorkflowExecutor.workflowScheduler.schedule(childWorkflowJob);
                                                WorkflowExecutor.execute();
                                            }
                                        });
                            } catch (NetworkException e) {
                                e.printStackTrace();
                            }
                        }
                    };

                }

            }
        }

    }

}