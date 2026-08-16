package hu.u_szeged.inf.fog.simulator.agent.strategy.selection.sa;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.GlobalOfferEvaluator;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.GlobalOfferMetrics;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;

import java.util.*;

public class AtomicCoverageSimulatedAnnealing {

    // TODO: magic numbers
    private static final double EPSILON = 1e-9;
    private static final double ADDITIONAL_REMOVAL_PROBABILITY = 0.25;
    private final Random random = SeedSyncer.centralRnd;
    private final GlobalOfferEvaluator globalOfferEvaluator = new GlobalOfferEvaluator();

    public AtomicCoverageState optimize(AgentApplication application, List<LocalOffer> availableOffers, int maxConstructionRestarts,
                                        int repairRestarts, int maxNeighborAttempts, int maxIterations, double initialTemperature, double minimumTemperature,
                                        double coolingRate, double initialHardPenaltyWeight, double finalHardPenaltyWeight) {

        AtomicCoverageConstructor constructor = new AtomicCoverageConstructor();

        AtomicCoverageState currentState = constructor.constructCoverage(application, availableOffers, maxConstructionRestarts);

        if (currentState == null) {
            return null;
        }

        AtomicCoverageState bestFeasibleState = null;
        double bestFeasibleQosUtility = Double.POSITIVE_INFINITY;

        GlobalOfferMetrics initialMetrics = globalOfferEvaluator.evaluate(currentState);

        if (globalOfferEvaluator.calculateHardRequirementViolation(application, initialMetrics) <= EPSILON) {
            bestFeasibleState = currentState;

            bestFeasibleQosUtility = globalOfferEvaluator.calculateQosUtility(application, initialMetrics);
        }

        double currentTemperature = initialTemperature;

        for (int iteration = 0; iteration < maxIterations && currentTemperature > minimumTemperature; iteration++) {
            AtomicCoverageState neighborState =
                    generateNeighbor(application, currentState, availableOffers, constructor, repairRestarts, maxNeighborAttempts);

            double currentEnergy =
                    calculateEnergy( application, currentState, currentTemperature, initialTemperature, minimumTemperature, initialHardPenaltyWeight, finalHardPenaltyWeight);

            double neighborEnergy =
                    calculateEnergy(application, neighborState, currentTemperature, initialTemperature, minimumTemperature, initialHardPenaltyWeight, finalHardPenaltyWeight);

            double energyDifference = neighborEnergy - currentEnergy;

            boolean acceptNeighbor;

            if (energyDifference <= 0.0) {
                acceptNeighbor = true;
            } else {
                double acceptanceProbability = Math.exp(-energyDifference / currentTemperature);

                acceptNeighbor = random.nextDouble() < acceptanceProbability;
            }

            if (acceptNeighbor) {
                currentState = neighborState;
            }

            GlobalOfferMetrics neighborMetrics = globalOfferEvaluator.evaluate(neighborState);

            double neighborHardViolation = globalOfferEvaluator.calculateHardRequirementViolation(application, neighborMetrics);

            if (neighborHardViolation <= EPSILON) {
                double neighborQosUtility = globalOfferEvaluator.calculateQosUtility(application, neighborMetrics);

                if (bestFeasibleState == null || neighborQosUtility < bestFeasibleQosUtility) {

                    bestFeasibleState = neighborState;
                    bestFeasibleQosUtility = neighborQosUtility;
                }
            }

            currentTemperature *= coolingRate;
        }

        return bestFeasibleState;
    }

    private double calculateEnergy(AgentApplication application, AtomicCoverageState state, double currentTemperature, double initialTemperature,
            double minimumTemperature, double initialHardPenaltyWeight, double finalHardPenaltyWeight) {

        if (!state.isStructurallyValid()) {
            throw new IllegalArgumentException("The SA can only evaluate structurally valid coverage states.");
        }

        GlobalOfferMetrics metrics = globalOfferEvaluator.evaluate(state);

        double hardViolation = globalOfferEvaluator.calculateHardRequirementViolation(application, metrics);
        double qosUtility = globalOfferEvaluator.calculateQosUtility(application, metrics);

        double hardPenaltyWeight = calculateHardPenaltyWeight(currentTemperature, initialTemperature, minimumTemperature,
                                             initialHardPenaltyWeight, finalHardPenaltyWeight);

        return qosUtility + hardPenaltyWeight * hardViolation;
    }

    private double calculateHardPenaltyWeight(double currentTemperature, double initialTemperature, double minimumTemperature,
                                                double initialHardPenaltyWeight, double finalHardPenaltyWeight) {

        if (initialTemperature <= minimumTemperature) {
            throw new IllegalArgumentException("Initial temperature must be greater than minimum temperature.");
        }

        double progress = (initialTemperature - currentTemperature) / (initialTemperature - minimumTemperature);

        progress = Math.max(0.0, Math.min(1.0, progress));

        return initialHardPenaltyWeight + progress * (finalHardPenaltyWeight - initialHardPenaltyWeight);
    }

    private AtomicCoverageState generateNeighbor(AgentApplication application, AtomicCoverageState currentState, List<LocalOffer> availableOffers,
            AtomicCoverageConstructor constructor, int repairRestarts, int maxNeighborAttempts) {

        if (!currentState.isStructurallyValid()) {
            throw new IllegalArgumentException("The current SA state must be structurally valid.");
        }

        if (currentState.selectedOffers.isEmpty()) {
            return currentState;
        }

        for (int attempt = 0; attempt < maxNeighborAttempts; attempt++) {
            List<LocalOffer> retainedOffers = new ArrayList<>( currentState.selectedOffers);

            Collections.shuffle(retainedOffers, random);

            int removalCount = 1;

            while (removalCount < retainedOffers.size() && random.nextDouble() < ADDITIONAL_REMOVAL_PROBABILITY) {
                removalCount++;
            }

            List<LocalOffer> removedOffers = new ArrayList<>(retainedOffers.subList( 0, removalCount));

            retainedOffers.subList(0, removalCount).clear();

            List<LocalOffer> alternativeOffers = new ArrayList<>(availableOffers);

            alternativeOffers.removeAll(removedOffers);

            AtomicCoverageState repairedState = constructor.repairCoverage(application, alternativeOffers, retainedOffers, repairRestarts);

            if (repairedState == null) {
                continue;
            }

            if (!haveSameSelectedOffers(currentState, repairedState)) {
                return repairedState;
            }
        }

        return currentState;
    }

    private boolean haveSameSelectedOffers(AtomicCoverageState first,AtomicCoverageState second) {
        return new HashSet<>(first.selectedOffers).equals(new HashSet<>(second.selectedOffers));
    }
}
