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
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser.TimelineEntry;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob.Uses;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.RenewableScheduler;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.WorkflowScheduler;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class WorkflowExecutor {
    
    private static WorkflowExecutor executor;
    
    public static ArrayList<WorkflowScheduler> workflowSchedulers;

    public static WorkflowExecutor getIstance() {
        if (executor == null) {
            executor = new WorkflowExecutor();
            workflowSchedulers = new ArrayList<WorkflowScheduler>();
        }
        return executor;
    }
    
    public void submitJobs(WorkflowScheduler workflowScheduler) {
        workflowSchedulers.add(workflowScheduler);

        workflowScheduler.init();

        checkVmState(workflowScheduler);
    }

    private static void checkVmState(WorkflowScheduler workflowScheduler) {
        new DeferredEvent(1) {

            @Override
            protected void eventAction() {
                if (checkComputingAppliances(workflowScheduler)) {
                    workflowScheduler.startTime = Timed.getFireCount();
                    execute(workflowScheduler);
                    startSensors(workflowScheduler);
                } else {
                    checkVmState(workflowScheduler);
                }
            }
        };
    }
    
    private static boolean checkComputingAppliances(WorkflowScheduler workflowScheduler) {
        int vmCount = 0;
        for (WorkflowComputingAppliance ca : workflowScheduler.computeArchitecture) {
            for (VirtualMachine vm : ca.workflowVms) {
                if (vm.getState().equals(VirtualMachine.State.RUNNING)) {
                    vmCount++;
                    break;
                }
            }
        }
            
        return vmCount == workflowScheduler.computeArchitecture.size();
    }

    public static void execute(WorkflowScheduler workflowScheduler) {
        if (workflowScheduler.actuatorArchitecture != null) {
            for (Actuator actuator : workflowScheduler.actuatorArchitecture) {
                WorkflowJob workflowJob = actuator.actuatorWorkflowQueue.poll();
                if (workflowJob != null && actuator.isWorking == false) {
                    workflowJob.state = WorkflowJob.State.STARTED;
                    actuator.isWorking = true;
                    long actuatorStartTime = Timed.getFireCount();
                    new DeferredEvent(actuator.delay) {

                        @Override
                        protected void eventAction() {
                            workflowJob.state = WorkflowJob.State.COMPLETED;
                            if (isAllJobCompleted(workflowScheduler)) {
                                workflowScheduler.stopTime = Timed.getFireCount();
                                
                                for (ComputingAppliance ca : workflowScheduler.computeArchitecture) {
                                    EnergyDataCollector.getEnergyCollector(ca.iaas).stop();
                                }
                                if (workflowScheduler instanceof RenewableScheduler) {
                                    ((RenewableScheduler) workflowScheduler).provider.stopProcessing();
                                }
                            }
                            actuator.isWorking = false;
                            actuator.actuatorEventList
                                    .add(new TimelineEntry(actuatorStartTime, Timed.getFireCount(), workflowJob.id));
                            execute(workflowScheduler);
                        }
                    };
                } else if (workflowJob != null && actuator.isWorking) {
                    actuator.actuatorWorkflowQueue.add(workflowJob);
                }
            }
        }
        
        for (WorkflowComputingAppliance ca : workflowScheduler.computeArchitecture) {
            int size = ca.workflowQueue.size();
            for (int i = 0; i < size; i++) {
                WorkflowJob workflowJob = ca.workflowQueue.poll();
                workflowJob.state = WorkflowJob.State.STARTED;                
                VirtualMachine vm = findVm(ca);
                long vmStartTime = Timed.getFireCount();
                double noi;
                if (workflowJob.runtime == 0) {
                    noi = workflowJob.bytesRecieved * workflowScheduler.instance.processingRatio;
                } else {
                    noi = 1000 * workflowJob.runtime * vm.getResourceAllocation().allocated.getRequiredCPUs()
                                * vm.getResourceAllocation().allocated.getRequiredProcessingPower();
                }
                
                SimLogger.logRun(workflowScheduler.appName + "-" + workflowJob.id + " started running on:  " + workflowJob.ca.name
                        + " at: " + Timed.getFireCount());
                
                try {
                    vm.newComputeTask(noi, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {

                        @Override
                        public void conComplete() {
                            workflowJob.ca.vmTime += Timed.getFireCount() - vmStartTime;
                            workflowJob.ca.timelineList.add(new TimelineEntry(vmStartTime, Timed.getFireCount(),
                                    Integer.toString(vm.hashCode()) + "-" + workflowJob.id));
                            workflowJob.state = WorkflowJob.State.COMPLETED;
                            if (isAllJobCompleted(workflowScheduler)) {
                                workflowScheduler.stopTime = Timed.getFireCount();
                                
                                for (ComputingAppliance ca : workflowScheduler.computeArchitecture) {
                                    EnergyDataCollector.getEnergyCollector(ca.iaas).stop();
                                }
                                if (workflowScheduler instanceof RenewableScheduler) {
                                    ((RenewableScheduler) workflowScheduler).provider.stopProcessing();
                                }
                            }
                            String id = workflowJob.ca.name + "-" + Integer.toString(vm.hashCode());
                            if (workflowScheduler.vmTaskLogger.get(id) == null) {
                                workflowScheduler.vmTaskLogger.put(id, 1);
                            } else {
                                workflowScheduler.vmTaskLogger.put(id, workflowScheduler.vmTaskLogger.get(id) + 1);
                            }
                                
                            sendFileToChildren(workflowScheduler, workflowJob);
                            
                            SimLogger.logRun(workflowScheduler.appName + "-" + workflowJob.id + " finished on: " + workflowJob.ca.name 
                                + " at: " + Timed.getFireCount());
                            }
                        });
                } catch (NetworkException e) {
                    e.printStackTrace();
                }                
            }
        }
    }
    
    private static VirtualMachine findVm(WorkflowComputingAppliance ca) {
        VirtualMachine virtualMachine = null;
        int min = Integer.MAX_VALUE;
        for (VirtualMachine vm : ca.iaas.listVMs()) {
            if (vm.getState().equals(VirtualMachine.State.RUNNING) && vm.underProcessing.size() < min) {
                virtualMachine = vm;
                min = vm.underProcessing.size();
            }
        }
        
        return virtualMachine;
    }
    
    private static void sendFileToChildren(WorkflowScheduler workflowScheduler, WorkflowJob currentJob) {
        for (Uses uses : currentJob.outputs) {
            if (uses.type.equals(Uses.Type.ACTUATE)) {
                WorkflowJob wj = findWorkflowJob(uses.id);
                wj.inputs.get(0).amount--;        
                workflowScheduler.schedule(wj);
                execute(workflowScheduler);
            } else if (uses.type.equals(Uses.Type.DATA)) {
                for (WorkflowJob childWorkflowJob : workflowScheduler.jobs) {
                    if (childWorkflowJob.id.equals(uses.id)) {
                        StorageObject so = new StorageObject(uses.id + "-" + currentJob.id, uses.size, false);
                        currentJob.ca.iaas.repositories.get(0).registerObject(so);
                        
                        long time = Timed.getFireCount();
                        
                        SimLogger.logRun(workflowScheduler.appName + "-" + currentJob.id + " sent " + uses.size + " bytes to: " 
                                + childWorkflowJob.id + " at: " + Timed.getFireCount());
                        
                        if (childWorkflowJob.ca == currentJob.ca) {
                            childWorkflowJob.inputs.get(0).amount--;
                            childWorkflowJob.bytesRecieved += uses.size;
                            
                            workflowScheduler.schedule(childWorkflowJob);
                            execute(workflowScheduler);
                        } else {
                            try {
                                childWorkflowJob.underRecieving++;
                                currentJob.ca.iaas.repositories.get(0).requestContentDelivery(so.id,
                                        childWorkflowJob.ca.iaas.repositories.get(0), new ConsumptionEventAdapter() {

                                            @Override
                                            public void conComplete() {
                                                childWorkflowJob.underRecieving--;
                                                childWorkflowJob.inputs.get(0).amount--;
                                                childWorkflowJob.bytesRecieved += uses.size;
                                                workflowScheduler.timeOnNetwork += Timed.getFireCount() - time;
                                                workflowScheduler.bytesOnNetwork += uses.size;
                                                
                                                workflowScheduler.schedule(childWorkflowJob);
                                                execute(workflowScheduler);
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
    
    private static boolean isAllJobCompleted(WorkflowScheduler workflowScheduler) {
        for (WorkflowJob job : workflowScheduler.jobs) {
            if (!job.state.equals(WorkflowJob.State.COMPLETED)) {
                return false;
            }       
        }
        return true;
    }
    
    private static void startSensors(WorkflowScheduler workflowScheduler) {
        for (WorkflowJob workflowJob : workflowScheduler.jobs) {
            if (workflowJob.id.contains("sensor")) {

                HashMap<String, Integer> latencyMap = new HashMap<>();
                EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = null;

                transitions = PowerTransitionGenerator.generateTransitions(0, 0, 0, 0, 0);
                final Map<String, PowerState> stTransitions = transitions
                        .get(PowerTransitionGenerator.PowerStateKind.storage);
                final Map<String, PowerState> nwTransitions = transitions
                        .get(PowerTransitionGenerator.PowerStateKind.network);

                Repository repo = new Repository(1024, "repo-" + workflowJob.id, 1024, 1024, 1024, latencyMap,
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
                            GeoLocation location = new GeoLocation(workflowJob.latitude, workflowJob.longitude);
                            int finalLatency = (int) (workflowScheduler.defaultLatency
                                    + location.calculateDistance(childWorkflowJob.ca.geoLocation) / 1000);
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
                                                childWorkflowJob.bytesRecieved += uses.size;

                                                workflowScheduler.schedule(childWorkflowJob);
                                                execute(workflowScheduler);
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
    
    private static WorkflowJob findWorkflowJob(String id) {
        for (WorkflowJob workflowJob : WorkflowJob.workflowJobs) {
            if (workflowJob.id.equals(id)) {
                return workflowJob;
            }
        }
        return null;
    }
    
    /*
    public static HashMap<WorkflowJob, Integer> actuatorReassigns = new HashMap<WorkflowJob, Integer>();

    public static HashMap<WorkflowJob, Integer> jobReassigns = new HashMap<WorkflowJob, Integer>();
    */
}