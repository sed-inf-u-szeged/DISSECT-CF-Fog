package hu.u_szeged.inf.fog.simulator.aco;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class ACO {

    private int numberOfClusters;    
    private int numberOfNodes;
    private int numberOfAnts;
    private double evaporationRate;
    private int maxIterations;
    private double randomFactor;
    private int defaultLatency;
    private double[][] pheromoneMatrix;

    public ACO(int numberOfClusters, int numberOfNodes, int numberOfAnts,
               double evaporationRate, int maxIterations, double randomFactor, int defaultLatency) {
        this.numberOfClusters = numberOfClusters;
        this.numberOfNodes = numberOfNodes;
        this.numberOfAnts = numberOfAnts;
        this.evaporationRate = evaporationRate;
        this.maxIterations = maxIterations;
        this.randomFactor = randomFactor;
        this.defaultLatency = defaultLatency;

        this.pheromoneMatrix = new double[numberOfNodes][numberOfClusters];
        for (int i = 0; i < numberOfNodes; i++) {
            //Arrays.fill(pheromoneMatrix[i], 1);
            for (int j = 0; j < numberOfClusters; j++) {
                pheromoneMatrix[i][j] = Math.random();
            }
        }
        ACO.printMatrix("inital pheromone matrix: ", pheromoneMatrix);
    }

    public ArrayList<LinkedHashMap<ComputingAppliance, Instance>> runACO(LinkedHashMap<ComputingAppliance, Instance> workflowArchitecture,
                                                                         ArrayList<ComputingAppliance> centerNodes) throws IOException {
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            SolutionAnt[] ants = new SolutionAnt[numberOfAnts];

            // Generate ant solutions
            System.out.println("Iteration: " + iteration);
            for (int i = 0; i < numberOfAnts; i++) {
                ants[i] = new SolutionAnt(numberOfNodes, numberOfClusters);
                ants[i].generateSolution(pheromoneMatrix, randomFactor, numberOfClusters);
                System.out.println("ant-" + i + " " + Arrays.toString(ants[i].solution));
            }
            calculateFitness(ants, workflowArchitecture, centerNodes);
            updatePheromones(ants,numberOfAnts);
            evaporatePheromones();
            ACO.printMatrix("updated pheromone matrix: ", pheromoneMatrix);
        }
        ArrayList<LinkedHashMap<ComputingAppliance, Instance>> workflowArchitectures = generateArchitetures(workflowArchitecture,centerNodes);
        Visualiser.mapGenerator(ScenarioBase.scriptPath,ScenarioBase.resultDirectory,workflowArchitectures);
        return workflowArchitectures;
    }

    /**
     * Calculates fitness level how accurate a solution is.
     * Uses calculateHeuristics for easier change of heuristic
     */
    private void calculateFitness(SolutionAnt[] ants, LinkedHashMap<ComputingAppliance, Instance> workflowArchitecture, ArrayList<ComputingAppliance> centerNodes) {
        for (SolutionAnt ant : ants) {
            double fitnessLevel = 0;
            for (int i = 0; i < numberOfNodes; i++) {
                double heuristic = calculateHeuristics(i, workflowArchitecture, centerNodes.get(ant.solution[i]));
                fitnessLevel += heuristic/1000;
            }
            ant.fitness = fitnessLevel;
        }
    }
    
    /**
     * This function exist so it's easier to change the heuristics.
     */
    private double calculateHeuristics(int j, LinkedHashMap<ComputingAppliance, Instance> workflowArchitecture, ComputingAppliance centerNode) {
        int i = 0;
        ComputingAppliance node = null;
        ComputingAppliance center = null;
        for (Map.Entry<ComputingAppliance, Instance> entry : workflowArchitecture.entrySet()) {
            if (i == j) {
                node = entry.getKey();
                center = centerNode;
                System.out.println(node.name + " " + center.name);
                return center.geoLocation.calculateDistance(node.geoLocation) / 1000;
            }
            i++;
        }
        return 0;
    }

    private void updatePheromones(SolutionAnt[] ants,int numberOfAnts) {
        Arrays.sort(ants);
        int number = (int) Math.ceil(numberOfAnts*0.2);
        // only top 20% of ants are considered
        for (int i = 0; i < number; i++) {
            for (int j = 0; j < numberOfNodes; j++) {
                pheromoneMatrix[j][ants[i].solution[j]] += 0.1;
            }
        }

    }
    
    private void evaporatePheromones() {
        for (int i = 0; i < numberOfNodes; i++) {
            for (int j = 0; j < numberOfClusters; j++) {
                pheromoneMatrix[i][j] = pheromoneMatrix[i][j] * (1 - evaporationRate);
            }
        }
    }

    public int getNumberOfClusters() {
        return this.numberOfClusters;
    }
    
    public int getNumberOfNodes() {
        return this.numberOfNodes;
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
    public ArrayList<LinkedHashMap<ComputingAppliance, Instance>> generateArchitetures(LinkedHashMap<ComputingAppliance,
            Instance> workflowArchitecture,ArrayList<ComputingAppliance> centerNodes) {
        ArrayList<LinkedHashMap<ComputingAppliance, Instance>> architectures = new ArrayList<>();
        for(int i = 0; i < numberOfClusters; i++) {
            LinkedHashMap <ComputingAppliance, Instance> cluster = new LinkedHashMap<>();
            cluster.put(centerNodes.get(i),workflowArchitecture.get(centerNodes.get(i)));
            architectures.add(cluster);

        }
        int i=0;
        for (Map.Entry<ComputingAppliance, Instance> entry : workflowArchitecture.entrySet()) {
            if(!centerNodes.contains(entry.getKey())) {
                double max = -1;
                int index = 0;
                for (int j = 0; j < numberOfClusters; j++) {
                    if (pheromoneMatrix[i][j] > max) {
                        max = pheromoneMatrix[i][j];
                        index = j;
                    }
                }
                architectures.get(index).put(entry.getKey(), entry.getValue());
            }
            i++;
        }
        for (LinkedHashMap<ComputingAppliance, Instance> Architecture : architectures) {
            i=0;
            for (Map.Entry<ComputingAppliance, Instance> entry : Architecture.entrySet()) {
                int j=0;
                for (Map.Entry<ComputingAppliance, Instance> entry2 : Architecture.entrySet()) {
                    if(j>=i+1 && j!=i){
                        ComputingAppliance ca = entry.getKey();
                        ca.addNeighbor(entry2.getKey(),defaultLatency);
                    }
                    j++;
                }
                i++;
            }
        }
        return architectures;
    }
}

