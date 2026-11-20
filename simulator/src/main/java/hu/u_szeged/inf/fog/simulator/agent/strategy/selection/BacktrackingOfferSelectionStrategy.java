package hu.u_szeged.inf.fog.simulator.agent.strategy.selection;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Offer;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
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
        List<Offer> offers = new ArrayList<>();

        generateCombinations(localOffers, application, new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>(), offers);

        return offers;
    }

    private boolean generateCombinations(
            List<LocalOffer> localOffers,
            AgentApplication application,
            Set<LocalOffer> currentCombination,
            Set<Component> includedComponents,
            Set<ResourceAgent> includedAgents,
            Set<String> seenStates,
            List<Offer> offers) {

        String stateKey = currentCombination.stream()
                .flatMap(localOffer -> localOffer.placements.stream()
                        .map(placement -> localOffer.agent.name + ":" + placement.component.id + "->" + placement.capacity.node.name))
                .sorted()
                .collect(Collectors.joining(","));

        if (!seenStates.add(stateKey)) {
            return false;
        }

        if (includedComponents.size() == application.components.size()) {
            application.globalCoverageEvaluationCount++;

            Offer offer = createOffer(currentCombination, offers.size());
            offer.metrics = globalOfferEvaluator.evaluate(offer);

            double hardViolation = globalOfferEvaluator.calculateHardRequirementViolation(application, offer.metrics);

            if (hardViolation <= EPSILON) {
                offers.add(offer);
                return onlyFirstOffer;
            }

            return false;
        }

        for (LocalOffer localOffer : localOffers) {
            if (includedAgents.contains(localOffer.agent) || localOffer.placements.isEmpty()) {
                continue;
            }

            Set<Component> offeredComponents = new LinkedHashSet<>();
            boolean validOffer = true;

            for (ComponentPlacement placement : localOffer.placements) {
                if (!application.components.contains(placement.component) || includedComponents.contains(placement.component) || !offeredComponents.add(placement.component)) {
                    validOffer = false;
                    break;
                }
            }

            if (!validOffer) {
                continue;
            }

            currentCombination.add(localOffer);
            includedAgents.add(localOffer.agent);
            includedComponents.addAll(offeredComponents);

            boolean found = generateCombinations(localOffers, application, currentCombination, includedComponents, includedAgents, seenStates, offers);

            currentCombination.remove(localOffer);
            includedAgents.remove(localOffer.agent);
            includedComponents.removeAll(offeredComponents);

            if (found) {
                return true;
            }
        }

        return false;
    }

    private Offer createOffer(Set<LocalOffer> combination, int offerId) {
        Map<ResourceAgent, Set<Component>> agentComponentsMap = new HashMap<>();
        List<ComponentPlacement> selectedPlacements = new ArrayList<>();

        for (LocalOffer localOffer : combination) {
            Set<Component> selectedComponents = agentComponentsMap.computeIfAbsent(localOffer.agent, ignored -> new LinkedHashSet<>());

            for (ComponentPlacement placement : localOffer.placements) {
                selectedComponents.add(placement.component);
                selectedPlacements.add(placement);
            }
        }

        Offer offer = new Offer(agentComponentsMap, offerId);
        offer.selectedPlacements.addAll(selectedPlacements);

        return offer;
    }
}