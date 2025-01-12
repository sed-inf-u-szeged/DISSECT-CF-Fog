package hu.u_szeged.inf.fog.simulator.workflow.aco;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DistributedAntOptimiser {
    
    private static double[][] globalPheromoneMatrix;
    
    public static HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> runOptimiser(
            ArrayList<ComputingAppliance> allComputingAppliances, int numberOfAnts, int numberOfIteration,double propability, double evaporationRate) {
        
        globalPheromoneMatrix = new double[allComputingAppliances.size()][allComputingAppliances.size()];
        for (int i = 0; i < allComputingAppliances.size(); i++) {
            for (int j = 0; j < allComputingAppliances.size(); j++) {
                double noise = SeedSyncer.centralRnd.nextDouble() * 0.1;
                globalPheromoneMatrix[i][j] = 0.5 - (0.1 / 2) + noise;
            }
        }
        
        CentralisedAntOptimiser.printMatrix("Init pheromone: ", globalPheromoneMatrix);
 

        for (int caNum = 0; caNum < allComputingAppliances.size(); caNum++) {
            
            for (int i = 0; i < numberOfIteration; i++) {
                System.out.println(allComputingAppliances.get(caNum).name + "-iteration: " + i);
                
                
                DistributedAnt[] ants = new DistributedAnt[numberOfAnts];
                for (int j = 0; j < numberOfAnts; j++) {
                    ants[j] = new DistributedAnt(j);
                }
                
                for (DistributedAnt ant : ants) {
                    ant.generateSolution(globalPheromoneMatrix[caNum], caNum, propability, evaporationRate);
                    
                }
                evaporatePheromones(globalPheromoneMatrix[caNum], evaporationRate);
             
            }
            CentralisedAntOptimiser.printMatrix("", globalPheromoneMatrix);
        }
        
        return createClusters(globalPheromoneMatrix);
    }
    
    private static void evaporatePheromones(double[] globalPheromoneMatrix, double evaporationRate) {
        
        for (int i = 0; i < globalPheromoneMatrix.length; i++) {
            if (globalPheromoneMatrix[i] > 0) {
                globalPheromoneMatrix[i] *= (1 - evaporationRate);
            }
        }
    }
    
    private static int[] assignClusters(double[][] globalPheromoneMatrix) {
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
    
    public static HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> createClusters(double[][] globalPheromoneMatrix) {
        int[] resultVector = assignClusters(globalPheromoneMatrix);

        System.out.println(Arrays.toString(resultVector));

        HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> clusters = new HashMap<>();
        boolean[] visited = new boolean[resultVector.length]; 

        for (int i = 0; i < resultVector.length; i++) {
            if (!visited[i]) { // ha még nincs cluster-ben
                
                ArrayList<WorkflowComputingAppliance> cluster = new ArrayList<>();
                int currentNode = i;

                while (!visited[currentNode]) { // amig nem találunk egy klaszerben lévőt
                    visited[currentNode] = true; // az aktuálisat tegyük látogatottá

                    if (currentNode != i) {
                        cluster.add((WorkflowComputingAppliance) ComputingAppliance.allComputingAppliances.get(currentNode));
                    }
                    currentNode = resultVector[currentNode];
                }
                if (cluster.isEmpty()) {
                    addToCluster(clusters, 
                            (WorkflowComputingAppliance) ComputingAppliance.allComputingAppliances.get(i),
                               (WorkflowComputingAppliance) ComputingAppliance.allComputingAppliances.get(resultVector[i]));
                    System.out.println("AAAAAAAAAAAAAAAAAAAAAA " + ComputingAppliance.allComputingAppliances.get(i).name  + " "
                            + resultVector[i] + " " + ComputingAppliance.allComputingAppliances.get(resultVector[i]).name);
                } else { 
                    WorkflowComputingAppliance firstNode = (WorkflowComputingAppliance) ComputingAppliance.allComputingAppliances.get(i);
                    clusters.put(firstNode, cluster);
                }
                
                
            }
        }


        return clusters;
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