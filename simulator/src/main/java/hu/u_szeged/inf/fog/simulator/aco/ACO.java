package hu.u_szeged.inf.fog.simulator.aco;

import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;

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
    private double[][] pheromoneMatrix;

    public ACO(int numberOfClusters, int numberOfNodes, int numberOfAnts, double evaporationRate, int maxIterations, double randomFactor) {
        this.numberOfClusters = numberOfClusters;
        this.numberOfNodes = numberOfNodes;
        this.numberOfAnts = numberOfAnts;
        this.evaporationRate = evaporationRate;
        this.maxIterations = maxIterations;
        this.randomFactor = randomFactor;

        this.pheromoneMatrix = new double[numberOfNodes][numberOfClusters];
        for (int i = 0; i < numberOfNodes; i++) {
            //Arrays.fill(pheromoneMatrix[i], 0.1);
            for (int j = 0; j < numberOfClusters; j++) {
                pheromoneMatrix[i][j] = Math.random();
            }
        }
        ACO.printMatrix("inital pheromone matrix: ", pheromoneMatrix);
    }

    public void runACO(LinkedHashMap<Object, Instance> workflowArchitecture, ArrayList<Object> centerNodes) {
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            Ant[] ants = new Ant[numberOfAnts];

            // Generate ant solutions
            System.out.println("Iteration: " + iteration);
            for (int i = 0; i < numberOfAnts; i++) {
                ants[i] = new Ant(numberOfNodes, numberOfClusters);           
                ants[i].generateSolution(pheromoneMatrix, randomFactor, numberOfClusters);
                System.out.println("ant-" + i + " " + Arrays.toString(ants[i].solution));
            }
            calculateFitness(ants, workflowArchitecture, centerNodes);
            updatePheromones(ants);
            evaporatePheromones();
            ACO.printMatrix("updated pheromone matrix: ", pheromoneMatrix);
        }
    }

    /**
     * Calculates fitness level how accurate a solution is.
     * Uses calculateHeuristics for easier change of heuristic
     */
    private void calculateFitness(Ant[] ants, LinkedHashMap<Object, Instance> workflowArchitecture, ArrayList<Object> centerNodes) {
        
        int[][] weightMatrix = new int[numberOfNodes][numberOfClusters];
        
        //fill weightMatrix
        for (Ant ant : ants) {
            for (int j = 0; j < numberOfNodes; j++) {
                for (int k = 0; k < numberOfClusters; k++) {
                    if (ant.solution[j] == k) {
                        weightMatrix[j][k] = 1;
                    } else {
                        weightMatrix[j][k] = 0;
                    }
                }   
            }
            double fitnessLevel = 0;
            System.out.println(Arrays.toString(ant.solution));
            for (int j = 0; j < numberOfNodes; j++) {
                for (int k = 0; k < numberOfClusters; k++) {
                    if (weightMatrix[j][k] != 0) {
                        double heuristic = calculateHeuristics(j, workflowArchitecture, centerNodes.get(k));
                        fitnessLevel += heuristic;
                    }
                }
            }
            ant.fitness = fitnessLevel;
        }
    }
    
    /**
     * This function exist so it's easier to change the heuristics.
     */
    private double calculateHeuristics(int j, LinkedHashMap<Object, Instance> workflowArchitecture, Object centerNode) {
        int i = 0;
        ComputingAppliance node = null;
        ComputingAppliance center = null;
        for (Map.Entry<Object, Instance> entry : workflowArchitecture.entrySet()) { 
            if (i == j) {
                node = (ComputingAppliance) entry.getKey();
                center = (ComputingAppliance) centerNode;
                System.out.println(node.name + " " + center.name);
                return center.geoLocation.calculateDistance(node.geoLocation) / 1000;
            }
            i++;
        }
        return 0;
    }

    private void updatePheromones(Ant[] ants) {
        Arrays.sort(ants);

        // only five ants are considered
        for (int i = 0; i < 5; i++) {
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
}

class Ant implements Comparable<Ant>{
    
    double fitness;
    int[] solution;
    double[] randomSolution;
    
    public Ant(int numberOfNodes, int numberOfClusters) {
        this.fitness = 0;
        this.solution = new int[numberOfNodes];
        this.randomSolution = new double[numberOfNodes];
        for (int i = 0; i < numberOfNodes; i++) {
            randomSolution[i] = Math.random();
        }
    }

    @Override
    public int compareTo(Ant ant) {
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
            if (randomSolution[i] < randomFactor) {
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