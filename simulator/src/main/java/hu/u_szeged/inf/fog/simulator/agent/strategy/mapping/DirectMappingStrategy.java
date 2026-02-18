package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

public class DirectMappingStrategy extends MappingStrategy {

    Map<String, String> mapping;
    
    public DirectMappingStrategy(Map<String, String> mapping) {
        this.mapping = mapping;
    }
    
    @Override
    public List<Pair<ResourceAgent, Component>> canFulfill(ResourceAgent agent, List<Component> components) {
        
        List<Pair<ResourceAgent, Component>> agentResourcePair = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String componentName = entry.getKey();
            String ra = entry.getValue();

            if (agent.name.equals(ra)) {
                for (Component component : components) {
                    if (component.id.equals(componentName)) {
                        for (Capacity c : agent.capacities.values()) {
                            if (c.cpu >= component.requirements.cpu) {
                                agentResourcePair.add(Pair.of(agent, component));
                                c.reserveCapacity(component);
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
