package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import org.apache.commons.lang3.tuple.Pair;

public class Deployment extends Timed {
    
    private static int taskNum = 0;
    public static Repository registryService;
    
    Pair<ComputingAppliance, Utilisation> leadResource;
    
    Offer offer;
    
    AgentApplication app;

    public Deployment(Pair<ComputingAppliance, Utilisation> leadResource, Offer offer, AgentApplication app) {
        this.app = app;
        this.offer = offer;
        this.leadResource = leadResource;
        subscribe(1);
    }
    
    public static void setImageRegistry(Repository repository) {
        registryService = repository;
        try {
            registryService.setState(NetworkNode.State.RUNNING);
        } catch (NetworkException e) {
            e.printStackTrace();
        }
    }

    private void deployResource(Pair<ComputingAppliance, Utilisation> resource) {
        ComputingAppliance node = resource.getLeft();
        Utilisation utilisation = resource.getRight();
                
        AlterableResourceConstraints arc = new AlterableResourceConstraints(utilisation.utilisedCpu, 1, utilisation.utilisedMemory);
        VirtualAppliance va = (VirtualAppliance) registryService.lookup(app.getComponentName(utilisation.resource.name));
       
        try {
            utilisation.vm = node.iaas.requestVM(va, arc, registryService, 1)[0];
            utilisation.initTime = Timed.getFireCount();
        } catch (VMManagementException e) {
            e.printStackTrace();
        }
    }
    
    private boolean checkRemainingDeployment() {
        for (Pair<ComputingAppliance, Utilisation> util : this.offer.utilisations) {
            if (util.getRight().utilisedCpu > 0 && !util.getRight().vm.getState().equals(VirtualMachine.State.RUNNING)) {
                return false;
            } else {
                util.getRight().setToAllocated();
            }
        }
        return true;
    }

    @Override
    public void tick(long fires) {     
        if (leadResource != null && this.leadResource.getRight().vm == null) {
            this.deployResource(this.leadResource);
        } else if (leadResource != null && this.leadResource.getRight().vm.getState().equals(VirtualMachine.State.RUNNING)) {
            this.leadResource.getRight().setToAllocated();
            SimLogger.logRun("Lead Resource for " + this.app.name + " was initilised at: " + Timed.getFireCount());
            unsubscribe();

            new Deployment(null, this.offer, this.app);
        }
                
        if (this.leadResource == null && offer.isRemainingDeploymentStarted == false) {
            offer.isRemainingDeploymentStarted = true;
            for (Pair<ComputingAppliance, Utilisation> util : this.offer.utilisations) {
                if (util.getRight().utilisedCpu > 0 && util.getRight().type == null) {
                    this.deployResource(util);
                } 
            }
        } else if (this.leadResource == null && checkRemainingDeployment() == true) {
            SimLogger.logRun("Remaining resources for " + this.app.name + " were initilised at: " + Timed.getFireCount());
            app.deploymentTime = Timed.getFireCount() - app.deploymentTime;
            unsubscribe();
            
            for (Pair<ComputingAppliance, Utilisation> util : this.offer.utilisations) {
                if (util.getRight().utilisedCpu > 0) {
                    
                    long actualTime = Timed.getFireCount();
                    taskNum++;
                    try {
                        util.getRight().vm.newComputeTask(30 * 60 * 1000, ResourceConsumption.unlimitedProcessing, 
                                new ConsumptionEventAdapter() {
                            
                                @Override
                                public void conComplete() {
                                    SimLogger.logRun(
                                            util.getRight().resource.name + " completed at: " + Timed.getFireCount() 
                                            + " in " + (Timed.getFireCount() - actualTime) / 1000 / 60 + " min.");
                                    taskNum--;
                                    if (taskNum == 0) {
                                        for (EnergyDataCollector ec : EnergyDataCollector.energyCollectors) {
                                            ec.stop();
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
    }
}
