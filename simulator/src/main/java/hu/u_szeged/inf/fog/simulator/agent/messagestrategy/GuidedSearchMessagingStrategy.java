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
 * <p>
 * Agents are selected probabilistically based on their normalized scores
 * during each filtering round.
 * </p>
 *
 * @see hu.u_szeged.inf.fog.simulator.agent.messagestrategy.MessagingStrategy
 */
public class GuidedSearchMessagingStrategy extends MessagingStrategy {

    private static final double WINNING_OFFER_WEIGHT = 0.8;
    private static final double MIN_SELECTION_PROBABILITY = 0.25;
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
            return potentialAgents; // return all agents due to no preferred agents yet
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

        // 1. Collect distances
        Map<ResourceAgent, Double> distances = new HashMap<>();
        double minDistance = Double.MAX_VALUE;
        double maxDistance = 0.0;

        for (ResourceAgent agent : gateway.neighborScores.keySet()) {
            double distance = gateway.hostNode.geoLocation.calculateDistance(agent.hostNode.geoLocation) / 1000; // km
            distances.put(agent, distance);
            minDistance = Math.min(minDistance, distance);
            maxDistance = Math.max(maxDistance, distance);
        }

        double distanceRange = maxDistance - minDistance;

        System.out.println("gateway: " + gateway.name + " with offer " + winningOffer.id); // debug

        for (ResourceAgent agent : gateway.neighborScores.keySet()) {
            boolean wasHelping = winningOffer.agentResourcesMap.containsKey(agent);
            double winningBonus = wasHelping ? WINNING_OFFER_WEIGHT : 0;
            double distance = distances.get(agent);

            // 2. Normalize distance: 1.0 (best, closest) to 0.4 (worst, farthest)
            double normalizedDistance;
            if (distanceRange < 1e-9) {
                normalizedDistance = 1.0; // All distances are almost the same
            } else {
                double inverted = (maxDistance - distance) / distanceRange; // closer = bigger
                normalizedDistance = 0.4 + inverted * (1.0 - 0.4); // Scale to [0.4..1.0]
            }

            // 3. Apply winning bonus
            double finalScore = winningBonus + normalizedDistance;

            gateway.neighborScores.put(agent, finalScore);

            System.out.println(agent.name + " -> wasHelping=" + wasHelping + ", distance=" + distance + ", Normdistance=" + normalizedDistance +", normalizedDistance=" + normalizedDistance + ", finalScore=" + finalScore); // debug
        }

        // 4. Normalize scores to [0.20..1.00] (already correct)
        normalizeScores(gateway.neighborScores);
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
