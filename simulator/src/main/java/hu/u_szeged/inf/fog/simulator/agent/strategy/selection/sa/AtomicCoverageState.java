package hu.u_szeged.inf.fog.simulator.agent.strategy.selection.sa;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AtomicCoverageState {

    public final List<Component> applicationComponents;

    public final List<LocalOffer> selectedOffers;

    public final Map<Component, Integer> coverageCounts;

    public final Map<ResourceAgent, Integer> selectedOfferCountsByAgent;

    public AtomicCoverageState(List<Component> applicationComponents, List<LocalOffer> selectedOffers) {
        this.applicationComponents = List.copyOf(applicationComponents);
        this.selectedOffers = List.copyOf(selectedOffers);
        this.coverageCounts = calculateCoverageCounts();
        this.selectedOfferCountsByAgent = calculateSelectedOfferCountsByAgent();
    }

    private Map<ResourceAgent, Integer> calculateSelectedOfferCountsByAgent() {
        Map<ResourceAgent, Integer> counts = new LinkedHashMap<>();

        for (LocalOffer localOffer : selectedOffers) {
            counts.merge(
                    localOffer.agent,
                    1,
                    Integer::sum);
        }

        return Collections.unmodifiableMap(counts);
    }

    private Map<Component, Integer> calculateCoverageCounts() {
        Map<Component, Integer> counts = new LinkedHashMap<>();

        for (Component component : applicationComponents) {
            counts.put(component, 0);
        }

        for (LocalOffer localOffer : selectedOffers) {
            for (LocalOffer.ComponentPlacement placement : localOffer.placements) {
                Integer currentCount = counts.get(placement.component);

                if (currentCount == null) {
                    throw new IllegalArgumentException("LocalOffer contains a component that does not belong to the application.");
                }

                counts.put(placement.component, currentCount + 1);
            }
        }

        return Collections.unmodifiableMap(counts);
    }

    public boolean hasAtMostOneOfferPerAgent() {
        return selectedOfferCountsByAgent.values().stream()
                .allMatch(count -> count <= 1);
    }

    public boolean isCompleteAndUnique() {
        return coverageCounts.values().stream()
                .allMatch(count -> count == 1);
    }

    public boolean isStructurallyValid() {
        return isCompleteAndUnique() && hasAtMostOneOfferPerAgent();
    }
}