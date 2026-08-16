package hu.u_szeged.inf.fog.simulator.agent.strategy.selection;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.Offer;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalMetricsCalculator;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.LocalMetrics;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.sa.AtomicCoverageState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GlobalOfferEvaluator {

    private static final double EPSILON = 1e-9;

    private final LocalMetricsCalculator localMetricsCalculator = new LocalMetricsCalculator();

    public double calculateHardRequirementViolation(AgentApplication application, GlobalOfferMetrics metrics) {
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

    private double calculateMaximumViolation(double actualValue, double maximumValue) {
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

    public GlobalOfferMetrics evaluate(Offer offer) {
        int providerCount = offer.agentComponentsMap.size();

        double totalCost = 0.0;
        double weightedLatencySum = 0.0;
        double weightedBandwidthSum = 0.0;
        int totalPlacementCount = 0;

        for (Map.Entry<ResourceAgent, Set<AgentApplication.Component>> entry : offer.agentComponentsMap.entrySet()) {
            ResourceAgent agent = entry.getKey();
            List<ComponentPlacement> agentPlacements = new ArrayList<>();

            for (ComponentPlacement placement : offer.selectedPlacements) {
                if (entry.getValue().contains(placement.component)) {
                    agentPlacements.add(placement);
                }
            }

            if (agentPlacements.isEmpty()) {
                throw new IllegalStateException("Offer contains an agent without selected placements: " + agent.name);
            }

            LocalMetrics localMetrics = localMetricsCalculator.calculate(agent, agentPlacements);
            int placementCount = agentPlacements.size();

            totalCost += localMetrics.cost;
            weightedLatencySum += localMetrics.latency * placementCount;
            weightedBandwidthSum += localMetrics.bandwidth * placementCount;
            totalPlacementCount += placementCount;
        }

        double totalProjectedPower = localMetricsCalculator.calculateProjectedPower(offer.selectedPlacements);
        double averageLatency = totalPlacementCount == 0 ? 0.0 : weightedLatencySum / totalPlacementCount;
        double averageBandwidth = totalPlacementCount == 0 ? 0.0 : weightedBandwidthSum / totalPlacementCount;

        return new GlobalOfferMetrics(providerCount, totalCost, totalProjectedPower, averageLatency, averageBandwidth);
    }

    public GlobalOfferMetrics evaluate(AtomicCoverageState state) {
        int providerCount = state.selectedOfferCountsByAgent.size();

        double totalCost = 0.0;
        double weightedLatencySum = 0.0;
        double weightedBandwidthSum = 0.0;
        int totalPlacementCount = 0;

        List<ComponentPlacement> selectedPlacements = new ArrayList<>();

        for (LocalOffer offer : state.selectedOffers) {
            int placementCount = offer.placements.size();

            totalCost += offer.metrics.cost;
            weightedLatencySum += offer.metrics.latency * placementCount;
            weightedBandwidthSum += offer.metrics.bandwidth * placementCount;
            totalPlacementCount += placementCount;
            selectedPlacements.addAll(offer.placements);
        }

        double totalProjectedPower = localMetricsCalculator.calculateProjectedPower(selectedPlacements);
        double averageLatency = totalPlacementCount == 0 ? 0.0 : weightedLatencySum / totalPlacementCount;
        double averageBandwidth = totalPlacementCount == 0 ? 0.0 : weightedBandwidthSum / totalPlacementCount;

        return new GlobalOfferMetrics(providerCount, totalCost, totalProjectedPower, averageLatency, averageBandwidth);
    }

    public double calculateQosUtility(AgentApplication application, GlobalOfferMetrics metrics) {
        double totalWeight = application.price + application.energy + application.latency + application.bandwidth;

        if (totalWeight <= EPSILON) {
            return 0.0;
        }

        double weightedScore = 0.0;

        if (application.price > EPSILON) {
            weightedScore += application.price * normalizeMinimizedMetric(metrics.cost, application.maxCost, "maxCost");
        }

        if (application.energy > EPSILON) {
            weightedScore += application.energy
                    * normalizeMinimizedMetric(metrics.energy, application.maxEnergyConsumption, "maxEnergyConsumption");
        }

        if (application.latency > EPSILON) {
            weightedScore += application.latency * normalizeMinimizedMetric(metrics.latency, application.maxLatency, "maxLatency");
        }

        if (application.bandwidth > EPSILON) {
            weightedScore += application.bandwidth
                    * normalizeMaximizedMetric(metrics.bandwidth, application.minBandwidth, "minBandwidth");
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
}
