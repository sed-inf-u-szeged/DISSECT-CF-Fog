package hu.u_szeged.inf.fog.simulator.workflow.aco;

import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Acoc {
    private int numberOfNodes;
    private int numberOfClusters;
    private final double evaporationRate;
    private final int maxIterations;
    private final double randomFactor;
    private final int baseLatency;
    private List<Ant> ants;
    private ArrayList<LinkedHashMap<WorkflowComputingAppliance, Instance>> workflowArchitectures;

    /**
     * Ant colony based algorithm constructor, it initializes the pheromone matrix with fix 0.2 value
    */
    public Acoc(double evaporationRate, int maxIterations, double randomFactor, int baseLatency) {
        this.numberOfNodes = 0;
        this.numberOfClusters = 0;
        this.evaporationRate = evaporationRate;
        this.maxIterations = maxIterations;
        this.randomFactor = randomFactor;
        this.baseLatency = baseLatency;
        this.ants = new ArrayList<>();
        this.workflowArchitectures = new ArrayList<>();
    }

    public ArrayList<LinkedHashMap<WorkflowComputingAppliance, Instance>> runAcoc(
            LinkedHashMap<WorkflowComputingAppliance, Instance> workflowArchitecture,
                        ArrayList<Actuator> actuatorArchitecutre) throws IOException {
        numberOfNodes = workflowArchitecture.size();
        for (Map.Entry<WorkflowComputingAppliance, Instance> entry : workflowArchitecture.entrySet()) {
            Ant ant = new Ant(numberOfNodes, entry.getKey(), entry.getValue());
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                ant.generateSolution(randomFactor, entry.getKey(), workflowArchitecture, iteration, evaporationRate);

                evaporatePheromones(ant, iteration);
            }
            ants.add(ant);
        }

        generateClusters();
        // AcoVisualiser.mapGenerator(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, ants);
        generateArchitectures(baseLatency);
        return workflowArchitectures;

    }

    private void evaporatePheromones(Ant ant, int position) {
        for (int i = 0; i < ant.pheromoneMatrix.length; i++) {
            if (i != position) {
                ant.pheromoneMatrix[i] = ant.pheromoneMatrix[i] * (1 - evaporationRate);
            }
        }
    }

    private void generateClusters() {
        numberOfClusters = 0;
        if (numberOfNodes == 1) {
            ants.get(0).clusterNumber = 1;
            return;
        }
        for (int i = 0; i < numberOfNodes; i++) {
            Ant anti = ants.get(i);
            for (int j = 0; j < numberOfNodes; j++) {
                if (i != j) {
                    Ant antj = ants.get(j);
                    if (anti.mergeMatrix[j] == 0 & antj.mergeMatrix[i] == 0) {
                        double number = (anti.pheromoneMatrix[j] + antj.pheromoneMatrix[i]) / 2;
                        anti.mergeMatrix[j] = number;
                        antj.mergeMatrix[i] = number;
                        if (anti.bestValue < number) {
                            anti.bestValue = number;
                            anti.bestNode = j;
                        }
                        if (antj.bestValue < number) {
                            antj.bestValue = number;
                            antj.bestNode = i;
                        }
                    } else if (anti.mergeMatrix[j] != 0) {
                        double number = anti.pheromoneMatrix[j];
                        antj.mergeMatrix[i] = number;
                        if (antj.bestValue < number) {
                            antj.bestValue = number;
                            antj.bestNode = i;
                        }
                    } else if (antj.mergeMatrix[i] != 0) {
                        double number = antj.pheromoneMatrix[i];
                        anti.mergeMatrix[j] = number;
                        if (anti.bestValue < number) {
                            anti.bestValue = number;
                            anti.bestNode = j;
                        }
                    }
                }
            }
        }
        Ant bestAnt = null;
        boolean runCondition = true;
        while (runCondition) {
            double localMax = 0;
            for (Ant ant : ants) {
                if (localMax < ant.bestValue & ant.clusterNumber == -1) {
                    localMax = ant.bestValue;
                    bestAnt = ant;
                }
            }
            Ant pairAnt = ants.get(bestAnt.bestNode);
            if (numberOfClusters == 0) {
                numberOfClusters++;
                bestAnt.clusterNumber = numberOfClusters;
                pairAnt.clusterNumber = numberOfClusters;
                mergeMatrixes(bestAnt, pairAnt);
            } else if (bestAnt.clusterNumber == -1 & pairAnt.clusterNumber == -1) {
                numberOfClusters++;
                pairAnt.clusterNumber = numberOfClusters;
                bestAnt.clusterNumber = numberOfClusters;
                mergeMatrixes(bestAnt, pairAnt);
            } else {
                if (bestAnt.clusterNumber != -1) {
                    pairAnt.clusterNumber = bestAnt.clusterNumber;
                    mergeMatrixes(bestAnt, pairAnt);
                } else {
                    bestAnt.clusterNumber = pairAnt.clusterNumber;
                    mergeMatrixes(bestAnt, pairAnt);
                }
            }

            int counter = 0;
            for (Ant ant : ants) {
                counter += ant.clusterNumber != -1 ? 1 : 0;
            }
            runCondition = counter != numberOfNodes;
            if (numberOfNodes == 3) {
                for (Ant ant : ants) {
                    if (ant.clusterNumber == -1) {
                        ant.clusterNumber = 2;
                    }
                }
                return;
            }
            if (runCondition) {
                for (Ant ant : ants) {
                    ant.bestValue = 0;
                    if (numberOfClusters <= 1) {
                        for (int i = 0; i < ant.mergeMatrix.length; i++) {
                            if (ant.bestValue < ant.mergeMatrix[i] 
                                    & ant.clusterNumber == -1 & ants.get(i).clusterNumber == -1) {
                                ant.bestValue = ant.mergeMatrix[i];
                                ant.bestNode = i;
                            }
                        }
                    } else {
                        for (int i = 0; i < ant.mergeMatrix.length; i++) {
                            if (ant.bestValue < ant.mergeMatrix[i]) {
                                ant.bestValue = ant.mergeMatrix[i];
                                ant.bestNode = i;
                            }
                        }
                    }
                }
            }
        }
    }

    private void mergeMatrixes(Ant first, Ant second) {
        for (int i = 0; i < first.pheromoneMatrix.length; i++) {
            if (first.pheromoneMatrix[i] == 0 | second.pheromoneMatrix[i] == 0) {
                first.pheromoneMatrix[i] = 0;
                first.mergeMatrix[i] = 0;
                second.pheromoneMatrix[i] = 0;
                second.mergeMatrix[i] = 0;
            } else {
                double number = first.mergeMatrix[i];
                first.pheromoneMatrix[i] = number;
                second.pheromoneMatrix[i] = number;
            }
        }
    }
    
    private void generateArchitectures(int latency) {
        for (int i = 0; i < numberOfClusters; i++) {
            LinkedHashMap<WorkflowComputingAppliance, Instance> workflowArchiteture = new LinkedHashMap<>();
            workflowArchitectures.add(workflowArchiteture);
        }
        for (Ant ant : ants) {
            LinkedHashMap<WorkflowComputingAppliance, Instance> workflowArchiteture = 
                    workflowArchitectures.get(ant.clusterNumber - 1);
            workflowArchiteture.put(ant.node, ant.instance);
        }
        for (LinkedHashMap<WorkflowComputingAppliance, Instance> workflowArchitecture : workflowArchitectures) {
            int i = 0;
            for (Map.Entry<WorkflowComputingAppliance, Instance> entry : workflowArchitecture.entrySet()) {
                int j = 0;
                for (Map.Entry<WorkflowComputingAppliance, Instance> entry2 : workflowArchitecture.entrySet()) {
                    if (j >= i + 1 && j != i) {
                        WorkflowComputingAppliance ca = entry.getKey();
                        ca.addNeighbor(entry2.getKey(), latency);
                    }
                    j++;
                }
                i++;
            }
        }
    }
    
    public int getNumberOfClusters() {
        return numberOfClusters;
    }
}

