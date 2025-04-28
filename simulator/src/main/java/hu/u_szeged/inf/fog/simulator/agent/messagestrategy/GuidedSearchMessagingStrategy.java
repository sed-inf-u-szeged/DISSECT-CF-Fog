package hu.u_szeged.inf.fog.simulator.agent.messagestrategy;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.Offer;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
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
 *
 * Agents are selected probabilistically based on their normalized scores
 * during each filtering round.
 * </p>
 *
 * @see hu.u_szeged.inf.fog.simulator.agent.messagestrategy.MessagingStrategy
 */
public class GuidedSearchMessagingStrategy extends MessagingStrategy {

    private static final double WINNING_OFFER_WEIGHT = 1.5;
    private static final double DISTANCE_WEIGHT = 0.3;
    private static final double MIN_SELECTION_PROBABILITY = 0.05;
    static int count = 0;

    public static List<ResourceAgent> guidedAgents;
    private Offer winningOffer;

    public GuidedSearchMessagingStrategy(final Offer offer) {
        this.winningOffer = offer;
    }

    public GuidedSearchMessagingStrategy() {
    }

    @Override
    public List<ResourceAgent> filterAgents(final ResourceAgent gateway) {
        List<ResourceAgent> potentialAgents = getPotentialAgents(gateway);

        count++;
        System.out.println("filter count: " + count); // debug

        if (potentialAgents.isEmpty()) {
            return Collections.emptyList();
        }

        if (isFirstRound(gateway)) {
            initializeScores(gateway);
            guidedAgents = potentialAgents;
            return potentialAgents; // return all agent due to no preferred agents yet
        } else {
            guidedAgents = selectAgentsBasedOnScores(gateway, potentialAgents);
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
        return gateway.neighborScores.isEmpty();
    }

    private void initializeScores(final ResourceAgent gateway) {
        for (ResourceAgent agent : getPotentialAgents(gateway)) {
            gateway.neighborScores.put(agent, 0.0);
        }
        System.out.println("initialized 0.0 scores for gate " + gateway.name);
    }

    private void updateScores(final ResourceAgent gateway) {
        if (winningOffer == null || gateway.neighborScores.isEmpty() || winningOffer.id == -1) {
            return;
        }

        double minDistance = Double.MAX_VALUE;
        double maxDistance = 0;
        for (ResourceAgent agent : gateway.neighborScores.keySet()) {
            double distance = gateway.hostNode.geoLocation.calculateDistance(agent.hostNode.geoLocation) / 1000; // Convert to km
            minDistance = Math.min(minDistance, distance);
            maxDistance = Math.max(maxDistance, distance);
        }

        System.out.println("gateway: " + gateway.name + "with offer " + winningOffer.id);
        for (ResourceAgent agent : gateway.neighborScores.keySet()) {
            boolean wasHelping = winningOffer.agentResourcesMap.containsKey(agent);
            double winningBonus = wasHelping ? WINNING_OFFER_WEIGHT : 1.0;

            double distance = gateway.hostNode.geoLocation.calculateDistance(agent.hostNode.geoLocation) / 1000; // km
            double normalizedDistance = (distance - minDistance) / (maxDistance - minDistance + 1e-6); // Normalize between 0 and 1
            double distanceScore = 1.0 - normalizedDistance;

            double newScore = (distanceScore * WINNING_OFFER_WEIGHT) + (winningBonus * (1.0 - DISTANCE_WEIGHT));
            gateway.neighborScores.put(agent, newScore);

            System.out.println(agent.name + " -> wasHelping=" + wasHelping + ", normalizedDistance=" + normalizedDistance + ", finalScore=" + newScore); // debug
        }

        normalizeScores(gateway.neighborScores);
    }

    private void normalizeScores(final Map<ResourceAgent, Double> scores) {
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
            double normalizedScore = (score - minScore) / Math.max(range, 1e-9); // safe range
            return Math.max(normalizedScore, MIN_SELECTION_PROBABILITY); // Enforce minimum threshold
        });

        System.out.println("Normalized neighbor scores:");
        scores.forEach((agent, score) -> {
            System.out.println("  " + agent.name + ": " + String.format("%.3f", score));
        });
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
}
