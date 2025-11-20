package hu.u_szeged.inf.fog.simulator.agent.decision;

import hu.u_szeged.inf.fog.simulator.aco.ClusterMessengerApplication;
import hu.u_szeged.inf.fog.simulator.aco.ClusterSorter;
import hu.u_szeged.inf.fog.simulator.aco.DecentralisedAntOptimiserApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.StandardResourceAgent;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DecentralizedAntBasedDecisionMaker extends DecisionMaker {
    public HashMap<Integer, ArrayList<StandardResourceAgent>> clusterAssignments = new HashMap<>();

    private ArrayList<StandardResourceAgent> chosenCluster = new ArrayList<>();

    private final ArrayList<StandardResourceAgent> allAgents;
    private final int numberOfAnts, numberOfIterations, freq;
    private final double probability, evaporationRate;

    public DecentralizedAntBasedDecisionMaker(ArrayList<StandardResourceAgent> allAgents, int numberOfAnts, int numberOfIterations, double probability, double evaporationRate, int freq) {
        this.allAgents = allAgents;
        this.numberOfAnts = numberOfAnts;
        this.numberOfIterations = numberOfIterations;
        this.probability = probability;
        this.evaporationRate = evaporationRate;
        this.freq = freq;
    }

    @Override
    public void start(AgentApplication app) {
        StandardResourceAgent.minimumsMaximums();

        app.normalizePriorities();

        clusterAssignments.clear();
        double[][] globalPheromoneMatrix = new DecentralisedAntOptimiserApplication().runOptimiser(allAgents, numberOfAnts, numberOfIterations, probability, evaporationRate, app);
        new ClusterMessengerApplication(globalPheromoneMatrix, allAgents, freq, this, app);
    }

    public void processClusters(AgentApplication app) {
        System.out.println("Before sorting by score: ");
        for (int i = 0; i < clusterAssignments.size(); i++) {
            System.out.println("Cluster " + i + ":");

            for (StandardResourceAgent agent : clusterAssignments.get(i)) {
                System.out.println("\t" + agent.name);
            }
        }

        List<ArrayList<StandardResourceAgent>> sortedClustersByScore = new ClusterSorter().sortClustersByScore(clusterAssignments, app);

        System.out.println("\nAfter sorting by score: ");
        for (int i = 0; i < sortedClustersByScore.size(); i++) {
            System.out.println("Cluster " + i + ":");

            for (StandardResourceAgent agent : sortedClustersByScore.get(i)) {
                System.out.println("\t" + agent.name);
            }
        }

        for (ArrayList<StandardResourceAgent> cluster : sortedClustersByScore) {
            chosenCluster = cluster;
            this.generateOffers(app);

            System.out.println(app.offers);

            if (!app.offers.isEmpty()) {
                standardSender.processAppOffer(app);
                return;
            }
        }

        SimLogger.logError("No cluster can satisfy " + app.name);
    }

    @Override
    public void generateOffers(AgentApplication app) {
        List<Pair<ResourceAgent, Resource>> agentResourcePairs = new ArrayList<>();

        for (StandardResourceAgent entry : chosenCluster) {
            agentResourcePairs.addAll(entry.agentStrategy.canFulfill(entry, app.resources));
        }

        generateUniqueOfferCombinations(agentResourcePairs, app);
    }
}
