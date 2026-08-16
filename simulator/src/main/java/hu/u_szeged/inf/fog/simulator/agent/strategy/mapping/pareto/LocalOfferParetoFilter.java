package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.pareto;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LocalOfferParetoFilter {

    private static final double EPSILON = 1e-9;

    public List<LocalOffer> filter(List<LocalOffer> localOffers) {
        Map<Set<Component>, List<LocalOffer>> offersByComponents = new LinkedHashMap<>();

        for (LocalOffer localOffer : localOffers) {
            Set<Component> componentSet = new LinkedHashSet<>();

            for (LocalOffer.ComponentPlacement placement : localOffer.placements) {
                componentSet.add(placement.component);
            }

            Set<Component> groupKey = Set.copyOf(componentSet);

            offersByComponents
                    .computeIfAbsent(
                            groupKey,
                            ignored -> new ArrayList<>())
                    .add(localOffer);
        }

        List<LocalOffer> filteredOffers = new ArrayList<>();

        for (List<LocalOffer> offerGroup : offersByComponents.values()) {
            for (LocalOffer candidate : offerGroup) {
                boolean dominated = false;

                for (LocalOffer other : offerGroup) {
                    if (candidate == other) {
                        continue;
                    }

                    if (dominates(other, candidate)) {
                        dominated = true;
                        break;
                    }
                }

                if (!dominated) {
                    filteredOffers.add(candidate);
                }
            }
        }

        return filteredOffers;
    }

    private boolean dominates(LocalOffer first, LocalOffer second) {
        LocalOffer.LocalMetrics firstMetrics = first.metrics;
        LocalOffer.LocalMetrics secondMetrics = second.metrics;

        boolean noWorseInMaximizedMetrics =
                firstMetrics.balance
                        >= secondMetrics.balance - EPSILON
                        && firstMetrics.utilisation
                        >= secondMetrics.utilisation - EPSILON
                        && firstMetrics.compactness
                        >= secondMetrics.compactness - EPSILON
                        && firstMetrics.bandwidth
                        >= secondMetrics.bandwidth - EPSILON;

        boolean noWorseInMinimizedMetrics =
                firstMetrics.fragmentation
                        <= secondMetrics.fragmentation + EPSILON
                        && firstMetrics.cost
                        <= secondMetrics.cost + EPSILON
                        && firstMetrics.energy
                        <= secondMetrics.energy + EPSILON
                        && firstMetrics.latency
                        <= secondMetrics.latency + EPSILON;

        boolean betterInMaximizedMetric =
                firstMetrics.balance
                        > secondMetrics.balance + EPSILON
                        || firstMetrics.utilisation
                        > secondMetrics.utilisation + EPSILON
                        || firstMetrics.compactness
                        > secondMetrics.compactness + EPSILON
                        || firstMetrics.bandwidth
                        > secondMetrics.bandwidth + EPSILON;

        boolean betterInMinimizedMetric =
                firstMetrics.fragmentation
                        < secondMetrics.fragmentation - EPSILON
                        || firstMetrics.cost
                        < secondMetrics.cost - EPSILON
                        || firstMetrics.energy
                        < secondMetrics.energy - EPSILON
                        || firstMetrics.latency
                        < secondMetrics.latency - EPSILON;

        return noWorseInMaximizedMetrics
                && noWorseInMinimizedMetrics
                && (betterInMaximizedMetric
                || betterInMinimizedMetric);
    }
}