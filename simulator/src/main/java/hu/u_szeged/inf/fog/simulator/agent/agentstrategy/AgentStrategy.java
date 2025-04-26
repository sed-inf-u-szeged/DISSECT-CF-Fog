package hu.u_szeged.inf.fog.simulator.agent.agentstrategy;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public abstract class AgentStrategy {
    
    public abstract List<Pair<ResourceAgent, Resource>> canFulfill(ResourceAgent agent, List<Resource> resources);
}
