package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import hu.u_szeged.inf.fog.simulator.agent.offer.LocalOffer;
import org.apache.commons.lang3.tuple.Pair;

public class DirectMappingStrategy extends MappingStrategy {

    Map<String, String> mapping;
    
    public DirectMappingStrategy(Map<String, String> mapping) {
        this.mapping = mapping;
    }

    @Override
    public List<LocalOffer> generateLocalOffers(
            ResourceAgent agent,
            List<Component> components) {

        List<LocalOffer.ComponentPlacement> placements = new ArrayList<>();

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String componentName = entry.getKey();
            String resourceAgentName = entry.getValue();

            if (agent.name.equals(resourceAgentName)) {
                for (Component component : components) {
                    if (component.id.equals(componentName)) {
                        for (Capacity capacity : agent.capacities.values()) {
                            if (isMatchingPreferences(component, capacity)
                                    && hasSufficientCapacity(component, capacity)) {

                                placements.add(new LocalOffer.ComponentPlacement(component,capacity));

                                capacity.reserveCapacity(component, agent);
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (placements.isEmpty()) {
            return List.of();
        }

        return List.of(new LocalOffer(agent, placements, null));
    }
}
