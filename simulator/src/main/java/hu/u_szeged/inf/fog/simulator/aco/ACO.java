package hu.u_szeged.inf.fog.simulator.aco;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.DecentralizedWorkflowScheduler;

import java.util.*;

public class ACO {

    private int numberOfClusters;
    private int numberOfNodes;
    private double[][] pheromoneMatrix;
    private int numberOfAnts;
    private double evaporationRate;
    private int maxIterations;
    private double randomFactor;

    /**
     * Ant colony based algorithm constructor, it initializes the pheromone matrix with fix 0.2 value
     * @param numberOfClusters
     * @param numberOfNodes minus the orchestration Nodes (numberOfNodes-numberOfClusters)
     * @param numberOfAnts
     * @param evaporationRate
     * @param maxIterations
     * @param randomFactor
     */
    public ACO(int numberOfClusters, int numberOfNodes, int numberOfAnts, double evaporationRate,
               int maxIterations, double randomFactor) {
        this.numberOfClusters = numberOfClusters;
        this.numberOfNodes = numberOfNodes-numberOfClusters;
        this.numberOfAnts = numberOfAnts;
        this.evaporationRate = evaporationRate;
        this.maxIterations = maxIterations;
        this.randomFactor = randomFactor;

        // Initialize pheromone matrix
        this.pheromoneMatrix = new double[numberOfNodes][numberOfClusters];
        for (int i = 0; i < numberOfNodes; i++) {
            Arrays.fill(pheromoneMatrix[i], 2);
        }
    }

    public void runACO(LinkedHashMap<Object, Instance> workflowArchitecture,
                       ArrayList<Actuator> actuatorArchitecutre, ArrayList<DecentralizedWorkflowScheduler> workflowSchedulers) {
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            Ant[] ants = new Ant[numberOfAnts];

            // Generate ant solutions
            for (int i = 0; i < numberOfAnts; i++) {
                ants[i] = new Ant(numberOfNodes,numberOfClusters);
                ants[i].generateSolution(pheromoneMatrix, randomFactor, numberOfClusters);
                calculateFitness(ants, workflowArchitecture);
            }

            updatePheromones(ants);
            
            evaporatePheromones();
        }
    }

    /**
     * Calculates fitness level how accurate a solution is.
     * Uses calculateHeuristics for easier change of heuristic
     * With the change of numberOfSolutions variable you can set how many solution you want to use to update
     * the pheromoneMatrix
     * @param ants
     */
    private void calculateFitness(Ant[] ants, LinkedHashMap<Object, Instance> workflowArchitecture){
        double fitnessLevel = 0;
        int[][] weightMatrix = new int[numberOfNodes][numberOfClusters];
        for (Ant ant : ants) {
            //fill weightMatrix
            for (int j = 0; j < numberOfNodes; j++) {
                for (int l = 0; l < numberOfClusters + 1; l++) {
                    if (ant.solution[j] == l) {
                        weightMatrix[j][l] = 1;
                    } else {
                        weightMatrix[j][l] = 0;
                    }
                }
            }
            for (int j = 0; j < numberOfNodes; j++) {
                for (int l = 0; l < numberOfClusters; l++) {
                    if(weightMatrix[j][l]!=0){
                        fitnessLevel += weightMatrix[j][l] * (Math.pow(Math.abs(pheromoneMatrix[j][l] -
                                calculateHeuristics(j,l,workflowArchitecture)), 2));
                    }
                }
            }
            ant.fitness = fitnessLevel;
        }
    }
    /**
     * This function exist so it's easier to change the heuristics.
     * @return heuristic number
     */
    private double calculateHeuristics(int x, int y, LinkedHashMap<Object, Instance> workflowArchitecture){
        int i=0;
        ComputingAppliance Node = null;
        ComputingAppliance Orchestrator = null;
        for (Map.Entry<Object, Instance> entry : workflowArchitecture.entrySet()) {
            if(i==y){
                Orchestrator= (ComputingAppliance) entry.getKey();
            }
            else if(i==x+numberOfClusters){
                Node= (ComputingAppliance) entry.getKey();
            }
            i++;
            if(Node!=null && Orchestrator!=null){
                break;
            }
        }
        return Orchestrator.geoLocation.calculateDistance(Node.geoLocation)/1000;
    }

    private void updatePheromones(Ant[] ants) {
        Arrays.sort(ants);
        for(int i=0; i<5 ;i++){
            for(int j=0; j<numberOfNodes;j++){
                pheromoneMatrix[j][ants[i].solution[j]]+=1;
            }
        }

    }
    private void evaporatePheromones() {
        for(int i=0;i<numberOfNodes;i++){
            for(int j=0;j<numberOfClusters;j++){
                pheromoneMatrix[i][j]=pheromoneMatrix[i][j]*(1-evaporationRate);
            }
        }
    }
    public int getNumberOfClusters(){
        return this.numberOfClusters;
    }
    public int getNumberOfNodes(){
        return this.numberOfNodes;
    }
    private static class Ant {
        private double fitness;
        private int[] solution;
        private double[] randomSolution;
        public Ant(int numberOfNodes, int numberOfClusters) {
            this.fitness = 0;
            this.solution = new int[numberOfNodes];
            this.randomSolution = new double[numberOfNodes];
            for (int i = 0; i < numberOfNodes; i++) {
                randomSolution[i] = Math.random();
            }
        }

        /**
         * Generates a solution "String" which is int array for given ant.
         * Uses both exploitation and biased exploration.
         * @param pheromoneMatrix
         * @param randomFactor
         * @param numberOfClusters
         */
        public void generateSolution(double[][] pheromoneMatrix, double randomFactor, int numberOfClusters) {
            double max = 0, sum = 0, normalizer, randomNumber;
            int maxIndex = 0;
            for(int i = 0; i<solution.length; i++){
                if(randomSolution[i] < (1 - randomFactor)){
                    for(int j = 0; j < numberOfClusters; j++){
                        if(pheromoneMatrix[i][j] > max){
                            max = pheromoneMatrix[i][j];
                            maxIndex = j;
                        }
                    }
                }
                else{
                    randomNumber =  Math.random();
                    for(int j = 0; j < numberOfClusters; j++){
                        sum += pheromoneMatrix[i][j];
                    }
                    normalizer = 1 / sum;
                    sum=0;
                    for(int j = 0; j < numberOfClusters; j++){
                        sum += pheromoneMatrix[i][j]*normalizer;
                        if(randomNumber < sum){
                            maxIndex = j;
                        }
                    }
                }
                solution[i] = maxIndex;
            }
        }
    }
    public class sortAnts implements Comparator<Ant> {
        public int compare(Ant p1, Ant p2) {
            if (p1.fitness < p2.fitness) return -1;
            if (p1.fitness > p2.fitness) return 1;
            return 0;
        }
    }
}