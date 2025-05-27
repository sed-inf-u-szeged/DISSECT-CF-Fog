package hu.u_szeged.inf.fog.simulator.agent.messagestrategy;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.Offer;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
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
    /**
     * The importance of the combined score of latency and distance between 0-1.
     */
    private static final double DISTANCE_LATENCY_WEIGHT = 0.4;
    /**
     * The importance of the resource score of an agent between 0-1.
     */
    private static final double RESOURCE_WEIGHT = 0.6;
    /**
     * The score awarded to an agent when it's in the winning offer.
     */
    private static final double WINNING_OFFER_WEIGHT = 0.2;
    /**
     * The importance of the distance in the distance-latency score between 0-1.
     */
    private static final double DISTANCE_WEIGHT = 0.2;
    /**
     * The importance of the latency in the distance-latency score between 0-1.
     */
    private static final double LATENCY_WEIGHT = 0.8;
    /**
     * The score awarded to this gateway agent and stored in the neighborScore map
     * of each selected agent that chose this gateway.
     */
    private static final double POINT_AWARDED_FOR_GATEWAY_CHOICE = 0.1;
    /**
     * Minimum chance for an agent to get selected for networking.
     */
    private static final double MIN_SELECTION_PROBABILITY = 0.20;
    /**
     * Minimum score needs to be reached before the guided search actually selects the agents.
     */
    private static final double MIN_SCORE_TO_USE_GUIDED_STRATEGY = 1;
    private Offer winningOffer;

    public GuidedSearchMessagingStrategy() {
    }

    @Override
    public List<ResourceAgent> filterAgents(final ResourceAgent gateway) {
        List<ResourceAgent> potentialAgents = getPotentialAgents(gateway);
        double maxScore = getMaxNeighborScore(gateway);

        if (isFirstRound(gateway) || winningOffer == null) {
            initializeDistanceScores(gateway);
            gateway.servedAsGateway = true;
            return potentialAgents;
        } else {
            List<ResourceAgent> selectedAgents = selectAgentsBasedOnScores(gateway, potentialAgents);

            if (maxScore < MIN_SCORE_TO_USE_GUIDED_STRATEGY) {
                return potentialAgents;
            }

            // System.out.println("=== Selected agents (adding 0.1 score for gateway: " + gateway.name + ") ===");
            for (ResourceAgent agent : selectedAgents) {
                double currentScore = agent.neighborScores.getOrDefault(gateway, 0.0);
                double updatedScore = currentScore + POINT_AWARDED_FOR_GATEWAY_CHOICE;
                agent.neighborScores.put(gateway, updatedScore);

                updatedScore = agent.neighborScores.get(gateway);
                //System.out.printf("%s → neighborScore for gateway %s: %.4f → %.4f%n", agent.name, gateway.name, currentScore, updatedScore);
            }
            setWinningOffer(null);

            return selectedAgents;
        }
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
        return !gateway.servedAsGateway;
    }

    private void initializeDistanceScores(final ResourceAgent gateway) {
        Map<ResourceAgent, Double> rawScores = new HashMap<>();
        Map<ResourceAgent, Double> distances = new HashMap<>();
        Map<ResourceAgent, Integer> latencies = new HashMap<>();

        NetworkNode gatewayNode = gateway.hostNode.iaas.repositories.get(0);

        double minScore = Double.MAX_VALUE;
        double maxScore = Double.MIN_VALUE;

        for (ResourceAgent agent : gateway.neighborScores.keySet()) {
            double distanceKm = gateway.hostNode.geoLocation.calculateDistance(agent.hostNode.geoLocation) / 1000.0;
            int latencyMs = getLatencyToNode(agent, gatewayNode);

            distances.put(agent, distanceKm);
            latencies.put(agent, latencyMs);

            double distScaled = Math.log(distanceKm + 1);
            double weightedSum = LATENCY_WEIGHT * latencyMs + DISTANCE_WEIGHT * distScaled;

            double score = 1.0 / (weightedSum + 1e-6);

            rawScores.put(agent, score);
            if (score < minScore) minScore = score;
            if (score > maxScore) maxScore = score;
        }

        for (ResourceAgent agent : gateway.neighborScores.keySet()) {
            double raw = rawScores.get(agent);
            double normalized = (maxScore == minScore) ? 1.0 : (raw - minScore) / (maxScore - minScore);
            normalized *= DISTANCE_LATENCY_WEIGHT;
            double currentScore = gateway.neighborScores.get(agent);
            gateway.neighborScores.put(agent, currentScore + normalized);
        }

        // Debug output
        /*
        System.out.println("=== Init Neighbor Scores ===");
        for (ResourceAgent agent : gateway.neighborScores.keySet()) {
            System.out.printf("%s → normalized score: %.4f | distance: %.2f km | latency: %d ms%n",
                    agent.name, gateway.neighborScores.get(agent), distances.get(agent), latencies.get(agent));
        }*/
    }


    private void updateScores(final ResourceAgent gateway) {
        if (winningOffer == null || winningOffer.id == -1) return;

        System.out.println("Working gateway: " + gateway.name);
        System.out.println("not normalized scores:");

        // Collect all raw resource scores and find max
        Map<ResourceAgent, Double> rawResourceScores = new HashMap<>();
        double maxScore = Double.NEGATIVE_INFINITY;

        for (ResourceAgent agent : gateway.neighborScores.keySet()) {
            double raw = getAvailableResourceScore(agent);
            rawResourceScores.put(agent, raw);
            if (raw > maxScore) maxScore = raw;
        }

        for (ResourceAgent agent : gateway.neighborScores.keySet()) {
            boolean wasHelping = winningOffer.agentResourcesMap.containsKey(agent);
            if (wasHelping) {
                agent.winningOfferSelectionCount++;
            }

            double winningBonus = wasHelping ? WINNING_OFFER_WEIGHT / agent.winningOfferSelectionCount : 0;

            double rawScore = rawResourceScores.get(agent);
            double normalizedResourceScore = (maxScore > 0) ? rawScore / maxScore : 0.0;
            normalizedResourceScore *= RESOURCE_WEIGHT;

            double finalScore = winningBonus + normalizedResourceScore;

            //System.out.printf("%s:  wBonus: %.4f resourceP: %.4f%n", agent.name, winningBonus, normalizedResourceScore);

            double currentScore = gateway.neighborScores.getOrDefault(agent, 0.0);
            gateway.neighborScores.put(agent, currentScore + finalScore);

            currentScore = gateway.neighborScores.getOrDefault(agent, 0.0);
            System.out.println("final updated: " + currentScore);
        }
    }


    private double getAvailableResourceScore(ResourceAgent agent) {
        Triple<Double, Long, Long> free = agent.getAllFreeResources();

        double cpu = free.getLeft();
        double memoryGB = free.getMiddle() / (1024.0 * 1024 * 1024);
        double storageGB = free.getRight() / (1024.0 * 1024 * 1024);

        double cpuScore = Math.sqrt(cpu);
        double memoryScore = Math.sqrt(memoryGB);
        double storageScore = Math.sqrt(storageGB);

        //System.out.println(cpu+ "sqr: " +cpuScore+ " "+ memoryScore +" "+storageScore);
        //System.out.println(agent.name + " " + (cpuScore + memoryScore + storageScore) / 3.0);

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

    private Map<ResourceAgent, Double> getNormalizedCopy(Map<ResourceAgent, Double> original) {
        Map<ResourceAgent, Double> copy = new HashMap<>(original);

        final double TARGET_MAX = 1.0;
        final double TARGET_MIN = MIN_SELECTION_PROBABILITY;

        double maxScore = copy.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double minScore = copy.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double range = maxScore - minScore;

        Map<ResourceAgent, Double> normalized = new HashMap<>();
        for (Map.Entry<ResourceAgent, Double> entry : copy.entrySet()) {
            double norm = (entry.getValue() - minScore) / Math.max(range, 1e-9);
            double scaled = TARGET_MIN + norm * (TARGET_MAX - TARGET_MIN);
            normalized.put(entry.getKey(), scaled);
        }

        return normalized;
    }


    private List<ResourceAgent> selectAgentsBasedOnScores(final ResourceAgent gateway, final List<ResourceAgent> potentialAgents) {
        updateScores(gateway);
        potentialAgents.sort((a, b) -> Double.compare(gateway.neighborScores.get(b), gateway.neighborScores.get(a)));

        Map<ResourceAgent, Double> normalized = getNormalizedCopy(gateway.neighborScores);

        System.out.println("Normalized scores for gateway " + gateway.name + ":");
        normalized.entrySet().stream()
                .sorted(Map.Entry.<ResourceAgent, Double>comparingByValue().reversed())
                .forEach(entry -> System.out.printf("  %s -> %.4f%n", entry.getKey().name, entry.getValue()));

        if (normalized.values().stream().mapToDouble(Double::doubleValue).sum() == 0.0) {
            return potentialAgents;
        }

        return weightedSample(normalized);
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

    private double getMaxNeighborScore(ResourceAgent gateway) {
        return gateway.neighborScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
    }
}
