package hu.u_szeged.inf.fog.simulator.aco;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.StandardResourceAgent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.stream.Collectors;

public class DecentralisedAntOptimiserApplication {

    private double[][] globalPheromoneMatrix;

    public double[][] runOptimiser(
            ArrayList<StandardResourceAgent> allComputingAppliances, int numberOfAnts,
            int numberOfIteration, double probability, double evaporationRate, AgentApplication app) {

        globalPheromoneMatrix = new double[allComputingAppliances.size()][allComputingAppliances.size()];
        for (int i = 0; i < allComputingAppliances.size(); i++) {
            for (int j = 0; j < allComputingAppliances.size(); j++) {
                double noise = SeedSyncer.centralRnd.nextDouble() * 0.1;
                globalPheromoneMatrix[i][j] = 0.5 - (0.1 / 2) + noise;
            }
        }
        
        //CentralisedAntOptimiser.printMatrix("Init pheromone: ", globalPheromoneMatrix);
 
        for (int caNum = 0; caNum < allComputingAppliances.size(); caNum++) {
            
            for (int i = 0; i < numberOfIteration; i++) {
                // System.out.println(allComputingAppliances.get(caNum).name + "-iteration: " + i);
                
                DecentralisedAntApplication[] ants = new DecentralisedAntApplication[numberOfAnts];
                for (int j = 0; j < numberOfAnts; j++) {
                    ants[j] = new DecentralisedAntApplication(j);
                }
                
                for (DecentralisedAntApplication ant : ants) {
                    ant.generateSolution(globalPheromoneMatrix[caNum], caNum, probability, evaporationRate, app);
                    
                }
                evaporatePheromones(globalPheromoneMatrix[caNum], evaporationRate);
             
            }
            // CentralisedAntOptimiser.printMatrix("", globalPheromoneMatrix);
        }
        
        return globalPheromoneMatrix;
    }
    
    private void evaporatePheromones(double[] globalPheromoneMatrix, double evaporationRate) {
        for (int i = 0; i < globalPheromoneMatrix.length; i++) {
            if (globalPheromoneMatrix[i] > 0) {
                double epsilon = (Math.random() * 0.02) - 0.01;
                globalPheromoneMatrix[i] = globalPheromoneMatrix[i] * (1 - evaporationRate) + epsilon;
            }
        }
    }
    
    public int[] assignClusters(double[][] globalPheromoneMatrix) {
        int[] clusterAssignments = new int[globalPheromoneMatrix.length];

        for (int i = 0; i < globalPheromoneMatrix.length; i++) {
            double maxPheromone = Double.NEGATIVE_INFINITY;
            int bestCluster = -1;

            for (int j = 0; j < globalPheromoneMatrix[i].length; j++) {
                if (j == i) {
                    continue; 
                }

                if (globalPheromoneMatrix[i][j] > maxPheromone) {
                    maxPheromone = globalPheromoneMatrix[i][j];
                    bestCluster = j;
                }
            }

            clusterAssignments[i] = bestCluster;
        }

        return clusterAssignments;
    }

    public void addToCluster(HashMap<StandardResourceAgent, ArrayList<StandardResourceAgent>> clusters,
                             StandardResourceAgent solo, StandardResourceAgent referred) {
        if (clusters.containsKey(referred)) {
            clusters.get(referred).add(solo);
        } else {
            for (ArrayList<StandardResourceAgent> list : clusters.values()) {
                if (list.contains(referred)) {
                    list.add(solo);
                    break;
                }
            }
        }
    }
}