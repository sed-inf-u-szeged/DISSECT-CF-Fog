package hu.u_szeged.inf.fog.simulator.workflow.aco;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CentralisedAntOptimiser {

    /**
     * The global pheromone matrix is to be updated depending on the quality of solutions
     * produced by the agents.
     */
    private static double[][] globalPheromoneMatrix;

    public static HashMap<Integer, ArrayList<WorkflowComputingAppliance>> runOptimiser(int clusterCount,
            ArrayList<WorkflowComputingAppliance> nodesToBeClustered, 
            int numberOfAnts, int numberOfIteration, double probability, double topPercentAnts,
            double pheromoneIncrement, double evaporationRate) {
        
        globalPheromoneMatrix = new double[nodesToBeClustered.size()][clusterCount];
        for (int i = 0; i < nodesToBeClustered.size(); i++) {
            for (int j = 0; j < clusterCount; j++) {
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
            
            calculateFitness(clusterCount, nodesToBeClustered, ants);
            for (CentralisedAnt ant : ants) {
                System.out.println(ant.id + ": " + Arrays.toString(ant.solution) + " " + ant.fitness);
            }
            updatePheromones(nodesToBeClustered, ants, topPercentAnts, pheromoneIncrement);
            evaporatePheromones(globalPheromoneMatrix, evaporationRate);
            printMatrix("updated pheromone matrix: ", globalPheromoneMatrix);
        }
        return generateClusters(clusterCount, globalPheromoneMatrix, nodesToBeClustered);
    }
    
    private static HashMap<Integer, ArrayList<WorkflowComputingAppliance>> generateClusters(
            int clusterCount, double[][] globalPheromoneMatrix, ArrayList<WorkflowComputingAppliance> nodesToBeClustered) {

        HashMap<Integer, ArrayList<WorkflowComputingAppliance>> clusterAssignment = new HashMap<>();
        for (int i = 0; i < clusterCount; i++) {
            clusterAssignment.computeIfAbsent(i, k -> new ArrayList<>());
        }

        for (int i = 0; i < globalPheromoneMatrix.length; i++) {
            int clusterIndex = 0;
            double maxValue = globalPheromoneMatrix[i][0];
            
            for (int j = 1; j < globalPheromoneMatrix[i].length; j++) {
                if (globalPheromoneMatrix[i][j] > maxValue) {
                    maxValue = globalPheromoneMatrix[i][j];
                    clusterIndex = j;
                }
            }
            clusterAssignment.get(clusterIndex).add(nodesToBeClustered.get(i));
        }

        return clusterAssignment;
    }

    private static void evaporatePheromones(double[][] globalPheromoneMatrix, double evaporationRate) {
       
        for (int i = 0; i < globalPheromoneMatrix.length; i++) {
            for (int j = 0; j < globalPheromoneMatrix[i].length; j++) {
                globalPheromoneMatrix[i][j] *= (1 - evaporationRate);
                if (globalPheromoneMatrix[i][j] < 0.01) {
                    globalPheromoneMatrix[i][j] = 0.01;
                }
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

    private static void calculateFitness(int clusterCount, 
            ArrayList<WorkflowComputingAppliance> nodesToBeClustered, CentralisedAnt[] ants) {
        
        for (CentralisedAnt ant : ants) {
            Map<Integer, ArrayList<WorkflowComputingAppliance>> clusters = new HashMap<>();

            for (int i = 0; i < clusterCount; i++) {
                clusters.computeIfAbsent(i, k -> new ArrayList<>());
            }
            for (int j = 0; j < ant.solution.length; j++) {
                clusters.get(ant.solution[j]).add(nodesToBeClustered.get(j));
            }
            
            
            for (Map.Entry<Integer, ArrayList<WorkflowComputingAppliance>> entry : clusters.entrySet()) {
                // System.out.println("Cluster " + entry.getKey() + ":");
                ArrayList<WorkflowComputingAppliance> cluster = entry.getValue();
                
                if (cluster.size() < 2) {
                    ant.fitness = Double.MAX_VALUE;
                    break;
                }
                
                double sum = 0.0;
                for (int i = 0; i < cluster.size(); i++) {
                    for (int j = i + 1; j < cluster.size(); j++) {
                        sum += calculateHeuristic(cluster.get(i), cluster.get(j));
                    }
                }
                int numPairs = cluster.size() * (cluster.size() - 1) / 2;
                double avgIntraDist = sum / numPairs;
                ant.fitness += avgIntraDist * Math.pow(cluster.size(), 1.5); 
            }          
            //System.out.println(ant.fitness);
        }
    }
    
    static double calculateHeuristic(WorkflowComputingAppliance node, WorkflowComputingAppliance center) {
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
            HashMap<Integer, ArrayList<WorkflowComputingAppliance>> clusterAssignment) {
        
        for (Map.Entry<Integer, ArrayList<WorkflowComputingAppliance>> entry :
            clusterAssignment.entrySet()) {
            
            WorkflowComputingAppliance center = entry.getValue().get(0);
            ArrayList<WorkflowComputingAppliance> nodes = entry.getValue();
            
            System.out.println("Cluster Center: " + center.name);
            System.out.println("Nodes:");
            for (WorkflowComputingAppliance node : nodes) {
                System.out.println(" - " + node.name);
            }
            System.out.println(); 
        }
    }
    
    public static double calculateAvgPairwiseDistance(ArrayList<WorkflowComputingAppliance> list) {
        double totalDistance = 0.0;
        int totalPairs = 0;

        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                WorkflowComputingAppliance node1 = list.get(i);
                WorkflowComputingAppliance node2 = list.get(j);
                totalDistance += calculateHeuristic(node1, node2);
                totalPairs++;
            }
        }

        return totalPairs == 0 ? 0.0 : totalDistance / totalPairs;
    }
    
    public static List<ArrayList<WorkflowComputingAppliance>> sortClustersByAveragePairwiseDistance(
            HashMap<Integer, ArrayList<WorkflowComputingAppliance>> clusters) {

        return clusters.entrySet().stream()
            .map(entry -> {
               // WorkflowComputingAppliance key = entry.getKey();
                ArrayList<WorkflowComputingAppliance> value = entry.getValue();

                ArrayList<WorkflowComputingAppliance> mergedList = new ArrayList<>();
              //  mergedList.add(key);
                mergedList.addAll(value);

                double avgDistance = calculateAvgPairwiseDistance(mergedList);

                return new AbstractMap.SimpleEntry<>(mergedList, avgDistance);
            })
            .sorted(Comparator.comparingDouble(Map.Entry::getValue)) 
            .map(Map.Entry::getKey) 
            .collect(Collectors.toList());
    }

}
