package hu.u_szeged.inf.fog.simulator.workflow.aco;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import java.util.ArrayList;
import java.util.HashMap;

public class DecentralisedAntOptimiser {
    
    private static double[][] globalPheromoneMatrix;
    
    public static double[][] runOptimiser(
            ArrayList<ComputingAppliance> allComputingAppliances, int numberOfAnts, 
            int numberOfIteration, double propability, double evaporationRate) {
        
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
                
                DecentralisedAnt[] ants = new DecentralisedAnt[numberOfAnts];
                for (int j = 0; j < numberOfAnts; j++) {
                    ants[j] = new DecentralisedAnt(j);
                }
                
                for (DecentralisedAnt ant : ants) {
                    ant.generateSolution(globalPheromoneMatrix[caNum], caNum, propability, evaporationRate);
                    
                }
                evaporatePheromones(globalPheromoneMatrix[caNum], evaporationRate);
             
            }
            // CentralisedAntOptimiser.printMatrix("", globalPheromoneMatrix);
        }
        
        return globalPheromoneMatrix;
    }
    
    private static void evaporatePheromones(double[] globalPheromoneMatrix, double evaporationRate) {
        
        for (int i = 0; i < globalPheromoneMatrix.length; i++) {
            if (globalPheromoneMatrix[i] > 0) {
                globalPheromoneMatrix[i] *= (1 - evaporationRate);
            }
        }
    }
    
    public static int[] assignClusters(double[][] globalPheromoneMatrix) {
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
    
    public static void addToCluster(HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> clusters, 
            WorkflowComputingAppliance solo, WorkflowComputingAppliance referred) {
        if (clusters.containsKey(referred)) {
            clusters.get(referred).add(solo);
        } else {
            for (ArrayList<WorkflowComputingAppliance> list : clusters.values()) {
                if (list.contains(referred)) {
                    list.add(solo);
                    break;
                }
            }
        }
    }
}