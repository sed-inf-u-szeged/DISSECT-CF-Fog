package hu.u_szeged.inf.fog.simulator.agent.messagestrategy;

import hu.u_szeged.inf.fog.simulator.agent.GuidedResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.List;

public abstract class MessagingStrategy {
    /**
     * Filters and selects a subset of resource agents based on a specific algorithm.
     * <p>
     * The selected agents are those chosen to be networked with the given gateway agent.
     * </p>
     *
     * @param gateway the gateway resource agent for which neighbors are filtered
     * @return a list of resource agents selected to be networked with the gateway
     */
    public abstract List<GuidedResourceAgent> filterAgents(GuidedResourceAgent gateway);
}