package hu.u_szeged.inf.fog.simulator.agent.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.offer.AtomicCoverageState;
import hu.u_szeged.inf.fog.simulator.agent.offer.LocalOffer;

import java.util.*;

public class AtomicCoverageSimulatedAnnealing {

    // TODO: magic numbers
    private static final double EPSILON = 1e-9;
    private static final double ADDITIONAL_REMOVAL_PROBABILITY = 0.25;
    private final Random random = SeedSyncer.centralRnd;

    private static class GlobalMetrics {

        private final int providerCount;
        private final double cost;
        private final double energy;
        private final double latency;
        private final double bandwidth;

        private GlobalMetrics(int providerCount, double cost, double energy, double latency, double bandwidth) {
            this.providerCount = providerCount;
            this.cost = cost;
            this.energy = energy;
            this.latency = latency;
            this.bandwidth = bandwidth;
        }
    }

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

        GlobalMetrics initialMetrics = calculateGlobalMetrics(currentState);

        if (calculateHardRequirementViolation(application, initialMetrics) <= EPSILON) {
            bestFeasibleState = currentState;

            bestFeasibleQosUtility = calculateQosUtility(application, initialMetrics);
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

            GlobalMetrics neighborMetrics = calculateGlobalMetrics(neighborState);

            double neighborHardViolation = calculateHardRequirementViolation(application, neighborMetrics);

            if (neighborHardViolation <= EPSILON) {
                double neighborQosUtility = calculateQosUtility(application, neighborMetrics);

                if (bestFeasibleState == null || neighborQosUtility < bestFeasibleQosUtility) {

                    bestFeasibleState = neighborState;
                    bestFeasibleQosUtility = neighborQosUtility;
                }
            }

            currentTemperature *= coolingRate;
        }

        return bestFeasibleState;
    }

    private GlobalMetrics calculateGlobalMetrics(AtomicCoverageState state) {
        int providerCount = state.selectedOfferCountsByAgent.size();

        double totalCost = 0.0;
        double totalEnergy = 0.0;

        double weightedLatencySum = 0.0;
        double weightedBandwidthSum = 0.0;
        int totalPlacementCount = 0;

        for (LocalOffer offer : state.selectedOffers) {
            totalCost += offer.metrics.cost;
            totalEnergy += offer.metrics.energy;

            int placementCount = offer.placements.size();
            weightedLatencySum += offer.metrics.latency * placementCount;
            weightedBandwidthSum += offer.metrics.bandwidth * placementCount;
            totalPlacementCount += placementCount;
        }

        double averageLatency = totalPlacementCount == 0 ? 0.0 : weightedLatencySum / totalPlacementCount;

        double averageBandwidth = totalPlacementCount == 0 ? 0.0 : weightedBandwidthSum / totalPlacementCount;

        return new GlobalMetrics( providerCount, totalCost, totalEnergy, averageLatency, averageBandwidth);
    }

    private double calculateHardRequirementViolation(AgentApplication application, GlobalMetrics metrics) {
        double violation = 0.0;

        if (application.minProviderCount != null) {
            violation += calculateMinimumViolation(metrics.providerCount, application.minProviderCount);
        }

        if (application.maxProviderCount != null) {
            violation += calculateMaximumViolation(metrics.providerCount, application.maxProviderCount);
        }

        if (application.maxCost != null) {
            violation += calculateMaximumViolation(metrics.cost, application.maxCost);
        }

        if (application.maxLatency != null) {
            violation += calculateMaximumViolation(metrics.latency, application.maxLatency);
        }

        if (application.minBandwidth != null) {
            violation += calculateMinimumViolation(metrics.bandwidth, application.minBandwidth);
        }

        if (application.maxEnergyConsumption != null) {
            violation += calculateMaximumViolation(metrics.energy, application.maxEnergyConsumption);
        }

        return violation;
    }

    double calculateMaximumViolation(double actualValue, double maximumValue) {
        if (actualValue <= maximumValue) {
            return 0.0;
        }

        double denominator = Math.max(Math.abs(maximumValue), EPSILON);

        return (actualValue - maximumValue) / denominator;
    }

    private double calculateMinimumViolation(double actualValue, double minimumValue) {
        if (actualValue >= minimumValue) {
            return 0.0;
        }

        double denominator = Math.max(Math.abs(minimumValue), EPSILON);

        return (minimumValue - actualValue) / denominator;
    }

    private double calculateQosUtility(AgentApplication application, GlobalMetrics metrics) {
        double totalWeight = application.price + application.energy + application.latency + application.bandwidth;

        double weightedScore = 0.0;

        if (application.price > EPSILON) {
            weightedScore += application.price * normalizeMinimizedMetric(metrics.cost, application.maxCost,"maxCost");
        }

        if (application.energy > EPSILON) {
            weightedScore += application.energy * normalizeMinimizedMetric(metrics.energy, application.maxEnergyConsumption,"maxEnergyConsumption");
        }

        if (application.latency > EPSILON) {
            weightedScore += application.latency * normalizeMinimizedMetric(metrics.latency, application.maxLatency,"maxLatency");
        }

        if (application.bandwidth > EPSILON) {
            weightedScore += application.bandwidth * normalizeMaximizedMetric(metrics.bandwidth, application.minBandwidth,"minBandwidth");
        }

        return weightedScore / totalWeight;
    }

    private double normalizeMinimizedMetric(double actualValue, Double referenceValue, String requirementName) {
        if (referenceValue == null || referenceValue <= EPSILON) {
            throw new IllegalArgumentException(requirementName + " must be positive when its QoS weight is positive.");
        }

        return actualValue / referenceValue;
    }

    private double normalizeMaximizedMetric(double actualValue, Double referenceValue, String requirementName) {
        if (referenceValue == null || referenceValue <= EPSILON) {
            throw new IllegalArgumentException(requirementName + " must be positive when its QoS weight is positive.");
        }

        return referenceValue / Math.max(actualValue, EPSILON);
    }

    private double calculateEnergy(AgentApplication application, AtomicCoverageState state, double currentTemperature, double initialTemperature,
            double minimumTemperature, double initialHardPenaltyWeight, double finalHardPenaltyWeight) {

        if (!state.isStructurallyValid()) {
            throw new IllegalArgumentException("The SA can only evaluate structurally valid coverage states.");
        }

        GlobalMetrics metrics = calculateGlobalMetrics(state);

        double hardViolation = calculateHardRequirementViolation(application, metrics);
        double qosUtility = calculateQosUtility(application, metrics);

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
