package hu.u_szeged.inf.fog.simulator.agent.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.offer.AtomicCoverageState;
import hu.u_szeged.inf.fog.simulator.agent.offer.LocalOffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AtomicCoverageSimulatedAnnealing {

    private final Random random;

    public AtomicCoverageSimulatedAnnealing() {
        this.random = SeedSyncer.centralRnd;
    }

    public AtomicCoverageState createInitialState(AgentApplication application, List<LocalOffer> availableOffers) {
        List<LocalOffer> shuffledOffers = new ArrayList<>(availableOffers);

        Collections.shuffle(shuffledOffers, random);

        int maximumInitialOfferCount = Math.min(application.components.size(), shuffledOffers.size());

        int selectedOfferCount = maximumInitialOfferCount == 0 ? 0 : random.nextInt(maximumInitialOfferCount + 1);

        List<LocalOffer> selectedOffers = new ArrayList<>(shuffledOffers.subList(0, selectedOfferCount));

        return new AtomicCoverageState(application.components, selectedOffers);
    }

    private AtomicCoverageState addOffer(AtomicCoverageState currentState, List<LocalOffer> availableOffers) {
        List<LocalOffer> unselectedOffers =
                availableOffers.stream()
                        .filter(localOffer -> !currentState.selectedOffers.contains(localOffer))
                        .toList();

        if (unselectedOffers.isEmpty()) {
            return currentState;
        }

        List<LocalOffer> offersCoveringMissingComponents =
                unselectedOffers.stream()
                        .filter(localOffer -> localOffer.placements.stream()
                                .anyMatch(placement -> currentState.coverageCounts.get(placement.component)== 0))
                        .toList();

        List<LocalOffer> candidateOffers;

        // TODO: the hardcoded 80% should be outsourced to the config file
        if (!offersCoveringMissingComponents.isEmpty() && random.nextDouble() < 0.8) {
            candidateOffers = offersCoveringMissingComponents;
        } else {
            candidateOffers = unselectedOffers;
        }

        LocalOffer selectedOffer =candidateOffers.get(random.nextInt(candidateOffers.size()));

        List<LocalOffer> neighborOffers = new ArrayList<>(currentState.selectedOffers);

        neighborOffers.add(selectedOffer);

        return new AtomicCoverageState(currentState.applicationComponents, neighborOffers);
    }

    private AtomicCoverageState removeOffer(AtomicCoverageState currentState) {
        if (currentState.selectedOffers.isEmpty()) {
            return currentState;
        }

        List<LocalOffer> conflictingOffers =
                currentState.selectedOffers.stream()
                        .filter(localOffer -> {

                            boolean agentConflict = currentState.selectedOfferCountsByAgent.get(localOffer.agent) > 1;

                            boolean coverageConflict = localOffer.placements.stream()
                                            .anyMatch(placement -> currentState.coverageCounts.get(placement.component) > 1);

                            return agentConflict || coverageConflict;
                        })
                        .toList();

        List<LocalOffer> candidateOffers;

        if (!conflictingOffers.isEmpty() && random.nextDouble() < 0.8) {
            candidateOffers = conflictingOffers;
        } else {
            candidateOffers = currentState.selectedOffers;
        }

        LocalOffer offerToRemove = candidateOffers.get(random.nextInt(candidateOffers.size()));

        List<LocalOffer> neighborOffers = new ArrayList<>(currentState.selectedOffers);

        neighborOffers.remove(offerToRemove);

        return new AtomicCoverageState(currentState.applicationComponents, neighborOffers);
    }

    private AtomicCoverageState replaceOffer(AtomicCoverageState currentState, List<LocalOffer> availableOffers) {

        if (currentState.selectedOffers.isEmpty()) {
            return addOffer( currentState, availableOffers);
        }

        List<LocalOffer> originallyUnselectedOffers =availableOffers.stream()
                        .filter(localOffer -> !currentState.selectedOffers.contains(localOffer))
                        .toList();

        if (originallyUnselectedOffers.isEmpty()) {
            return currentState;
        }

        AtomicCoverageState stateAfterRemoval = removeOffer(currentState);

        return addOffer(stateAfterRemoval, originallyUnselectedOffers);
    }

    private AtomicCoverageState generateNeighbor(AtomicCoverageState currentState, List<LocalOffer> availableOffers) {

        if (currentState.selectedOffers.isEmpty()) {
            return addOffer(currentState, availableOffers);
        }

        int moveType = random.nextInt(3);

        return switch (moveType) {
            case 0 -> addOffer(currentState,availableOffers);
            case 1 -> removeOffer(currentState);
            default -> replaceOffer(currentState, availableOffers);
        };
    }

    private double calculateStructuralPenalty(AtomicCoverageState state) {
        double missingComponentPenalty = state.getMissingComponentCount();

        double duplicateCoveragePenalty = state.getDuplicateCoverageCount();

        double agentConflictPenalty = state.getAgentConflictCount();

        return missingComponentPenalty + duplicateCoveragePenalty + agentConflictPenalty;
    }
}