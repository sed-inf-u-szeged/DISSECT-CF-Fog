package hu.u_szeged.inf.fog.simulator.agent;

import java.util.Random;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.node.AgentComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

public interface ResourceAgent {
    
  
    public static class Deployment extends Timed {

        AgentApplication app;
        
        int bcastMessageSize;
        
        AgentComputingAppliance aca;
        
        public Deployment(AgentApplication app, int bcastMessageSize) {
            this.app = app;
            this.bcastMessageSize = bcastMessageSize;
            int random = new Random().nextInt(ComputingAppliance.getAllComputingAppliances().size());
            this.aca = (AgentComputingAppliance) ComputingAppliance.getAllComputingAppliances().get(random);
            subscribe(1);
        }
       
        @Override
        public void tick(long fires) {
            if (this.aca.agent.getState().equals(VirtualMachine.State.RUNNING)) {
                SimLogger.logRun(this.aca.name + " picked up " + app.name + " at: " + Timed.getFireCount());
                this.aca.broadcast(this.app, 100);
                unsubscribe();
            }
        }
    }
    
    /**
     * It defines the agent's virtual image file with 0.5 GB of disk size requirement.
     */
    public static final VirtualAppliance agentVa = new VirtualAppliance("agentVa", 1, 0, false, 536870912L); 
    
    /**
     * It defines the agent's resource requirements for 
     * (1 CPU core, 0.001 processing speed and 0.5 GB of memory) the agent VM.
     */
    public static final AlterableResourceConstraints agentArc = new AlterableResourceConstraints(1, 0.001, 536870912L);
    
    public void broadcast(AgentApplication app, int bcastMessageSize);
}