class SolutionAnt implements Comparable<SolutionAnt>{
    
    double fitness;
    int[] solution;
    double[] randomSolution;
    
    public SolutionAnt(int numberOfNodes, int numberOfClusters) {
        this.fitness = 0;
        this.solution = new int[numberOfNodes];
        this.randomSolution = new double[numberOfNodes];
        for (int i = 0; i < numberOfNodes; i++) {
            randomSolution[i] = Math.random();
        }
    }

    @Override
    public int compareTo(SolutionAnt ant) {
        if (this.fitness < ant.fitness) {
            return -1;
        }
        if (this.fitness > ant.fitness) {
            return 1;
        }
        return 0;
    }

    
    public void generateSolution(double[][] pheromoneMatrix, double randomFactor, int numberOfClusters) {
        int maxIndex = 0;

        System.out.println("randomsolution: " + Arrays.toString(randomSolution));
        for (int i = 0; i < solution.length; i++) {
            if (randomSolution[i] > randomFactor) {
                double max = Double.MIN_VALUE;
                
                for (int j = 0; j < numberOfClusters; j++) {
                    if (pheromoneMatrix[i][j] > max) {
                        max = pheromoneMatrix[i][j];
                        maxIndex = j;
                    }
                }
                System.out.println("i: " +maxIndex);
            } else {
                double sum = Double.MIN_VALUE;
                double randomNum = Math.random();
                for (int j = 0; j < numberOfClusters; j++) {
                    sum += pheromoneMatrix[i][j];
                }
                
                for (int j = 0; j < numberOfClusters; j++) {
                    double num = pheromoneMatrix[i][j] / sum;
                    if (num > randomNum) {
                        maxIndex = j;
                    }
                }
                System.out.println("sum: " + sum + " random: " + randomSolution[i]);
                System.out.println(Arrays.toString(pheromoneMatrix[i]));
                System.out.println("ii: " + maxIndex);
            }
            solution[i] = maxIndex;
        }
       
    }

}