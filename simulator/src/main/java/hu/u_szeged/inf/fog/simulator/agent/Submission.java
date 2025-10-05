package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentApplicationReader;
import java.util.HashMap;
import java.util.List;

public class Submission extends Timed {

    AgentApplication app;
    
    ResourceAgent agent;
    
    int bcastMessageSize;

    private int delay;

    
    public Submission(String filepath, int bcastMessageSize, int delay, HashMap<String, Number> configuration) {
    
        SimLogger.logRun("Application description " + filepath 
            + " was submitted at: " + Timed.getFireCount() / 1000.0 / 60.0 + " min.");
        
        if (ResourceAgent.resourceAgents.size() < 2) {
            SimLogger.logError("Only one RA is available in the system!");
        }
        
        this.app = AgentApplicationReader.readAgentApplications(filepath);
        this.app.configuration = configuration;
        this.bcastMessageSize = bcastMessageSize;
        int random = SeedSyncer.centralRnd.nextInt(ResourceAgent.resourceAgents.size());
        this.agent = ResourceAgent.resourceAgents.get(random);
        this.registerImages(app.components);
        this.delay = delay;
        subscribe(10);
    }
   
    private void registerImages(List<Component> components) {
        for (Component component : components) {
            if (component.type.equals("compute")) {
                VirtualAppliance va = new VirtualAppliance(component.name, 1, 0, false, Long.parseLong(component.image));
                Deployment.registryService.registerObject(va);
            }
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
            unsubscribe();
            new DeferredEvent(this.delay) {

                @Override
                protected void eventAction() {
                    SimLogger.logRun(agent.name + " picked up application " + app.name + " at: " 
                        + Timed.getFireCount() / 1000.0 / 60.0 + " min.");
                    agent.broadcast(app, 100);
                    app.deploymentTime = Timed.getFireCount();
                }
            };
        }
    }
}