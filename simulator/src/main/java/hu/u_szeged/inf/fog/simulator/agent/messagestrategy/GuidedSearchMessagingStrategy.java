package hu.u_szeged.inf.fog.simulator.agent.messagestrategy;

import hu.u_szeged.inf.fog.simulator.agent.Offer;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.*;
import java.util.stream.Collectors;

public class GuidedSearchMessagingStrategy extends MessagingStrategy {

    private Offer winningOffer;
    private static final double INITIAL_EPSILON = 0.3;
    private double epsilon = INITIAL_EPSILON;
    private final Random random = new Random();

    public GuidedSearchMessagingStrategy(Offer offer) {
        this.winningOffer = offer;
    }

    public GuidedSearchMessagingStrategy() {
    }

    @Override
    public List<ResourceAgent> filterAgents(ResourceAgent gateway) {
        List<ResourceAgent> potentialAgents = getPotentialAgents(gateway);
        if (potentialAgents.isEmpty()) {
            return Collections.emptyList();
        }

        List<ResourceAgent> selectedAgents;
        if (isFirstRound(gateway)) {
            System.out.println("First round for " + gateway.name + ": initializing scores (exploration).");
            initializeScores(gateway);
            selectedAgents = potentialAgents;
        } else {
            System.out.println("Subsequent round for " + gateway.name + ": selecting based on scores (exploitation).");
            selectedAgents = weightedSample(gateway.neighborScores, potentialAgents.size());
        }

        updateScores(gateway);
        decayEpsilon();
        return selectedAgents;
    }

    private List<ResourceAgent> getPotentialAgents(ResourceAgent gateway) {
        return ResourceAgent.resourceAgents.stream()
                .filter(agent -> !agent.equals(gateway)) // Exclude itself
                .collect(Collectors.toList());
    }

    private boolean isFirstRound(ResourceAgent gateway) {
        return gateway.neighborScores.isEmpty();
    }

    private void initializeScores(ResourceAgent gateway) {
        for (ResourceAgent agent : getPotentialAgents(gateway)) {
            double distance = gateway.hostNode.geoLocation.calculateDistance(agent.hostNode.geoLocation);
            double score = 1.0 / (distance + 1e-6); // Inverse distance for score, small epsilon to avoid div by zero
            gateway.neighborScores.put(agent, score);
        }

        normalizeScores(gateway.neighborScores);

        System.out.println("Initialized neighbor scores for " + gateway.name + ":");
        printAgentScores(gateway);
    }


    private void updateScores(ResourceAgent gateway) {
        if (winningOffer == null || gateway.neighborScores == null) {
            return;
        }


        for (ResourceAgent agent : winningOffer.agentResourcesMap.keySet()) {
            if (gateway.neighborScores.containsKey(agent)) {
                double boostedScore = gateway.neighborScores.get(agent) + 1.0;
                gateway.neighborScores.put(agent, boostedScore);
                System.out.println("Boosted score for " + agent.name + ": " + String.format("%.2f", boostedScore));
            }
        }

        normalizeScores(gateway.neighborScores);
    }

    private void normalizeScores(Map<ResourceAgent, Double> scores) {
        double maxScore = scores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(1.0);

        if (maxScore == 0.0) {
            System.out.println("All scores are 0. Skipping normalization.");
            return;
        }

        scores.replaceAll((agent, score) -> score / maxScore);

        System.out.println("Normalized neighbor scores:");
        scores.forEach((agent, score) ->
                System.out.println("  " + agent.name + ": " + String.format("%.3f", score))
        );
    }

    private List<ResourceAgent> weightedSample(Map<ResourceAgent, Double> scores, int limit) {
        List<ResourceAgent> selected = new ArrayList<>();
        Map<ResourceAgent, Double> available = new HashMap<>(scores);

        while (selected.size() < Math.min(limit, available.size())) {
            double totalWeight = available.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();

            if (totalWeight == 0.0) {
                System.out.println("No valid weights. Selecting fallback agent.");
                selected.add(available.keySet().iterator().next());
                break;
            }

            double r = random.nextDouble() * totalWeight;
            double cumulative = 0.0;

            for (Map.Entry<ResourceAgent, Double> entry : available.entrySet()) {
                cumulative += entry.getValue();
                if (r <= cumulative) {
                    ResourceAgent chosen = entry.getKey();
                    selected.add(chosen);
                    available.remove(chosen);
                    break;
                }
            }
        }

        if (selected.isEmpty() && !available.isEmpty()) {
            System.out.println("No agent selected by weight, picking first available.");
            selected.add(available.keySet().iterator().next());
        }

        System.out.println("Selected agents with probability: " );
        for (ResourceAgent s : selected
        ) {
            System.out.println(s.name);
        }

        return selected;
    }

    private void decayEpsilon() {
        epsilon *= 0.99;
        System.out.println("Epsilon decayed to: " + String.format("%.4f", epsilon));
    }

    private void printAgentScores(ResourceAgent gateway) {
        gateway.neighborScores.forEach((agent, score) ->
                System.out.println("  " + agent.name + ": " + String.format("%.3f", score))
        );
    }
}
