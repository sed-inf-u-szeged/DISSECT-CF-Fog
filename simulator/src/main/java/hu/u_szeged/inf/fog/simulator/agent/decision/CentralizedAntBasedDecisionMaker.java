package hu.u_szeged.inf.fog.simulator.agent.decision;

import hu.u_szeged.inf.fog.simulator.aco.CentralisedAntOptimiserApplication;
import hu.u_szeged.inf.fog.simulator.aco.ClusterSorter;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.StandardResourceAgent;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CentralizedAntBasedDecisionMaker extends DecisionMaker {
    private List<StandardResourceAgent> chosenCluster;

    private final ArrayList<StandardResourceAgent> nodesToBeClustered;
    private final int clusterCount, numberOfAnts, numberOfIterations;
    private final double probability, topPercentAnts, pheromoneIncrement, evaporationRate;


    public CentralizedAntBasedDecisionMaker(int clusterCount, ArrayList<StandardResourceAgent> nodesToBeClustered,
                                            int numberOfAnts, int numberOfIterations, double probability, double topPercentAnts,
                                            double pheromoneIncrement, double evaporationRate) {
        this.clusterCount = clusterCount;
        this.nodesToBeClustered = nodesToBeClustered;
        this.numberOfAnts = numberOfAnts;
        this.numberOfIterations = numberOfIterations;
        this.probability = probability;
        this.topPercentAnts = topPercentAnts;
        this.pheromoneIncrement = pheromoneIncrement;
        this.evaporationRate = evaporationRate;
    }

    @Override
    public void start(AgentApplication app) {
        StandardResourceAgent.minimumsMaximums();

        app.normalizePriorities();

        HashMap<Integer, ArrayList<StandardResourceAgent>> clusterAssignments;

        clusterAssignments = new CentralisedAntOptimiserApplication().runOptimiser(clusterCount, nodesToBeClustered, numberOfAnts, numberOfIterations, probability, topPercentAnts, pheromoneIncrement, evaporationRate, app);

        List<ArrayList<StandardResourceAgent>> clusterList = new ClusterSorter().sortClustersByScore(clusterAssignments, app);

        //Print after sorting it
        for (int i = 0; i < clusterList.size(); i++) {
            System.out.println("Cluster " + (i + 1) + ":");

            for (StandardResourceAgent agent : clusterList.get(i)) {
                System.out.println("\t" + agent.name);
            }
        }

        for (ArrayList<StandardResourceAgent> cluster : clusterList) {
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
    protected void generateOffers(AgentApplication app) {
        List<Pair<ResourceAgent, Resource>> agentResourcePairs = new ArrayList<>();

        for (StandardResourceAgent entry : chosenCluster) {
            agentResourcePairs.addAll(entry.agentStrategy.canFulfill(entry, app.resources));
        }

        generateUniqueOfferCombinations(agentResourcePairs, app);
    }
}
