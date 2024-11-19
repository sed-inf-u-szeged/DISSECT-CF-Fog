package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Mapping;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import org.apache.commons.lang3.tuple.Pair;

public class Deployment extends Timed {
    
    public static Repository registryService;
    
    Pair<ComputingAppliance, Utilisation> leadResource;
    
    Offer offer;
    
    VirtualMachine leadResourceVm;
    
    AgentApplication app;

    public Deployment(Pair<ComputingAppliance, Utilisation> leadResource, Offer offer, AgentApplication app) {
        this.app = app;
        deployLeadResource(leadResource);
        subscribe(1);
    }
    
    private void deployLeadResource(Pair<ComputingAppliance, Utilisation> leadResource) {
        ComputingAppliance node = leadResource.getLeft();
        Utilisation utilisation = leadResource.getRight();
        
        AlterableResourceConstraints arc = new AlterableResourceConstraints(utilisation.utilisedCpu, 0.001, utilisation.utilisedMemory);
        
        VirtualAppliance va = null;
        for (StorageObject so : registryService.contents()) {
            String component = app.getComponentForResource(utilisation.resource.name);
            if (so.id.equals(component)) {
                va = (VirtualAppliance) so;
            }
        }
        
        try {
            this.leadResourceVm = node.iaas.requestVM(va, arc,
                    registryService, 1)[0];
        } catch (VMManagementException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void tick(long fires) {
        if (this.leadResourceVm.getState().equals(VirtualMachine.State.RUNNING)) {
            SimLogger.logRun("Lead Resource for " + this.app.name + " was initilised at: " + Timed.getFireCount());
            unsubscribe();

            // TODO: set ALLOCATED state for the capacity
            // TODO: here the rest of the deployment should be initialized
            
            for (ResourceAgent agent : ResourceAgent.resourceAgents) {
                for (Capacity cap : agent.capacities) {
                    System.out.println(cap);
                    for (Utilisation util : cap.utilisations) {
                        System.out.println(util);
                    }
                }
            }            
        }
    }
}
