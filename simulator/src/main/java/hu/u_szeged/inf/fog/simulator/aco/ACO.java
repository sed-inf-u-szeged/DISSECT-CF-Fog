package hu.u_szeged.inf.fog.simulator.aco;
import java.util.Arrays;

public class ACO {

    private int numberOfClusters;
    private int numberOfNodes;
    private double[][] pheromoneMatrix;
    private int numberOfAnts;
    private double alpha; // Pheromone influence
    private double beta;  // Distance influence
    private double evaporationRate;
    private int maxIterations;
    private double randomFactor;

    public ACO(int numberOfClusters, int numberOfNodes, int numberOfAnts, double alpha, double beta, double evaporationRate, int maxIterations, double randomFactor) {
        this.numberOfClusters = numberOfClusters;
        this.numberOfNodes = numberOfNodes;
        this.numberOfAnts = numberOfAnts;
        this.alpha = alpha;
        this.beta = beta;
        this.evaporationRate = evaporationRate;
        this.maxIterations = maxIterations;
        this.randomFactor = randomFactor;

        // Initialize pheromone matrix
        this.pheromoneMatrix = new double[numberOfNodes][numberOfClusters];
        for (int i = 0; i < numberOfNodes; i++) {
            Arrays.fill(pheromoneMatrix[i], 0.2);
        }
    }

    public void runACO() {
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            Ant[] ants = new Ant[numberOfAnts];

            // Generate ant solutions
            for (int i = 0; i < numberOfAnts; i++) {
                ants[i] = new Ant(numberOfNodes);
                ants[i].generateSolution(pheromoneMatrix, alpha, beta, randomFactor, numberOfClusters);
                calculateFitness(ants);
            }

            // Update pheromones
            updatePheromones(ants);
            
            //TODO evaporatePheromones
        }
    }

    /**
     * Calculates fitness level how accurate a solution is.
     * Uses calculateHeuristics for easier change of heuristic
     * @param ants
     */
    private void calculateFitness(Ant[] ants){
        double fitnessLevel = 0;
        double sum = 0;
        double[][] distanceMatrix = new double[numberOfNodes][numberOfClusters];
        int[][] weightMatrix = new int[numberOfNodes][numberOfClusters];
        for(int i = 0; i < ants.length; i++){
            //fill wightMatrix
            for(int j = 0; j < numberOfNodes; j++){
                for(int l = 1; l < numberOfClusters+1; l++){
                    if(ants[i].solution[j]==l){
                        weightMatrix[j][l]=1;
                    }
                    else{
                        weightMatrix[j][l]=0;
                    }
                }
            }
            for(int j = 0; j < numberOfNodes; j++){
                for (int l = 0; l < numberOfClusters; l++){
                    sum += weightMatrix[j][l]*(Math.pow(Math.abs(pheromoneMatrix[j][l]-calculateHeuristics()),2));
                }
            }
            ants[i].fitness=sum;
        }
    }
    /**
     * This function exist so it's easier to change the heuristics.
     * @return heuristic number
     */
    private double calculateHeuristics(){
        double heuristic = 0;
        //TODO implement distance based heuristic
        return heuristic;
    }

    private void updatePheromones(Ant[] ants) {
        //TODO updating
    }
    public static void main(String[] args) {
        int numberOfClusters=3;
        int numberOfNodes=8;
        int numberOfAnts = 10;
        double alpha = 1.0;
        double beta = 2.0;
        double evaporationRate = 0.5;
        int maxIterations = 100;
        double randomFactor = 0.01;

        ACO aco = new ACO(numberOfClusters, numberOfNodes, numberOfAnts, alpha, beta, evaporationRate, maxIterations, randomFactor);
        aco.runACO();
    }

    private static class Ant {
        private double fitness;
        private int[] solution;
        private double[] randomSolution;
        public Ant(int numberOfNodes) {
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
         * @param alpha
         * @param beta
         * @param randomFactor
         * @param numberOfClusters
         */
        public void generateSolution(double[][] pheromoneMatrix, double alpha, double beta, double randomFactor, int numberOfClusters) {
            double max = 0, sum = 0, normalizer = 0, randomNumber = 0;
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
}