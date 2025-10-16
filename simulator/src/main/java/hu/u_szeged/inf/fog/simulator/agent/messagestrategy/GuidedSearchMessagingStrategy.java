package hu.u_szeged.inf.fog.simulator.agent.messagestrategy;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.Offer;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import org.apache.commons.lang3.tuple.Triple;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Messaging strategy that guides resource agent selection
 * based on previous offer success, resources, and geographical distance.
 *
 * <p>
 * This strategy assigns scores to neighboring resource agents
 * to bias future selections:
 * <ul>
 *   <li>Initially, scores are assigned based on latency and geographical distance from/to the gateway.</li>
 *   <li>In each iteration, additional points are added based on the winning offer participation and available resources.</li>
 *   <li>These scores are normalized to form selection probabilities.</li>
 *   <li>Agents are then selected probabilistically, where higher scores translate into a higher chance of being selected.</li>
 * </ul>
 * <p>
 * A minimum selection probability is enforced to maintain diversity.
 * </p>
 *
 * @see hu.u_szeged.inf.fog.simulator.agent.messagestrategy.MessagingStrategy
 */
public class GuidedSearchMessagingStrategy extends MessagingStrategy {
    // Base component weights (must sum to 1.0)
    private static final double STATIC_WEIGHT = 0.3;
    private static final double RESOURCE_WEIGHT = 0.4;
    private static final double REPUTATION_WEIGHT = 0.3;  // Historical performance

    private static final double REPUTATION_DECAY = 0.9;  // Prevents unbounded growth
    private static final double LEARNING_RATE = 0.15;      // How fast to adapt
    private static final double SUCCESS_BONUS = 1.0;      // Reward for being in winning offer
    private static final double SELECTION_BONUS = 0.5;    // Reward for being selected

    /**
     * The importance of the distance in the distance-latency score between 0-1.
     */
    private static final double DISTANCE_WEIGHT = 0.2;

    /**
     * The importance of the latency in the distance-latency score between 0-1.
     */
    private static final double LATENCY_WEIGHT = 0.8;

    /**
     * Minimum chance for an agent to get selected for networking.
     */
    private static final double MIN_SELECTION_PROBABILITY = 0.15;

    /**
     * Minimum reputation score needs to be reached before the guided search actually selects the agents.
     */
    private static final double MIN_ROUNDS_TO_USE_ACTIVATE = 0;

    private Offer winningOffer;

    @Override
    public List<ResourceAgent> filterAgents(final ResourceAgent gateway) {
        List<ResourceAgent> potentialAgents = getPotentialAgents(gateway);
        System.out.println("Working gateway: " + gateway.name);

        if (isFirstRound(gateway)) {
            initializeStaticScores(gateway, potentialAgents);
            return potentialAgents;
        }

        if (gateway.servedAsGatewayCount < MIN_ROUNDS_TO_USE_ACTIVATE) {
            return potentialAgents;
        }

        applyReputationDecay(gateway);

        Map<ResourceAgent, Double> compositeScores = calculateCompositeScores(gateway, potentialAgents);
        List<ResourceAgent> selectedAgents = selectAgentsByProbability(compositeScores);

        updateReputationScores(gateway, selectedAgents, potentialAgents);

        return selectedAgents;
    }

    public void setWinningOffer(final Offer winningOffer) {
        this.winningOffer = winningOffer;
    }

    private List<ResourceAgent> getPotentialAgents(final ResourceAgent gateway) {
        return ResourceAgent.resourceAgents.stream()
                .filter(agent -> agent.service.getState().equals(VirtualMachine.State.RUNNING))
                .filter(agent -> agent.hostNode != gateway.hostNode)
                .filter(agent -> !agent.equals(gateway))
                .collect(Collectors.toList());
    }

    private boolean isFirstRound(final ResourceAgent gateway) {
        return gateway.servedAsGatewayCount == 0;
    }

    /**
     * Initialize static scores based on distance and latency (one-time calculation)
     */
    private void initializeStaticScores(ResourceAgent gateway, List<ResourceAgent> potentialAgents) {
        NetworkNode gatewayNode = gateway.hostNode.iaas.repositories.get(0);
        Map<ResourceAgent, Double> rawStaticScores = new HashMap<>();

        for (ResourceAgent agent : potentialAgents) {
            double distanceKm = gateway.hostNode.geoLocation.calculateDistance(agent.hostNode.geoLocation) / 1000.0;
            int latencyMs = getLatencyToNode(agent, gatewayNode);

            double distanceScore = 1.0 / (Math.log(distanceKm + 1) + 1);
            double latencyScore = 1.0 / (latencyMs + 1);
            double combinedScore = (DISTANCE_WEIGHT * distanceScore + LATENCY_WEIGHT * latencyScore);

            rawStaticScores.put(agent, combinedScore);
        }

        Map<ResourceAgent, Double> normalizedStatic = normalizeToRange(rawStaticScores, 0.0, 1.0);

        for (ResourceAgent agent : potentialAgents) {
            double staticScore = normalizedStatic.get(agent);
            gateway.staticScores.put(agent, staticScore);
            gateway.reputationScores.put(agent, 0.0);
        }
    }

    /**
     * Apply exponential decay to reputation scores to prevent unbounded growth
     */
    private void applyReputationDecay(ResourceAgent gateway) {
        for (Map.Entry<ResourceAgent, Double> entry : gateway.reputationScores.entrySet()) {
            double currentScore = entry.getValue();
            double decayedScore = currentScore * REPUTATION_DECAY;
            entry.setValue(decayedScore);
        }
    }

