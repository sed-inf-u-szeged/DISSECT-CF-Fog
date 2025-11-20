package hu.u_szeged.inf.fog.simulator.agent.decision;

import hu.u_szeged.inf.fog.simulator.aco.CentralisedAntOptimiserApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CentralizedAntBasedDecisionMaker extends DecisionMaker {
    private List<Map.Entry<ComputingAppliance, ResourceAgent>> chosenCluster;

    private final ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> nodesToBeClustered;
    private final int clusterCount, numberOfAnts, numberOfIterations;
    private final double probability, topPercentAnts, pheromoneIncrement, evaporationRate;


    public CentralizedAntBasedDecisionMaker(int clusterCount, ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> nodesToBeClustered,
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
    public void deploy(AgentApplication app) {
        HashMap<Integer, ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>>> clusterAssignments;

        clusterAssignments = CentralisedAntOptimiserApplication.runOptimiser(clusterCount, nodesToBeClustered, numberOfAnts, numberOfIterations, probability, topPercentAnts, pheromoneIncrement, evaporationRate, app);

        List<ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>>> clusterList = CentralisedAntOptimiserApplication.sortClustersByScore(clusterAssignments);

        for (int i = 0; i < clusterList.size(); i++) {
            System.out.println("Cluster " + (i + 1) + ":");

            for (int j = 0; j < clusterList.get(i).size(); j++) {
                System.out.println("\t" + clusterList.get(i).get(j).getKey().name + ", RA: " + clusterList.get(i).get(j).getValue().name);
            }
        }

        for (ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> cluster : clusterList) {
            chosenCluster = cluster;
            this.generateOffers(app);

            if (!app.offers.isEmpty()) {
                return;
            }
        }
    }

    @Override
    void generateOffers(AgentApplication app) {
        List<Pair<ResourceAgent, AgentApplication.Resource>> agentResourcePairs = new ArrayList<>();

        for (Map.Entry<ComputingAppliance, ResourceAgent> entry : chosenCluster) {
            agentResourcePairs.addAll(entry.getValue().agentStrategy.canFulfill(entry.getValue(), app.resources));
        }

        generateUniqueOfferCombinations(agentResourcePairs, app);
    }
}
