package hu.u_szeged.inf.fog.simulator.agent.messagestrategy;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.Offer;
import hu.u_szeged.inf.fog.simulator.agent.GuidedResourceAgent;
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
    private static final double STATIC_WEIGHT = 0.2;
    private static final double RESOURCE_WEIGHT = 0.6;
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
    public List<GuidedResourceAgent> filterAgents(final GuidedResourceAgent gateway) {
        List<GuidedResourceAgent> potentialAgents = getPotentialAgents(gateway);

        if (isFirstRound(gateway)) {
            initializeStaticScores(gateway, potentialAgents);
            return potentialAgents;
        }

        applyReputationDecay(gateway);

        Map<GuidedResourceAgent, Double> compositeScores = calculateCompositeScores(gateway, potentialAgents);
        List<GuidedResourceAgent> selectedAgents = selectAgentsByProbability(compositeScores);

        if (selectedAgents.isEmpty()) {
            List<GuidedResourceAgent> topScorers = compositeScores.entrySet().stream()
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

    private List<GuidedResourceAgent> getPotentialAgents(final GuidedResourceAgent gateway) {
        return GuidedResourceAgent.GuidedResourceAgents.stream()
                .filter(agent -> agent.service.getState().equals(VirtualMachine.State.RUNNING))
                .filter(agent -> agent.hostNode != gateway.hostNode)
                .filter(agent -> !agent.equals(gateway))
                .collect(Collectors.toList());
    }

    private boolean isFirstRound(final GuidedResourceAgent gateway) {
        return gateway.servedAsGatewayCount == 0;
    }

    /**
     * Initialize static scores based on distance and latency (one-time calculation)
     */
    private void initializeStaticScores(GuidedResourceAgent gateway, List<GuidedResourceAgent> potentialAgents) {
        NetworkNode gatewayNode = gateway.hostNode.iaas.repositories.get(0);
        Map<GuidedResourceAgent, Double> rawStaticScores = new HashMap<>();

        for (GuidedResourceAgent agent : potentialAgents) {
            double distanceKm = gateway.hostNode.geoLocation.calculateDistance(agent.hostNode.geoLocation) / 1000.0;
            int latencyMs = getLatencyToNode(agent, gatewayNode);
            double score = -(DISTANCE_WEIGHT * distanceKm + LATENCY_WEIGHT * latencyMs);
            rawStaticScores.put(agent, score);
        }
        Map<GuidedResourceAgent, Double> normalizedStatic = normalizeToRange(rawStaticScores, 0.0, 1.0);

        for (GuidedResourceAgent agent : potentialAgents) {
            double staticScore = normalizedStatic.get(agent);
            gateway.staticScores.put(agent, staticScore);
            gateway.reputationScores.put(agent, 0.0);
        }
    }

    /**
     * Apply exponential decay to reputation scores to prevent unbounded growth
     */
    private void applyReputationDecay(GuidedResourceAgent gateway) {
        for (Map.Entry<GuidedResourceAgent, Double> entry : gateway.reputationScores.entrySet()) {
            double currentScore = entry.getValue();
            double decayedScore = currentScore * REPUTATION_DECAY;
            entry.setValue(decayedScore);
        }
    }

    /**
     * Calculate composite scores combining static, resource, and reputation components
     */
    private Map<GuidedResourceAgent, Double> calculateCompositeScores(GuidedResourceAgent gateway, List<GuidedResourceAgent> potentialAgents) {
        Map<GuidedResourceAgent, Double> compositeScores = new HashMap<>();
        Map<GuidedResourceAgent, Double> resourceScores = calculateResourceScores(potentialAgents);

        for (GuidedResourceAgent agent : potentialAgents) {
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
    private Map<GuidedResourceAgent, Double> calculateResourceScores(List<GuidedResourceAgent> agents) {
        Map<GuidedResourceAgent, Double> rawScores = new HashMap<>();
        double minCpu = Double.MAX_VALUE, maxCpu = 0;
        double minMemory = Double.MAX_VALUE, maxMemory = 0;
        double minStorage = Double.MAX_VALUE, maxStorage = 0;

        for (GuidedResourceAgent agent : agents) {
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

        for (GuidedResourceAgent agent : agents) {
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

        return normalizeToRange(rawScores, 0.0, 1.0);
    }

    /**
     * Update reputation scores based on selection and winning offer participation
     */
    private void updateReputationScores(GuidedResourceAgent gateway, List<GuidedResourceAgent> selectedAgents, List<GuidedResourceAgent> allAgents) {
        for (GuidedResourceAgent agent : allAgents) {
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

    private int getLatencyToNode(GuidedResourceAgent agent, NetworkNode targetNode) {
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
    private List<GuidedResourceAgent> selectAgentsByProbability(Map<GuidedResourceAgent, Double> compositeScores) {
        Map<GuidedResourceAgent, Double> probabilities = normalizeToProbabilities(compositeScores);
        List<GuidedResourceAgent> selected = new ArrayList<>();
        for (Map.Entry<GuidedResourceAgent, Double> entry : probabilities.entrySet()) {
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

    private double calculateMean(Map<GuidedResourceAgent, Double> scores) {
        if (scores.isEmpty()) {
            return 0.0;
        }
        return scores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private double calculateStdDev(Map<GuidedResourceAgent, Double> scores, double mean) {
        if (scores.isEmpty()) {
            return 0.0;
        }

        double variance = scores.values().stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    private double scoreToExp(double score, double mean, double stdDev) {
        if (stdDev < EPSILON) {
            return 1.0; // All values are the same
        }

        double zScore = (score - mean) / stdDev;

        return Math.exp(zScore);
    }

    /**
     * Normalize scores using Z-score standardization
     */
    private Map<GuidedResourceAgent, Double> normalizeToRange(Map<GuidedResourceAgent, Double> scores, double minVal, double maxVal) {
        if (scores.isEmpty()) {
            return new HashMap<>();
        }

        double mean = calculateMean(scores);
        double stdDev = calculateStdDev(scores, mean);

        Map<GuidedResourceAgent, Double> normalized = new HashMap<>();

        for (Map.Entry<GuidedResourceAgent, Double> entry : scores.entrySet()) {
            double expVal = scoreToExp(entry.getValue(), mean, stdDev);
            double sigmoid = 1.0 / (1.0 + Math.exp(-Math.log(expVal)));
            double scaled = minVal + sigmoid * (maxVal - minVal);
            normalized.put(entry.getKey(), scaled);
        }

        return normalized;
    }

    /**
     * Normalize scores to selection probabilities based on distance from best
     * Agents close to the best score get high probabilities, not just relative ranking
     */
    private Map<GuidedResourceAgent, Double> normalizeToProbabilities(Map<GuidedResourceAgent, Double> scores) {
        if (scores.isEmpty()) {
            return new HashMap<>();
        }

        double maxScore = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        Map<GuidedResourceAgent, Double> probabilities = new HashMap<>();

        for (Map.Entry<GuidedResourceAgent, Double> entry : scores.entrySet()) {
            double ratio = entry.getValue() / maxScore;
            double powered = Math.pow(ratio, 2.0);
            double probability = MIN_SELECTION_PROBABILITY + powered * (1.0 - MIN_SELECTION_PROBABILITY);

            probabilities.put(entry.getKey(), probability);
        }

        return probabilities;
    }
}