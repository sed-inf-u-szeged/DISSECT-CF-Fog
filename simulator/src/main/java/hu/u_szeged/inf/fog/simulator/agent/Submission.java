package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.util.AgentApplicationReader;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Submission extends Timed {

    AgentApplication app;
    
    ResourceAgent agent;
    
    int bcastMessageSize;
    
    public Submission(String filepath, int bcastMessageSize) {
    
        SimLogger.logRun("Application description " + new File(filepath).getName()
            + " was submitted at: " + Timed.getFireCount() /  ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
        
        if (ResourceAgent.allResourceAgents.size() < 2) {
            SimLogger.logError("Only one RA is available in the system!");
        }
        
        this.app = AgentApplicationReader.readJson(filepath);
        this.app.submissionTime = Timed.getFireCount();
        this.bcastMessageSize = bcastMessageSize;
        List<ResourceAgent> agents = new ArrayList<>(ResourceAgent.allResourceAgents.values());
        agent = agents.get(SeedSyncer.centralRnd.nextInt(agents.size()));
        this.registerImages(app.components);
        subscribe(1_000);
    }
   
    private void registerImages(List<Component> components) {
        for (Component component : components) {
            VirtualAppliance va = new VirtualAppliance(component.id, 1, 0, false, component.properties.image);
            Deployment.registryService.registerObject(va);
        }
    }

    private boolean checkRaStatus() {
        for (ResourceAgent ra : ResourceAgent.allResourceAgents.values()) {
            if (!ra.raService.getState().equals(VirtualMachine.State.RUNNING)) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public void tick(long fires) {
        if (this.checkRaStatus()) {
            unsubscribe();
            SimLogger.logRun(agent.name + " picked up application " + app.name + " at: "
                    + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
            agent.broadcast(app, 100);
            app.deploymentTime = Timed.getFireCount();
        }
    }
}