    /**
     * Calculate composite scores combining static, resource, and reputation components
     */
    private Map<ResourceAgent, Double> calculateCompositeScores(ResourceAgent gateway, List<ResourceAgent> potentialAgents) {
        Map<ResourceAgent, Double> compositeScores = new HashMap<>();
        Map<ResourceAgent, Double> resourceScores = calculateResourceScores(potentialAgents);

        for (ResourceAgent agent : potentialAgents) {
            double staticScore = gateway.staticScores.getOrDefault(agent, 0.0);
            double resourceScore = resourceScores.getOrDefault(agent, 0.0);
            double reputationScore = gateway.reputationScores.getOrDefault(agent, 0.0);

            // Weighted combination for FINAL point
            double composite = STATIC_WEIGHT * staticScore +
                    RESOURCE_WEIGHT * resourceScore +
                    REPUTATION_WEIGHT * reputationScore;

            compositeScores.put(agent, composite);

            System.out.println(agent.name + " - Static: " + String.format("%.3f", staticScore) +
                    ", Resource: " + String.format("%.3f", resourceScore) +
                    ", Reputation: " + String.format("%.3f", reputationScore) +
                    ", Composite: " + String.format("%.3f", composite));
        }

        return compositeScores;
    }

    /**
     * Calculate current resource availability scores (normalized to 0-1)
     */
    private Map<ResourceAgent, Double> calculateResourceScores(List<ResourceAgent> agents) {
        Map<ResourceAgent, Double> resourceScores = new HashMap<>();

        for (ResourceAgent agent : agents) {
            Triple<Double, Long, Long> free = agent.getAllFreeResources();
            double cpu = free.getLeft();
            double memoryGB = free.getMiddle() / (1024.0 * 1024 * 1024);
            double storageGB = free.getRight() / (1024.0 * 1024 * 1024);

            double cpuScore = Math.log(cpu + 1);
            double memoryScore = Math.log(memoryGB + 1);
            double storageScore = Math.log(storageGB + 1);

            double combinedScore = (cpuScore + memoryScore + storageScore) / 3.0;
            resourceScores.put(agent, combinedScore);
        }

        return normalizeToRange(resourceScores, 0.0, 1.0);
    }

    /**
     * Update reputation scores based on selection and winning offer participation
     */
    private void updateReputationScores(ResourceAgent gateway, List<ResourceAgent> selectedAgents, List<ResourceAgent> allAgents) {
        System.out.println(gateway.servedAsGatewayCount + " galo " + winningOffer);
        for (ResourceAgent agent : allAgents) {
            double reputationIncrement = 0.0;

            if (selectedAgents.contains(agent)) {
                reputationIncrement += SELECTION_BONUS * LEARNING_RATE;
            }

            if (winningOffer != null && winningOffer.agentResourcesMap.containsKey(agent)) {
                reputationIncrement += SUCCESS_BONUS * LEARNING_RATE;
                agent.winningOfferSelectionCount++;
            }

            // NEW: Add diversity bonus - agents that haven't won recently get a small boost
            if (agent.winningOfferSelectionCount == 0 && gateway.servedAsGatewayCount > 2) {
                reputationIncrement += 0.05;  // Small boost for unexplored agents
            }

            double currentReputation = gateway.reputationScores.getOrDefault(agent, 0.0);
            double newReputation = Math.min(1.0, currentReputation + reputationIncrement);

            gateway.reputationScores.put(agent, Math.max(0.0, newReputation));
        }
    }

    private int getLatencyToNode(ResourceAgent agent, NetworkNode targetNode) {
        try {
            String targetName = targetNode.getName();
            Map<String, Integer> latencies = agent.hostNode.iaas.repositories.get(0).getLatencies();

            return latencies.getOrDefault(targetName, Integer.MAX_VALUE);
        } catch (Exception e) {
            SimLogger.logRes("Failed to read latency for agent " + agent.name);
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Select agents probabilistically based on composite scores
     */
    private List<ResourceAgent> selectAgentsByProbability(Map<ResourceAgent, Double> compositeScores) {
        Map<ResourceAgent, Double> probabilities = normalizeToProbabilities(compositeScores);

        List<ResourceAgent> selected = new ArrayList<>();
        for (Map.Entry<ResourceAgent, Double> entry : probabilities.entrySet()) {
            double randomValue = SeedSyncer.centralRnd.nextDouble();

            if (randomValue <= entry.getValue()) {
                selected.add(entry.getKey());
            }
        }

        return selected;
    }

    /**
     * Normalize scores to probability range [MIN_SELECTION_PROBABILITY, 1.0]
     */
    private Map<ResourceAgent, Double> normalizeToProbabilities(Map<ResourceAgent, Double> scores) {
        double min = scores.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double range = max - min;

        Map<ResourceAgent, Double> probabilities = new HashMap<>();
        for (Map.Entry<ResourceAgent, Double> entry : scores.entrySet()) {
            double normalized = (range > 1e-9) ? (entry.getValue() - min) / range : 0.5;
            double probability = MIN_SELECTION_PROBABILITY + normalized * (1.0 - MIN_SELECTION_PROBABILITY);

            probabilities.put(entry.getKey(), probability);
        }

        return probabilities;
    }

    /**
     * Generic normalization to specified range
     */
    private Map<ResourceAgent, Double> normalizeToRange(Map<ResourceAgent, Double> scores, double minVal, double maxVal) {
        double min = scores.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double range = max - min;

        Map<ResourceAgent, Double> normalized = new HashMap<>();
        for (Map.Entry<ResourceAgent, Double> entry : scores.entrySet()) {
            double norm = (range > 1e-9) ? (entry.getValue() - min) / range : 0.5;
            double scaled = minVal + norm * (maxVal - minVal);
            normalized.put(entry.getKey(), scaled);
        }

        return normalized;
    }
}