package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    public List<LocalOffer> generateLocalOffers(ResourceAgent agent, List<Component> components) {

        List<LocalOffer.ComponentPlacement> placements = new ArrayList<>();

        Map<Capacity, AvailableCapacity> availableCapacities = new LinkedHashMap<>();

        for (Capacity capacity : agent.capacities.values()) {
            availableCapacities.put(capacity, new AvailableCapacity(capacity));
        }

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String componentName = entry.getKey();
            String resourceAgentName = entry.getValue();

            if (!agent.name.equals(resourceAgentName)) {
                continue;
            }

            for (Component component : components) {
                if (!component.id.equals(componentName)) {
                    continue;
                }

                for (Capacity capacity : agent.capacities.values()) {
                    AvailableCapacity availableCapacity =
                            availableCapacities.get(capacity);

                    if (isMatchingPreferences(component, capacity)
                            && availableCapacity.canHost(component)) {

                        availableCapacity.consume(component);

                        placements.add(
                                new LocalOffer.ComponentPlacement(
                                        component,
                                        capacity));

                        break;
                    }
                }
            }
        }

        if (placements.isEmpty()) {
            return List.of();
        }

        return List.of(new LocalOffer(agent, placements,null));
    }
}
