package hu.u_szeged.inf.fog.simulator.agent.agentstrategy;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

public class DirectMappingAgentStrategy extends AgentStrategy {

    Map<String, String> mapping;
    
    public DirectMappingAgentStrategy(Map<String, String> mapping) {
        this.mapping = mapping;
    }
    
    @Override
    public List<Pair<ResourceAgent, Resource>> canFulfill(ResourceAgent agent, List<Resource> resources) {
        
        List<Pair<ResourceAgent, Resource>> agentResourcePair = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String resourceName = entry.getKey();
            String ra = entry.getValue();

            if (agent.name.equals(ra)) {

                for (Resource resource : resources) {
                    if (resource.name.equals(resourceName)) {
                        for (Capacity c : agent.capacities) {
                            if (c.cpu >= resource.cpu) {
                                agentResourcePair.add(Pair.of(agent, resource));
                                c.reserveCapacity(resource);
                                 
                                break;
                            }
                        }  
                    }
                }
            }
        }
        return agentResourcePair;
    }
}
