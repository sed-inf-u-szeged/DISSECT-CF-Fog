package hu.u_szeged.inf.fog.simulator.agent.messagestrategy;

import com.sun.security.jgss.GSSUtil;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.Offer;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import org.apache.commons.lang3.tuple.Triple;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Messaging strategy that guides resource agent selection
 * based on previous offer success and geographical distance.
 *
 * <p>
 * This strategy assigns scores to neighboring resource agents
 * to bias future selections:
 * <ul>
 *   <li>Agents that contributed to the winning offer are favored.</li>
 *   <li>Agents closer to the gateway are favored via distance weighting.</li>
 *   <li>A minimum selection probability is enforced to ensure diversity.</li>
 * </ul>
 * <p>
 * Agents are selected probabilistically based on their normalized scores
 * during each filtering round.
 * </p>
 *
 * @see hu.u_szeged.inf.fog.simulator.agent.messagestrategy.MessagingStrategy
 */
public class GuidedSearchMessagingStrategy extends MessagingStrategy {
    private static final double INSIGNIFICANT_DISTANCE_KM = 100;
    private static final double WINNING_OFFER_WEIGHT = 0.8;
    /**
     * The score awarded to this gateway agent and stored in the neighborScore map
     * of each selected agent that chose this gateway.
     */
    private static final double POINT_AWARDED_FOR_GATEWAY_CHOICE = 0.2;
    private static final double MIN_SELECTION_PROBABILITY = 0.25;
    private Offer winningOffer;

    public GuidedSearchMessagingStrategy() {
    }

    @Override
    public List<ResourceAgent> filterAgents(final ResourceAgent gateway) {
        List<ResourceAgent> potentialAgents = getPotentialAgents(gateway);

        if (potentialAgents.isEmpty()) {
            return Collections.emptyList();
        }

        if (isFirstRound(gateway)) {
            initializeScores(gateway);
            gateway.servedAsGateway = true;
            System.out.println("First gateway round for " + gateway.name);
            return potentialAgents; // return all agents due to no preferred agents yet
        } else {
            return selectAgentsBasedOnScores(gateway, potentialAgents);
        }
    }

    private List<ResourceAgent> getPotentialAgents(final ResourceAgent gateway) {
        return ResourceAgent.resourceAgents.stream()
                .filter(agent -> agent.service.getState().equals(VirtualMachine.State.RUNNING))
                .filter(agent -> agent.hostNode != gateway.hostNode)
                .filter(agent -> !agent.equals(gateway))
                .collect(Collectors.toList());
    }

    private boolean isFirstRound(final ResourceAgent gateway) {
        return !gateway.servedAsGateway;
    }

    private void initializeScores(final ResourceAgent gateway) {
        for (ResourceAgent agent : getPotentialAgents(gateway)) {
            gateway.neighborScores.put(agent, 0.0);
        }
    }

    private void updateScores(final ResourceAgent gateway) {
        if (winningOffer == null || winningOffer.id == -1) {
            return;
        }

        Map<ResourceAgent, Double> distances = new HashMap<>();
        Map<ResourceAgent, Integer> latencies = new HashMap<>();
        double minDistance = Double.MAX_VALUE;
        double maxDistance = 0.0;
        int minLatency = Integer.MAX_VALUE;
        int maxLatency = 0;

        NetworkNode gatewayNode = gateway.hostNode.iaas.repositories.get(0);

        for (ResourceAgent agent : gateway.neighborScores.keySet()) {
            double distance = gateway.hostNode.geoLocation.calculateDistance(agent.hostNode.geoLocation) / 1000; // km
            distances.put(agent, distance);
            minDistance = Math.min(minDistance, distance);
            maxDistance = Math.max(maxDistance, distance);

            int latency = getLatencyToNode(agent, gatewayNode);
            latencies.put(agent, latency);
            minLatency = Math.min(minLatency, latency);
            maxLatency = Math.max(maxLatency, latency);
        }

        double distanceRange = maxDistance - minDistance;
        int latencyRange = maxLatency - minLatency;

        System.out.println("Working gateway: " + gateway.name); // debug

        for (ResourceAgent agent : gateway.neighborScores.keySet()) {
            boolean wasHelping = winningOffer.agentResourcesMap.containsKey(agent);
            double distance = distances.get(agent);
            int latency = latencies.get(agent);
            if (wasHelping) {
                agent.winningOfferSelectionCount++;
            }
            double winningBonus = wasHelping ? WINNING_OFFER_WEIGHT / agent.winningOfferSelectionCount : 0;

            System.out.println("fa " +agent.name);
            System.out.println(getAvailableResourceScore(agent));
            // Normalize distance (prefer closer agents)
            double normalizedDistance;
            if (distanceRange < INSIGNIFICANT_DISTANCE_KM) {
                normalizedDistance = 1.0;
            } else {
                double inverted = (maxDistance - distance) / distanceRange;
                normalizedDistance = 0.4 + inverted * (1.0 - 0.4); // Scale to [0.4..1.0]
            }

            // Normalize latency (prefer lower latency)
            double normalizedLatency;
            if (latencyRange == 0) {
                normalizedLatency = 1.0;
            } else {
                double inverted = (maxLatency - latency) / (double) latencyRange;
                normalizedLatency = 0.4 + inverted * (1.0 - 0.4); // Scale to [0.4..1.0]
            }

            System.out.println(agent.name + " Winningbonus: " + winningBonus + " nDistance: " + normalizedDistance + " nLatency: " + normalizedLatency);
            double finalScore = winningBonus + normalizedDistance + normalizedLatency;

            double currentScore = gateway.neighborScores.getOrDefault(agent, 0.0);
            gateway.neighborScores.put(agent, currentScore + finalScore);
        }

        normalizeScores(gateway.neighborScores);
    }

