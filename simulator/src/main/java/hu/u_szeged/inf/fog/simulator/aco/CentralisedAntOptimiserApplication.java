package hu.u_szeged.inf.fog.simulator.aco;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.util.*;
import java.util.stream.Collectors;

public class CentralisedAntOptimiserApplication {

    /**
     * The global pheromone matrix is to be updated depending on the quality of solutions
     * produced by the agents.
     */
    private static double[][] globalPheromoneMatrix;
    private static AgentApplication application;

    private static long minBW = Long.MAX_VALUE;
    private static long maxBW = Long.MIN_VALUE;
    private static Integer minLatency = Integer.MAX_VALUE;
    private static Integer maxLatency = Integer.MIN_VALUE;
    private static double minPrice = Double.MAX_VALUE;
    private static double maxPrice = Double.MIN_VALUE;
    private static double minEnergy = Double.MAX_VALUE;
    private static double maxEnergy = Double.MIN_VALUE;

    public static HashMap<Integer, ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>>> runOptimiser(int clusterCount,
            ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> nodesToBeClustered,
            int numberOfAnts, int numberOfIteration, double probability, double topPercentAnts,
            double pheromoneIncrement, double evaporationRate, AgentApplication app) {
        
        globalPheromoneMatrix = new double[nodesToBeClustered.size()][clusterCount];

        application = app;

        minimumsMaximums(nodesToBeClustered);

        normalizePriorities();

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
    
    private static HashMap<Integer, ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>>> generateClusters(
            int clusterCount, double[][] globalPheromoneMatrix, ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> nodesToBeClustered) {

        HashMap<Integer, ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>>> clusterAssignment = new HashMap<>();
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

    private static void updatePheromones(ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> nodesToBeClustered,
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
            ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> nodesToBeClustered, CentralisedAnt[] ants) {
        
        for (CentralisedAnt ant : ants) {
            Map<Integer, ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>>> clusters = new HashMap<>();

            for (int i = 0; i < clusterCount; i++) {
                clusters.computeIfAbsent(i, k -> new ArrayList<>());
            }
            for (int j = 0; j < ant.solution.length; j++) {
                clusters.get(ant.solution[j]).add(nodesToBeClustered.get(j));
            }
            
            
            for (Map.Entry<Integer, ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>>> entry : clusters.entrySet()) {
                // System.out.println("Cluster " + entry.getKey() + ":");
                ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> cluster = entry.getValue();
                
                if (cluster.size() < 2) {
                    ant.fitness = Double.MAX_VALUE;
                    break;
                }
                
                double sum = 0.0;
                for (int i = 0; i < cluster.size(); i++) {
                    for (int j = i + 1; j < cluster.size(); j++) {
                        sum += calculateHeuristic(cluster.get(i).getKey(), cluster.get(j).getKey(),
                                                  cluster.get(i).getValue(), cluster.get(j).getValue());
                    }
                }
                int numPairs = cluster.size() * (cluster.size() - 1) / 2;
                double avgIntraDist = sum / numPairs;
                ant.fitness += avgIntraDist * Math.pow(cluster.size(), 1.5); 
            }          
            //System.out.println(ant.fitness);
        }
    }

    static void normalizePriorities() {
        double sum = application.bandwidthPriority + application.energyPriority + application.pricePriority +  application.latencyPriority;

        if (sum != 1.0) {
            application.bandwidthPriority /= sum;
            application.energyPriority /= sum;
            application.pricePriority /= sum;
            application.latencyPriority /= sum;
        }
    }

    static void minimumsMaximums(ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> nodesToBeClustered) {
        for (Map.Entry<ComputingAppliance, ResourceAgent> item  : nodesToBeClustered) {
            //BW
            if (getBW(item.getKey()) < minBW) {
                minBW = getBW(item.getKey());
            }

            if (getBW(item.getKey()) > maxBW) {
                maxBW = getBW(item.getKey());
            }

            //Latency
            if(getLatency(item.getKey()) < minLatency) {
                minLatency = getLatency(item.getKey());
            }

            if(getLatency(item.getKey()) > maxLatency) {
                maxLatency = getLatency(item.getKey());
            }

            //Energy
            if (getEnergy(item.getKey()) < minEnergy) {
                minEnergy = getEnergy(item.getKey());
            }

            if (getEnergy(item.getKey()) > maxEnergy) {
                maxEnergy = getEnergy(item.getKey());
            }

            //Price
            if (item.getValue().hourlyPrice < minPrice) {
                minPrice = item.getValue().hourlyPrice;
            }

            if (item.getValue().hourlyPrice > maxPrice) {
                maxPrice = item.getValue().hourlyPrice;
            }
        }
    }

    static double normalize(double value, double min, double max) {
        if (min == max) {
            return 0.5;
        }

        return ((value - min) / (max - min));
    }

    static long getBW(ComputingAppliance node) {
        long bw = 0;

        for (int i = 0; i < node.iaas.repositories.size(); i++) {
            bw += node.iaas.repositories.get(i).getDiskbw();
        }

        for (int i = 0; i < node.iaas.machines.size(); i++) {
            bw += node.iaas.machines.get(i).localDisk.getDiskbw();
        }

        return bw/(node.iaas.repositories.size() + node.iaas.machines.size());
    }

    static Integer getLatency(ComputingAppliance node) {
        int latency = 0;

        for (int i = 0; i < node.iaas.repositories.size(); i++) {
            latency += node.iaas.repositories.get(i).getLatencies().get(node.name + "-nodeRepo");
        }

        for (int i = 0; i < node.iaas.machines.size(); i++) {
            latency += node.iaas.machines.get(i).localDisk.getLatencies().get(node.name + "-localRepo");
        }

        return latency/(node.iaas.repositories.size() + node.iaas.machines.size());
    }

    static double getEnergy(ComputingAppliance node) {
        double energy = 0;

        for (int i = 0; i < node.iaas.machines.size(); i++) {
            energy += node.iaas.machines.get(i).getCurrentPowerBehavior().getMinConsumption() + node.iaas.machines.get(i).getCurrentPowerBehavior().getConsumptionRange();
        }

        return energy/(node.iaas.machines.size());
    }

    static double calculateHeuristic(ComputingAppliance node, ComputingAppliance center, ResourceAgent agent1, ResourceAgent agent2) {
        double nodeBW = normalize((double)getBW(node), (double)minBW, (double)maxBW);
        double centerBW = normalize((double)getBW(center), (double)minBW, (double)maxBW);
        double avgBW = (nodeBW + centerBW)/2;

        double nodeLat = normalize(getLatency(node).doubleValue(), minLatency.doubleValue(), maxLatency.doubleValue());
        double centerLat = normalize(getLatency(center).doubleValue(), minLatency.doubleValue(), maxLatency.doubleValue());
        double avgLat = (nodeLat + centerLat)/2;

        //Price
        double nodePrice = normalize(agent1.hourlyPrice, minPrice, maxPrice);
        double centerPrice = normalize(agent2.hourlyPrice, minPrice, maxPrice);
        double avgPrice = (nodePrice + centerPrice)/2;

        //Energy
        //getMin... => returns idle
        //getRange... => returns max-idle
        double nodeEnergy = normalize(getEnergy(node), minEnergy, maxEnergy);
        double centerEnergy = normalize(getEnergy(center), minEnergy, maxEnergy);
        double avgEnergy = (nodeEnergy + centerEnergy)/2;

        return -avgBW * application.bandwidthPriority +
                avgLat * application.latencyPriority +
                avgEnergy * application.energyPriority +
                avgPrice * application.pricePriority;
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
            HashMap<Integer, ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>>> clusterAssignment) {
        
        for (Map.Entry<Integer, ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>>> entry :
            clusterAssignment.entrySet()) {
            
            ComputingAppliance center = entry.getValue().get(0).getKey();
            ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> nodes = entry.getValue();
            
            System.out.println("Cluster Center: " + center.name);
            System.out.println("Nodes:");
            for (Map.Entry<ComputingAppliance, ResourceAgent> node : nodes) {
                System.out.println(" - " + node.getKey().name);
            }
            System.out.println(); 
        }
    }
    
    public static double calculateScore(ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> list) {
        double totalDistance = 0.0;
        int totalPairs = 0;

        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                ComputingAppliance node1 = list.get(i).getKey();
                ComputingAppliance node2 = list.get(j).getKey();
                ResourceAgent agent1 = list.get(i).getValue();
                ResourceAgent agent2 = list.get(j).getValue();
                totalDistance += calculateHeuristic(node1, node2, agent1, agent2);
                totalPairs++;
            }
        }

        return totalPairs == 0 ? 0.0 : totalDistance / totalPairs;
    }
    
    public static List<ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>>> sortClustersByScore(
            HashMap<Integer, ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>>> clusters) {

        return clusters.entrySet().stream()
            .map(entry -> {
                // ComputingAppliance key = entry.getKey();
                ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> value = entry.getValue();

                ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> mergedList = new ArrayList<>();
                //  mergedList.add(key);
                mergedList.addAll(value);

                double avgDistance = calculateScore(mergedList);

                return new AbstractMap.SimpleEntry<>(mergedList, avgDistance);
            })
            .sorted(Comparator.comparingDouble(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

}
