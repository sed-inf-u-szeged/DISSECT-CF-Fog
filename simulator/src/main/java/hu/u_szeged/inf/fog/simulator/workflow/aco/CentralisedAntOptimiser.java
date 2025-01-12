package hu.u_szeged.inf.fog.simulator.workflow.aco;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CentralisedAntOptimiser {

    /**
     * The global pheromone matrix is to be updated depending on the quality of solutions
     * produced by the agents.
     */
    private static double[][] globalPheromoneMatrix;

    public static HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> runOptimiser(
            ArrayList<WorkflowComputingAppliance> centerNodes, 
            ArrayList<WorkflowComputingAppliance> nodesToBeClustered, 
            int numberOfAnts, int numberOfIteration, double probability, double topPercentAnts,
            double pheromoneIncrement, double evaporationRate) {
        
        globalPheromoneMatrix = new double[nodesToBeClustered.size()][centerNodes.size()];
        for (int i = 0; i < nodesToBeClustered.size(); i++) {
            for (int j = 0; j < centerNodes.size(); j++) {
                double noise = SeedSyncer.centralRnd.nextDouble() * 0.1;
                globalPheromoneMatrix[i][j] = 0.5 - (0.1 / 2) + noise;
            }
        }
        printMatrix("Init pheromone: ", globalPheromoneMatrix);

        for (int i = 0; i < numberOfIteration; i++) {
            System.out.println("Iteration: " + i);
            
            CentralisedAnt[] ants = new CentralisedAnt[numberOfAnts];
            for (int j = 0; j < numberOfAnts; j++) {
                ants[j] = new CentralisedAnt(j);
            }
            
            for (CentralisedAnt ant : ants) {
                ant.generateSolution(globalPheromoneMatrix, nodesToBeClustered.size(), probability);
            }
            
            calculateFitness(centerNodes, nodesToBeClustered, ants);
            for (CentralisedAnt ant : ants) {
                System.out.println(ant.id + ": " + Arrays.toString(ant.solution) + " " + ant.fitness);
            }
            updatePheromones(nodesToBeClustered, ants, topPercentAnts, pheromoneIncrement);
            evaporatePheromones(globalPheromoneMatrix, evaporationRate);
            printMatrix("updated pheromone matrix: ", globalPheromoneMatrix);
        }
        return generateClusters(globalPheromoneMatrix, nodesToBeClustered, centerNodes);
    }
    
    private static HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> generateClusters(
            double[][] globalPheromoneMatrix,
            ArrayList<WorkflowComputingAppliance> nodesToBeClustered,
            ArrayList<WorkflowComputingAppliance> centerNodes) {

        HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> clusterAssignment = new HashMap<>();

        for (int i = 0; i < globalPheromoneMatrix.length; i++) {
            int clusterIndex = 0;
            double maxValue = globalPheromoneMatrix[i][0];
            
            for (int j = 1; j < globalPheromoneMatrix[i].length; j++) {
                if (globalPheromoneMatrix[i][j] > maxValue) {
                    maxValue = globalPheromoneMatrix[i][j];
                    clusterIndex = j;
                }
            }

            WorkflowComputingAppliance selectedCluster = centerNodes.get(clusterIndex);
            WorkflowComputingAppliance node = nodesToBeClustered.get(i);
            
            if (!clusterAssignment.containsKey(selectedCluster)) {
                clusterAssignment.put(selectedCluster, new ArrayList<>());
            }
            clusterAssignment.get(selectedCluster).add(node);
        }

        return clusterAssignment;
    }

    private static void evaporatePheromones(double[][] globalPheromoneMatrix, double evaporationRate) {
       
        for (int i = 0; i < globalPheromoneMatrix.length; i++) {
            for (int j = 0; j < globalPheromoneMatrix[i].length; j++) {
                globalPheromoneMatrix[i][j] *= (1 - evaporationRate);
            }
        }
    }

    private static void updatePheromones(ArrayList<WorkflowComputingAppliance> nodesToBeClustered, 
            CentralisedAnt[] ants, double topPercentAnts, double pheromoneIncrement) {
        
        Arrays.sort(ants);
        int number = (int) Math.ceil(ants.length * topPercentAnts);
        double maxFitness = 1.0 / ants[0].fitness; 

        System.out.print("Best: ");
        for (int i = 0; i < number; i++) {
            System.out.print(ants[i].id + " ");
            double relativeFitness = (1.0 / ants[i].fitness) / maxFitness; 
            for (int j = 0; j < nodesToBeClustered.size(); j++) {
                globalPheromoneMatrix[j][ants[i].solution[j]] += pheromoneIncrement * relativeFitness;
            }
        }
        System.out.println();
    }

    private static void calculateFitness(ArrayList<WorkflowComputingAppliance> centerNodes, 
            ArrayList<WorkflowComputingAppliance> nodesToBeClustered, CentralisedAnt[] ants) {
        for (CentralisedAnt ant : ants) {
            double fitnessLevel = 0.0;
            for (int i = 0; i < nodesToBeClustered.size(); i++) {
                fitnessLevel += calculateHeuristic(nodesToBeClustered.get(i), centerNodes.get(ant.solution[i]));
            }
            ant.fitness = fitnessLevel;
            //System.out.println(ant.fitness);
        }
    }
    
    private static double calculateHeuristic(WorkflowComputingAppliance node, WorkflowComputingAppliance center) {
        return center.geoLocation.calculateDistance(node.geoLocation) / 1000;
    }


    public static void printMatrix(String title, double[][] matrix) {
        System.out.println(title);
        for (double[] row : matrix) {
            for (double value : row) {
                System.out.printf("%.2f ", value); 
            }
            System.out.println(); 
        }
    }
    
    public static void printClusterAssignments(
            HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> clusterAssignment) {
        
        for (Map.Entry<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> entry :
            clusterAssignment.entrySet()) {
            
            WorkflowComputingAppliance center = entry.getKey();
            ArrayList<WorkflowComputingAppliance> nodes = entry.getValue();
            
            System.out.println("Cluster Center: " + center.name);
            System.out.println("Nodes:");
            for (WorkflowComputingAppliance node : nodes) {
                System.out.println(" - " + node.name);
            }
            System.out.println(); 
        }
    }
    
    public static WorkflowComputingAppliance calculateAveragePairwiseDistance(
            HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> clusters) {
        
        WorkflowComputingAppliance clusterWithSmallestAvg = null;
        double smallestAvgDistance = Double.MAX_VALUE;
        
        for (HashMap.Entry<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> entry : clusters.entrySet()) {
            WorkflowComputingAppliance key = entry.getKey();
            ArrayList<WorkflowComputingAppliance> value = entry.getValue();

            ArrayList<WorkflowComputingAppliance> mergedList = new ArrayList<>();
            mergedList.add(key);
            mergedList.addAll(value);

            double totalDistance = 0.0;
            int totalPairs = 0;

            for (int i = 0; i < mergedList.size(); i++) {
                for (int j = i + 1; j < mergedList.size(); j++) {
                    WorkflowComputingAppliance node1 = mergedList.get(i);
                    WorkflowComputingAppliance node2 = mergedList.get(j);
                    totalDistance += calculateHeuristic(node1, node2);
                    totalPairs++; 
                }
            }

            double avgDistance = totalPairs == 0 ? 0.0 : totalDistance / totalPairs;
            
            if (avgDistance < smallestAvgDistance) {
                smallestAvgDistance = avgDistance;
                clusterWithSmallestAvg = key;
            }

            System.out.println("Cluster " + key.name + " Average Pairwise Distance: " + avgDistance + " (km)");
        }
        System.out.println(clusterWithSmallestAvg.name);
        return clusterWithSmallestAvg;
    }
}
