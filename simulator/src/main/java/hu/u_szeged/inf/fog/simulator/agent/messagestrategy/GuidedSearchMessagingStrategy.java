package hu.u_szeged.inf.fog.simulator.agent.messagestrategy;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
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

        demonstrateRanking(potentialAgents, gateway);

        /*
        for(ResourceAgent agent : ResourceAgent.resourceAgents){
            agent.capacities.forEach(c -> {
                c.utilisations.forEach(u -> System.out.println(agent.name + " utliszed cpu " + u.utilisedCpu));
            });

            Map<String, Integer> latencies = agent.hostNode.iaas.repositories.get(0).getLatencies();

            System.out.println("Latencies for "+ agent.name + ":");
            for (Map.Entry<String, Integer> entry : latencies.entrySet()) {
                System.out.println("  To " + entry.getKey() + " -> " + entry.getValue() + " ms");
            }
        }*/

        if (isFirstRound(gateway)) {
            initializeScores(gateway);
            gateway.servedAsGateway = true;
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
        System.out.println("initialized 0.0 scores for gate " + gateway.name);
    }

    private void updateScores(final ResourceAgent gateway) {
        if (winningOffer == null || winningOffer.id == -1) {
            return;
        }


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

        System.out.println("Working gateway: " + gateway.name); // debug

        for (ResourceAgent agent : gateway.neighborScores.keySet()) {
            boolean wasHelping = winningOffer.agentResourcesMap.containsKey(agent);
            double distance = distances.get(agent);
            if (wasHelping) {
                agent.winningOfferSelectionCount++;
            }
            double winningBonus = wasHelping ? WINNING_OFFER_WEIGHT / agent.winningOfferSelectionCount : 0;


            double normalizedDistance;
            if (distanceRange < INSIGNIFICANT_DISTANCE_KM) {
                normalizedDistance = 1.0;
            } else {
                double inverted = (maxDistance - distance) / distanceRange;
                normalizedDistance = 0.4 + inverted * (1.0 - 0.4); // Scale to [0.4..1.0]
            }

            double finalScore = winningBonus + normalizedDistance;

            double currentScore = gateway.neighborScores.getOrDefault(agent, 0.0);
            gateway.neighborScores.put(agent, currentScore + finalScore);

            //System.out.println(agent.name + " -> wasHelping=" + wasHelping + ", distance=" + distance + ", normalizedDistance=" + normalizedDistance + ", finalScore=" + finalScore + ", hasWonCount=" + agent.winningOfferSelectionCount); // debug
        }


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

    public void demonstrateRanking(List<ResourceAgent> agents, ResourceAgent gateway) {
        rank ranker = new rank();

        NetworkNode gateeayNode = gateway.hostNode.iaas.repositories.get(0);

        System.out.println("\n=== Ranking by Bandwidth ===");
        List<ResourceAgent> byBandwidth = ranker.rankByBandwidth(agents);
        int i = 1;
        for (ResourceAgent agent : byBandwidth) {
            long bandwidth = ranker.getTotalBandwidth(agent);
            System.out.println(i++ + ". " + agent.name + " → Bandwidth: " + bandwidth);
        }

        System.out.println("\n=== Ranking by Latency ===");
        List<ResourceAgent> byLatency = ranker.rankByLatency(agents, gateeayNode);
        i = 1;
        for (ResourceAgent agent : byLatency) {
            int latency = ranker.getLatencyToNode(agent, gateeayNode);
            System.out.println(i++ + ". " + agent.name + " → Latency: " + latency + "ms");
        }

        System.out.println("\n=== Ranking by Power Consumption ===");
        List<ResourceAgent> byPower = ranker.rankByPowerConsumption(agents);
        i = 1;
        for (ResourceAgent agent : byPower) {
            double power = ranker.calculatePowerConsumption(agent);
            System.out.printf("%d. %s → Power: %.2fkW%n", i++, agent.name, power);
        }

        System.out.println("\n=== Custom Ranking (Latency → Bandwidth) ===");
        List<ResourceAgent> customRanked = ranker.rankCustom(agents, gateeayNode);
        i = 1;
        for (ResourceAgent agent : customRanked) {
            int latency = ranker.getLatencyToNode(agent, gateeayNode);
            long bandwidth = ranker.getTotalBandwidth(agent);
            System.out.println(i++ + ". " + agent.name + " → Latency: " + latency + "ms, Bandwidth: " + bandwidth);
        }
    }

}
