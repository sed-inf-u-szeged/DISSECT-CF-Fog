package hu.u_szeged.inf.fog.simulator.aco;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.StandardResourceAgent;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.util.*;
import java.util.stream.Collectors;

public class CentralisedAntOptimiserApplication {

    /**
     * The global pheromone matrix is to be updated depending on the quality of solutions
     * produced by the agents.
     */
    private double[][] globalPheromoneMatrix;
    private AgentApplication application;

    public HashMap<Integer, ArrayList<StandardResourceAgent>> runOptimiser(int clusterCount,
            ArrayList<StandardResourceAgent> nodesToBeClustered,
            int numberOfAnts, int numberOfIteration, double probability, double topPercentAnts,
            double pheromoneIncrement, double evaporationRate, AgentApplication app) {
        
        globalPheromoneMatrix = new double[nodesToBeClustered.size()][clusterCount];

        application = app;

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
    
    private HashMap<Integer, ArrayList<StandardResourceAgent>> generateClusters(
            int clusterCount, double[][] globalPheromoneMatrix, ArrayList<StandardResourceAgent> nodesToBeClustered) {

        HashMap<Integer, ArrayList<StandardResourceAgent>> clusterAssignment = new HashMap<>();
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

    private void evaporatePheromones(double[][] globalPheromoneMatrix, double evaporationRate) {
        for (int i = 0; i < globalPheromoneMatrix.length; i++) {
            for (int j = 0; j < globalPheromoneMatrix[i].length; j++) {
                globalPheromoneMatrix[i][j] *= (1 - evaporationRate);
                if (globalPheromoneMatrix[i][j] < 0.01) {
                    globalPheromoneMatrix[i][j] = 0.01;
                }
            }
        }
    }

    private void updatePheromones(ArrayList<StandardResourceAgent> nodesToBeClustered,
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

    private void calculateFitness(int clusterCount,
            ArrayList<StandardResourceAgent> nodesToBeClustered, CentralisedAnt[] ants) {
        
        for (CentralisedAnt ant : ants) {
            Map<Integer, ArrayList<StandardResourceAgent>> clusters = new HashMap<>();

            for (int i = 0; i < clusterCount; i++) {
                clusters.computeIfAbsent(i, k -> new ArrayList<>());
            }
            for (int j = 0; j < ant.solution.length; j++) {
                clusters.get(ant.solution[j]).add(nodesToBeClustered.get(j));
            }
            
            
            for (Map.Entry<Integer, ArrayList<StandardResourceAgent>> entry : clusters.entrySet()) {
                // System.out.println("Cluster " + entry.getKey() + ":");
                ArrayList<StandardResourceAgent> cluster = entry.getValue();
                
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

    double calculateHeuristic(StandardResourceAgent nodeAgent, StandardResourceAgent centerAgent) {
        double nodeBW = ResourceAgent.normalize((double)ResourceAgent.getAvgBW(nodeAgent), (double)ResourceAgent.minBW, (double)ResourceAgent.maxBW);
        double centerBW = ResourceAgent.normalize((double)ResourceAgent.getAvgBW(centerAgent), (double)ResourceAgent.minBW, (double)ResourceAgent.maxBW);
        double avgBW = (nodeBW + centerBW)/2;

        double nodeLat = ResourceAgent.normalize(ResourceAgent.getAvgLatency(nodeAgent).doubleValue(), ResourceAgent.minLatency.doubleValue(), ResourceAgent.maxLatency.doubleValue());
        double centerLat = ResourceAgent.normalize(ResourceAgent.getAvgLatency(centerAgent).doubleValue(), ResourceAgent.minLatency.doubleValue(), ResourceAgent.maxLatency.doubleValue());
        double avgLat = (nodeLat + centerLat)/2;

        //Price
        double nodePrice = ResourceAgent.normalize(nodeAgent.hourlyPrice, ResourceAgent.minPrice, ResourceAgent.maxPrice);
        double centerPrice = ResourceAgent.normalize(centerAgent.hourlyPrice, ResourceAgent.minPrice, ResourceAgent.maxPrice);
        double avgPrice = (nodePrice + centerPrice)/2;

        //Energy
        //getMin... => returns idle
        //getRange... => returns max-idle
        double nodeEnergy = ResourceAgent.normalize(ResourceAgent.getAvgEnergy(nodeAgent), ResourceAgent.minEnergy, ResourceAgent.maxEnergy);
        double centerEnergy = ResourceAgent.normalize(ResourceAgent.getAvgEnergy(centerAgent), ResourceAgent.minEnergy, ResourceAgent.maxEnergy);
        double avgEnergy = (nodeEnergy + centerEnergy)/2;

        return  (1 - avgBW) * application.bandwidthPriority +
                avgLat * application.latencyPriority +
                avgEnergy * application.energyPriority +
                avgPrice * application.pricePriority;
    }

    public void printMatrix(String title, double[][] matrix) {
        System.out.println(title);
        for (double[] row : matrix) {
            for (double value : row) {
                System.out.printf("%.2f ", value); 
            }
            System.out.println(); 
        }
    }
    
    public void printClusterAssignments(
            HashMap<Integer, ArrayList<StandardResourceAgent>> clusterAssignment) {
        
        for (Map.Entry<Integer, ArrayList<StandardResourceAgent>> entry :
            clusterAssignment.entrySet()) {
            
            StandardResourceAgent center = entry.getValue().get(0);
            ArrayList<StandardResourceAgent> nodes = entry.getValue();
            
            System.out.println("Cluster Center: " + center.name);
            System.out.println("Nodes:");
            for (StandardResourceAgent node : nodes) {
                System.out.println(" - " + node.name);
            }
            System.out.println(); 
        }
    }
}