    private double getAvailableResourceScore(ResourceAgent agent) {
        Triple<Double, Long, Long> free = agent.getAllFreeResources();

        double cpuScore = Math.log1p(free.getLeft());                // log(1 + cpu)
        double memoryScore = Math.log1p(free.getMiddle() / 1024.0);    // log(1 + GB)
        double storageScore = Math.log1p(free.getRight() / 1024.0);    // log(1 + GB)

        return (cpuScore + memoryScore + storageScore) / 3.0;
    }

    private int getLatencyToNode(ResourceAgent agent, NetworkNode targetNode) {
        try {
            String targetName = targetNode.getName();
            Map<String, Integer> latencies = agent.hostNode.iaas.repositories.get(0).getLatencies();

            return latencies.getOrDefault(targetName, Integer.MAX_VALUE);
        } catch (Exception e) {
            System.out.println("Failed to read latency for agent " + agent.name);
            return Integer.MAX_VALUE;
        }
    }


    private void normalizeScores(final Map<ResourceAgent, Double> scores) {
        final double TARGET_MAX = 1.0;
        final double TARGET_MIN = MIN_SELECTION_PROBABILITY;

        double maxScore = scores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(1.0);

        double minScore = scores.values().stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(0.0);

        double range = maxScore - minScore;

        scores.replaceAll((agent, score) -> {
            double normalized = (score - minScore) / Math.max(range, 1e-9);

            // Scale to [0.20, 1.0]
            return TARGET_MIN + normalized * (TARGET_MAX - TARGET_MIN);
        });

        System.out.println("Normalized neighbor scores (scaled 0.20..1.0):");
        scores.forEach((agent, score) -> System.out.println("  " + agent.name + ": " + String.format("%.3f", score)));
    }

    private List<ResourceAgent> selectAgentsBasedOnScores(final ResourceAgent gateway, final List<ResourceAgent> potentialAgents) {
        updateScores(gateway);
        potentialAgents.sort((a, b) -> Double.compare(gateway.neighborScores.get(b), gateway.neighborScores.get(a)));

        if (gateway.neighborScores.values().stream().mapToDouble(Double::doubleValue).sum() == 0.0) {
            return potentialAgents;
        }

        return weightedSample(gateway.neighborScores);
    }

    private List<ResourceAgent> weightedSample(Map<ResourceAgent, Double> scores) {
        List<ResourceAgent> selected = new ArrayList<>();
        Map<ResourceAgent, Double> available = new HashMap<>(scores);
        List<Map.Entry<ResourceAgent, Double>> agentList = new ArrayList<>(available.entrySet());

        for (Map.Entry<ResourceAgent, Double> entry : agentList) {
            ResourceAgent agent = entry.getKey();
            double score = entry.getValue();

            double probability = SeedSyncer.centralRnd.nextDouble();
            //System.out.println("Agent: " + agent.name + ", Score: " + score + ", Random: " + probability);

            if (score >= probability) {
                selected.add(agent);
            }
        }

        return selected;
    }

    public void setWinningOffer(final Offer winningOffer) {
        this.winningOffer = winningOffer;
    }
}
