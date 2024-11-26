package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import java.util.List;
import java.util.Random;

public class Submission extends Timed {

    AgentApplication app;
    
    ResourceAgent agent;
    
    int bcastMessageSize;

    private int delay;

    
    public Submission(AgentApplication app, int bcastMessageSize, int delay) {
        
        if (ResourceAgent.resourceAgents.size() < 2) {
            SimLogger.logError("Only one RA is available in the system!");
        }
        
        this.app = app;
        this.bcastMessageSize = bcastMessageSize;
        int random = new Random().nextInt(ResourceAgent.resourceAgents.size());
        this.agent = ResourceAgent.resourceAgents.get(random);
        this.registerImages(app.components);
        this.delay = delay;
        subscribe(1);
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
                    SimLogger.logRun(agent.name + " picked up " + app.name + " at: " + Timed.getFireCount());
                    agent.broadcast(app, 100);
                    app.deploymentTime = Timed.getFireCount();
                }
            };
        }
    }
}