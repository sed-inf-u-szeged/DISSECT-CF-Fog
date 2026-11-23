
package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RandomFirstFitMappingStrategy extends MappingStrategy {

    @Override
    public List<LocalOffer> generateLocalOffers(ResourceAgent agent, AgentApplication application) {
        List<Component> components = new ArrayList<>(application.components);
        Collections.shuffle(components, SeedSyncer.centralRnd);

        List<Capacity> capacities = new ArrayList<>(agent.capacities.values());
        Collections.shuffle(capacities, SeedSyncer.centralRnd);

        List<ComponentPlacement> placements = new ArrayList<>();
        Map<Capacity, AvailableCapacity> availableCapacities = new LinkedHashMap<>();

        for (Capacity capacity : capacities) {
            availableCapacities.put(capacity, new AvailableCapacity(capacity));
        }

        for (Component component : components) {
            for (Capacity capacity : capacities) {
                AvailableCapacity availableCapacity = availableCapacities.get(capacity);

                if (isMatchingPreferences(component, capacity)
                        && availableCapacity.canHost(component)) {
                    availableCapacity.consume(component);
                    placements.add(new ComponentPlacement(component, capacity));
                    break;
                }
            }
        }

        if (placements.isEmpty()) {
            return List.of();
        }

        application.localCandidateEvaluationCount++;
        return List.of(new LocalOffer(agent, placements, null));
    }
}
