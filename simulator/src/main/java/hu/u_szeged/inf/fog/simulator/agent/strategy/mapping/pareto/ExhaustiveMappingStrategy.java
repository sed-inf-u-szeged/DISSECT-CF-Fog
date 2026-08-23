package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.pareto;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.MappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalMetricsCalculator;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExhaustiveMappingStrategy extends MappingStrategy {

    private final LocalMetricsCalculator metricsCalculator;

    private final LocalOfferParetoFilter paretoFilter;

    public ExhaustiveMappingStrategy() {
        this.metricsCalculator =  new LocalMetricsCalculator();
        this.paretoFilter = new LocalOfferParetoFilter();
    }

    @Override
    public List<LocalOffer> generateLocalOffers(ResourceAgent agent, AgentApplication application) {

        Map<Capacity, AvailableCapacity> availableCapacities = new LinkedHashMap<>();

        for (Capacity capacity : agent.capacities.values()) {
            availableCapacities.put(capacity, new AvailableCapacity(capacity));
        }

        List<LocalOffer> localOffers = new ArrayList<>();
        List<ComponentPlacement> currentPlacements = new ArrayList<>();

        generateCombinations(agent, application.components, 0, availableCapacities, currentPlacements, localOffers);

        application.localCandidateEvaluationCount += localOffers.size();
        application.localOffersBeforePareto += localOffers.size();

        List<LocalOffer> filteredOffers = paretoFilter.filter(localOffers);

        application.localOffersAfterPareto += filteredOffers.size();

        return filteredOffers;
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
                List<ComponentPlacement> placements = List.copyOf(currentPlacements);
                localOffers.add(new LocalOffer(agent, placements, metricsCalculator.calculate( agent, placements)));
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
