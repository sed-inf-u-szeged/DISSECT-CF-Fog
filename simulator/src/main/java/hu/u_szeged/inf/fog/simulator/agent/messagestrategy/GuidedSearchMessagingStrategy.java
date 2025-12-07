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
 * based on previous selection/offer success, resources, and geographical distance.
 *
 * <p>
 * This strategy assigns scores to neighboring resource agents
 * to bias future selections:
 * <ul>
 *   <li>Initially, scores are assigned based on latency and geographical distance from/to the gateway.</li>
 *   <li>In each iteration, additional points are added based on the previous selections and available resources.</li>
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
    private static final double RESOURCE_WEIGHT = 0.5;
    private static final double REPUTATION_WEIGHT = 0.2;  // Historical performance

    private static final double REPUTATION_DECAY = 0.9;  // Prevents unbounded growth
    private static final double LEARNING_RATE = 0.2;      // How fast to adapt with reputation [1,0]
    private static final double SUCCESS_BONUS = 1.0;      // Reward for being in winning offer
    private static final double SELECTION_BONUS = 0.25;    // Reward for being selected

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

    final double EPSILON = 1e-9;

    private Offer winningOffer; // will only be used after ack rounds (potential rebroadcasts)

    @Override
    public List<ResourceAgent> filterAgents(final ResourceAgent gateway) {
        List<ResourceAgent> potentialAgents = getPotentialAgents(gateway);

        if (isFirstRound(gateway)) {
            initializeStaticScores(gateway, potentialAgents);
            return potentialAgents;
        }

        applyReputationDecay(gateway);

        Map<ResourceAgent, Double> compositeScores = calculateCompositeScores(gateway, potentialAgents);
        List<ResourceAgent> selectedAgents = selectAgentsByProbability(compositeScores);

        if (selectedAgents.isEmpty()) {
            List<ResourceAgent> topScorers = compositeScores.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(Math.max(1, potentialAgents.size() / 2))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            SimLogger.logRun("WARNING: Empty selection for gateway " + gateway.name +
                    ". Returning top " + topScorers.size() + " agents by composite score.");
            return topScorers;
        }

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

            double distanceScore = 1.0 / (Math.log(distanceKm + EPSILON) + EPSILON);
            double latencyScore = 1.0 / (latencyMs + EPSILON);
            double combinedScore = (DISTANCE_WEIGHT * distanceScore + LATENCY_WEIGHT * latencyScore);

            rawStaticScores.put(agent, combinedScore);
        }

        Map<ResourceAgent, Double> normalizedStatic = normalizeToRange(rawStaticScores, 0.2, 1.0);

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

            double composite = STATIC_WEIGHT * staticScore +
                    RESOURCE_WEIGHT * resourceScore +
                    REPUTATION_WEIGHT * reputationScore;

            compositeScores.put(agent, composite);
            /*
            System.out.println(agent.name + " - Static: " + String.format("%.3f", staticScore) +
                    ", Resource: " + String.format("%.3f", resourceScore) +
                    ", Reputation: " + String.format("%.3f", reputationScore) +
                    ", Composite: " + String.format("%.3f", composite));
             */
        }

        return compositeScores;
    }

    /**
     * Calculate current resource availability scores (normalized to 0-1)
     */
    private Map<ResourceAgent, Double> calculateResourceScores(List<ResourceAgent> agents) {
        Map<ResourceAgent, Double> rawScores = new HashMap<>();

        double minCpu = Double.MAX_VALUE, maxCpu = 0;
        double minMemory = Double.MAX_VALUE, maxMemory = 0;
        double minStorage = Double.MAX_VALUE, maxStorage = 0;

        for (ResourceAgent agent : agents) {
            Triple<Double, Long, Long> free = agent.getAllFreeResources();
            double cpu = free.getLeft();
            double memoryGB = free.getMiddle() / (1024.0 * 1024 * 1024);
            double storageGB = free.getRight() / (1024.0 * 1024 * 1024);

            minCpu = Math.min(minCpu, cpu);
            maxCpu = Math.max(maxCpu, cpu);
            minMemory = Math.min(minMemory, memoryGB);
            maxMemory = Math.max(maxMemory, memoryGB);
            minStorage = Math.min(minStorage, storageGB);
            maxStorage = Math.max(maxStorage, storageGB);
        }

        for (ResourceAgent agent : agents) {
            Triple<Double, Long, Long> free = agent.getAllFreeResources();
            double cpu = free.getLeft();
            double memoryGB = free.getMiddle() / (1024.0 * 1024 * 1024);
            double storageGB = free.getRight() / (1024.0 * 1024 * 1024);

            double cpuScore = (maxCpu > minCpu) ? (cpu - minCpu) / (maxCpu - minCpu) : 1.0;
            double memoryScore = (maxMemory > minMemory) ? (memoryGB - minMemory) / (maxMemory - minMemory) : 1.0;
            double storageScore = (maxStorage > minStorage) ? (storageGB - minStorage) / (maxStorage - minStorage) : 1.0;

            double combinedScore = (cpuScore + memoryScore + storageScore) / 3.0;
            rawScores.put(agent, combinedScore);
        }

        return normalizeToRange(rawScores, 0.2, 1.0);
    }

    /**
     * Update reputation scores based on selection and winning offer participation
     */
    private void updateReputationScores(ResourceAgent gateway, List<ResourceAgent> selectedAgents, List<ResourceAgent> allAgents) {
        for (ResourceAgent agent : allAgents) {
            double reputationIncrement = 0.0;

            if (selectedAgents.contains(agent)) {
                reputationIncrement += SELECTION_BONUS * LEARNING_RATE;
            }

            if (winningOffer != null && winningOffer.agentResourcesMap.containsKey(agent)) {
                reputationIncrement += SUCCESS_BONUS * LEARNING_RATE;
                agent.winningOfferSelectionCount++;
            }

            // Add diversity bonus - agents that haven't won yet get a small boost
            if (agent.winningOfferSelectionCount == 0 && gateway.servedAsGatewayCount > 2) {
                reputationIncrement += 0.05;
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
            double probability = entry.getValue();
            /*
            System.out.println(entry.getKey().name + " - Prob: " + String.format("%.3f", probability)
                    + ", Rand: " + String.format("%.3f", randomValue)
                    + " → " + (randomValue <= probability ? "Selected" : "Not selected"));

             */
            if (randomValue <= probability) {
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
            double normalized = (range > EPSILON) ? (entry.getValue() - min) / range : 0.5;
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
            double norm = (range > EPSILON) ? (entry.getValue() - min) / range : 0.5;
            double scaled = minVal + norm * (maxVal - minVal);
            normalized.put(entry.getKey(), scaled);
        }

        return normalized;
    }
}