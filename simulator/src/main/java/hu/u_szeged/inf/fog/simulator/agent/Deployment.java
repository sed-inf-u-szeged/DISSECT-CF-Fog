package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import java.util.List;
import java.util.Random;

public class Deployment extends Timed {

    AgentApplication app;
    
    ResourceAgent agent;
    
    int bcastMessageSize;

    public static Repository imageRegistry;
    
    public Deployment(AgentApplication app, int bcastMessageSize) {
        this.app = app;
        this.bcastMessageSize = bcastMessageSize;
        int random = new Random().nextInt(ResourceAgent.resourceAgents.size());
        //this.agent = ResourceAgent.resourceAgents.get(random);
        this.agent = ResourceAgent.resourceAgents.get(2);
        this.registerImages(app.components);
        subscribe(1);
    }
   
    private void registerImages(List<Component> components) {
        for (Component component : components) {
            if (component.type.equals("compute")) {
                StorageObject so = new StorageObject(component.name, Long.parseLong(component.image), false);
                Deployment.imageRegistry.registerObject(so);
            }
        }
    }

    public static void setImageRegistry(Repository repository, int latency) {
        Deployment.imageRegistry = repository;
        try {
            imageRegistry.setState(NetworkNode.State.RUNNING);
        } catch (NetworkException e) {
            e.printStackTrace();
        }
        for (ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            imageRegistry.addLatencies(ca.iaas.repositories.get(0).getName(), latency);
        }   
    }
    
    private boolean checkRaStatus() {
        for (ResourceAgent ra : ResourceAgent.resourceAgents) {
            if (!ra.service.getState().equals(VirtualMachine.State.RUNNING)) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public void tick(long fires) {
        if (this.checkRaStatus()) {
            SimLogger.logRun(agent.name + " picked up " + app.name + " at: " + Timed.getFireCount());
            this.agent.broadcast(this.app, 100);
            unsubscribe();
        }
    }
}