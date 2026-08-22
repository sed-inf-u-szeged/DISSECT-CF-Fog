package hu.u_szeged.inf.fog.simulator.agent.strategy.selection;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Offer;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import org.apache.commons.lang3.tuple.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BacktrackingOfferSelectionStrategy {

    private final boolean onlyFirstOffer;
    private final GlobalOfferEvaluator globalOfferEvaluator;
    private static final double EPSILON = 1e-9;

    public BacktrackingOfferSelectionStrategy(boolean onlyFirstOffer) {
        this.onlyFirstOffer = onlyFirstOffer;
        this.globalOfferEvaluator = new GlobalOfferEvaluator();
    }

    public List<Offer> selectOffers(List<LocalOffer> localOffers, AgentApplication application) {

        List<Pair<ResourceAgent, ComponentPlacement>> pairs = localOffers.stream()
                .flatMap(localOffer -> localOffer.placements.stream()
                        .map(placement -> Pair.of(localOffer.agent, placement)))
                .toList();

        List<Offer> offers = new ArrayList<>();

        generateCombinations(pairs, application, new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>(), offers);
        return offers;
    }

    private boolean generateCombinations(
            List<Pair<ResourceAgent, ComponentPlacement>> pairs,
            AgentApplication application,
            Set<Pair<ResourceAgent, ComponentPlacement>> currentCombination,
            Set<Component> includedComponents,
            Set<String> seenStates,
            List<Offer> offers) {

        if (includedComponents.size() == application.components.size()) {
            Offer offer = createOffer(currentCombination, offers.size());
            offer.metrics = globalOfferEvaluator.evaluate(offer);

            double hardViolation = globalOfferEvaluator.calculateHardRequirementViolation(application, offer.metrics);

            if (hardViolation <= EPSILON) {
                offers.add(offer);
                return onlyFirstOffer;
            }

            return false;
        }

        String stateKey = currentCombination.stream()
                .map(pair -> pair.getLeft().name
                        + ":" + pair.getRight().component.id
                        + "->" + pair.getRight().capacity.node.name)
                .sorted()
                .collect(Collectors.joining(","));

        if (!seenStates.add(stateKey)) {
            return false;
        }

        for (Pair<ResourceAgent, ComponentPlacement> pair : pairs) {
            Component component = pair.getRight().component;

            if (currentCombination.contains(pair) || includedComponents.contains(component)) {
                continue;
            }

            currentCombination.add(pair);
            includedComponents.add(component);

            boolean found = generateCombinations(
                    pairs,
                    application,
                    currentCombination,
                    includedComponents,
                    seenStates,
                    offers);

            currentCombination.remove(pair);
            includedComponents.remove(component);

            if (found) {
                return true;
            }

        }

        return false;
    }

    private Offer createOffer(Set<Pair<ResourceAgent, ComponentPlacement>> combination, int offerId) {
        Map<ResourceAgent, Set<Component>> agentComponentsMap = new HashMap<>();

        for (Pair<ResourceAgent, ComponentPlacement> pair : combination) {
            agentComponentsMap.computeIfAbsent(pair.getLeft(), ignored -> new LinkedHashSet<>())
                    .add(pair.getRight().component);
        }

        Offer offer = new Offer(agentComponentsMap, offerId);

        offer.selectedPlacements.addAll(combination.stream()
                .map(Pair::getRight)
                .toList());

        return offer;
    }
}
