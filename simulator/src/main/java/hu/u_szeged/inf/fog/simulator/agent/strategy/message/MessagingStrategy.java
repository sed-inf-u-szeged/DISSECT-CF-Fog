package hu.u_szeged.inf.fog.simulator.agent.strategy.message;

import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.List;

public abstract class MessagingStrategy {
    
    /**
     * Filters and selects a subset of resource agents based on a specific algorithm.
     * The selected agents are those chosen to be networked with the given gateway agent.
     *
     * @param gateway the gateway resource agent for which neighbors are filtered
     * @return a list of resource agents selected to be networked with the gateway
     */
    public abstract List<ResourceAgent> filterAgents(ResourceAgent gateway);
}