class Ant implements Comparable<Ant> {
    double bestValue;
    int bestNode;
    int clusterNumber;
    double[] pheromoneMatrix;
    double[] mergeMatrix;
    WorkflowComputingAppliance node;
    Instance instance;

    public Ant(int numberOfNodes, WorkflowComputingAppliance node, Instance instance) {
        this.node = node;
        this.instance = instance;
        this.clusterNumber = -1;
        this.bestValue = 0;
        this.pheromoneMatrix = new double[numberOfNodes];
        this.mergeMatrix = new double[numberOfNodes];
        Arrays.fill(pheromoneMatrix, 0.2);
        Arrays.fill(mergeMatrix, 0);
    }
    
    @Override
    public int compareTo(Ant ant) {
        if (this.bestValue < ant.bestValue) {
            return -1;
        }
        if (this.bestValue > ant.bestValue) {
            return 1;
        }
        return 0;
    }

    /**
     * Generates a solution "String" which is int array for given ant.
     * Uses both exploitation and biased exploration.
     */
    public void generateSolution(double randomFactor, WorkflowComputingAppliance node,
            LinkedHashMap<WorkflowComputingAppliance, Instance> workflowArchitecture,
            int position, double evaporationRate) {
        double randomNumber;
        double number;
        
        for (int i = 0; i < pheromoneMatrix.length; i++) {
            if (i != position) {
                randomNumber = Math.random();
                if (randomNumber > randomFactor) {
                    pheromoneMatrix[i] = gaussianKernel(calculateHeuristics(i, node, workflowArchitecture) / 1000, 2)
                            + pheromoneMatrix[i];

                } else {
                    number = (evaporationRate * pheromoneMatrix[i]) / (1 - evaporationRate);
                    pheromoneMatrix[i] += number;
                }
            } else {
                pheromoneMatrix[i] = 0;
            }
        }
    }
    
    public double gaussianKernel(double x, double h) {
        double u = x / h;
        return (1 / (Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * Math.pow(u, 2));
    }
    
    /**
     * This function exist so it's easier to change the heuristics.
     */
    private double calculateHeuristics(int x, WorkflowComputingAppliance current, 
            LinkedHashMap<WorkflowComputingAppliance, Instance> workflowArchitecture) {
        int i = 0;
        WorkflowComputingAppliance node = null;
        for (Map.Entry<WorkflowComputingAppliance, Instance> entry : workflowArchitecture.entrySet()) {
            if (i == x) {
                node = entry.getKey();
            }
            i++;
            if (node == current) {
                return Double.MAX_VALUE;
            }
            if (node != null) {
                break;
            }
        }
        return current.geoLocation.calculateDistance(node.geoLocation) / 1000;
    }
}