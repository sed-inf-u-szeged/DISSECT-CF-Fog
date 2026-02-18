package hu.u_szeged.inf.fog.simulator.agent.strategy.message;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.List;

public class FloodingMessagingStrategy extends MessagingStrategy {
    
    @Override
    public List<ResourceAgent> filterAgents(ResourceAgent gateway) {
        return ResourceAgent.allResourceAgents.values().stream()
                .filter(agent -> agent.raService.getState() == VirtualMachine.State.RUNNING)
                .filter(agent -> agent.hostNode != gateway.hostNode)
                .filter(agent -> agent != gateway)
                .toList();
    }
}