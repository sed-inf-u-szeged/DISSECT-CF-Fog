package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.offer.LocalOffer.ComponentPlacement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExhaustiveMappingStrategy extends MappingStrategy {

    @Override
    public List<LocalOffer> generateLocalOffers(ResourceAgent agent, List<Component> components) {

        Map<Capacity, AvailableCapacity> availableCapacities = new LinkedHashMap<>();

        for (Capacity capacity : agent.capacities.values()) {
            availableCapacities.put(capacity, new AvailableCapacity(capacity));
        }

        List<LocalOffer> localOffers = new ArrayList<>();
        List<ComponentPlacement> currentPlacements = new ArrayList<>();

        generateCombinations(agent, components, 0, availableCapacities, currentPlacements, localOffers);

        return localOffers;
    }

    private void generateCombinations(
            ResourceAgent agent,
            List<Component> components,
            int componentIndex,
            Map<Capacity, AvailableCapacity> availableCapacities,
            List<ComponentPlacement> currentPlacements,
            List<LocalOffer> localOffers) {

        if (componentIndex == components.size()) {
            if (!currentPlacements.isEmpty()) {
                localOffers.add(new LocalOffer(agent, List.copyOf(currentPlacements),null));
            }

            return;
        }

        Component component = components.get(componentIndex);

        generateCombinations(agent, components, componentIndex + 1, availableCapacities, currentPlacements, localOffers);

        for (Capacity capacity : agent.capacities.values()) {
            AvailableCapacity availableCapacity = availableCapacities.get(capacity);

            if (!isMatchingPreferences(component, capacity) || !availableCapacity.canHost(component)) {
                continue;
            }

            availableCapacity.consume(component);

            currentPlacements.add(new ComponentPlacement(component, capacity));

            generateCombinations( agent, components, componentIndex + 1, availableCapacities, currentPlacements, localOffers);

            currentPlacements.remove(currentPlacements.size() - 1);

            availableCapacity.restore(component);
        }
    }
}
