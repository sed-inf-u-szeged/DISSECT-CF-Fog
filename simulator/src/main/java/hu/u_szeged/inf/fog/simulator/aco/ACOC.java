package hu.u_szeged.inf.fog.simulator.aco;
import hu.u_szeged.inf.fog.simulator.iot.Actuator;
import hu.u_szeged.inf.fog.simulator.iot.EdgeDevice;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.workflow.scheduler.DecentralizedWorkflowScheduler;

import java.util.*;

public class ACOC {
    private int numberOfNodes;
    private double evaporationRate;
    private int maxIterations;
    private double randomFactor;
    private List<Ant> ants;

    /**
     * Ant colony based algorithm constructor, it initializes the pheromone matrix with fix 0.2 value
     * @param numberOfNodes minus the orchestration Nodes (numberOfNodes-numberOfClusters)
     * @param evaporationRate
     * @param maxIterations
     * @param randomFactor
     */
    public ACOC(int numberOfNodes, double evaporationRate,
               int maxIterations, double randomFactor) {
        this.numberOfNodes = numberOfNodes;
        this.evaporationRate = evaporationRate;
        this.maxIterations = maxIterations;
        this.randomFactor = randomFactor;
        this.ants = new ArrayList<Ant>();
    }

    public void runACOC(LinkedHashMap<Object, Instance> workflowArchitecture,
                       ArrayList<Actuator> actuatorArchitecutre, ArrayList<DecentralizedWorkflowScheduler> workflowSchedulers) {
        int i=0;
        for (Map.Entry<Object, Instance> entry : workflowArchitecture.entrySet()) {
            Ant ant = new Ant(numberOfNodes);
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                ant.generateSolution(randomFactor,entry.getKey(), workflowArchitecture,i,evaporationRate);

                evaporatePheromones(ant,i);
            }
            ants.add(ant);
            i++;
        }

        generateClusters(ants);

    }
    private void evaporatePheromones(Ant ant, int position) {
        for(int i=0; i<ant.pheromoneMatrix.length;i++){
            if(i!=position){
                ant.pheromoneMatrix[i] = ant.pheromoneMatrix[i]*evaporationRate;
            }
        }
    }
    private void generateClusters(List<Ant> ants){
        int numberOfClusters = 0;
        for(int i = 0; i<numberOfNodes;i++){
            Ant anti = ants.get(i);
            for(int j = 0; j < numberOfNodes;j++){
                if(i != j){
                    Ant antj = ants.get(j);
                    if(anti.mergeMatrix[j] == 0 & antj.mergeMatrix[i] == 0){
                        double number = (anti.pheromoneMatrix[j]+ antj.pheromoneMatrix[i])/2;
                        anti.mergeMatrix[j] = number;
                        antj.mergeMatrix[i] = number;
                        if(anti.bestValue < number){
                            anti.bestValue = number;
                            anti.bestNode = j;
                        }
                        if(antj.bestValue < number){
                            antj.bestValue = number;
                            antj.bestNode = i;
                        }
                    } else if (anti.mergeMatrix[j] != 0) {
                        double number = anti.pheromoneMatrix[j];
                        antj.mergeMatrix[i] = number;
                        if(antj.bestValue < number){
                            antj.bestValue = number;
                            antj.bestNode = i;
                        }
                    } else if (antj.mergeMatrix[i] != 0 ) {
                        double number = antj.pheromoneMatrix[i];
                        anti.mergeMatrix[j] = number;
                        if(anti.bestValue < number){
                            anti.bestValue = number;
                            anti.bestNode = j;
                        }
                    }
                }
            }
        }
        Ant bestAnt = null;
        boolean runCondition=true;
        while(runCondition){
            double localMax=0;
            for (Ant ant : ants){
                if(localMax < ant.bestValue & ant.clusterNumber == -1){
                    localMax = ant.bestValue;
                    bestAnt = ant;
                }
            }
            Ant pairAnt = ants.get(bestAnt.bestNode);
            //TODO: itt van valami hogy nem jó klasztert kapnak
            if(numberOfClusters==0){
                numberOfClusters++;
                bestAnt.clusterNumber = numberOfClusters;
                pairAnt.clusterNumber = numberOfClusters;
                mergeMatrixes(bestAnt,pairAnt);
            }else if(bestAnt.clusterNumber == -1 & pairAnt.clusterNumber == -1){
                numberOfClusters++;
                pairAnt.clusterNumber = numberOfClusters;
                bestAnt.clusterNumber = numberOfClusters;
                mergeMatrixes(bestAnt,pairAnt);
            }else{
                if(bestAnt.clusterNumber != -1){
                    pairAnt.clusterNumber = bestAnt.clusterNumber;
                    mergeMatrixes(bestAnt,pairAnt);
                }else {
                    bestAnt.clusterNumber = pairAnt.clusterNumber;
                    mergeMatrixes(bestAnt,pairAnt);
                }
            }

            int counter = 0;
            for (Ant ant : ants) {
                counter += ant.clusterNumber != -1 ? 1 : 0;
            }
            runCondition = counter != numberOfNodes;
            if(runCondition){
                for(Ant ant : ants){
                    ant.bestValue = 0;
                    for(int i = 0; i<ant.mergeMatrix.length;i++){
                        if(ant.bestValue < ant.mergeMatrix[i]){
                            ant.bestValue = ant.mergeMatrix[i];
                            ant.bestNode = i;
                        }
                    }
                }
            }
        }
    }

    private void mergeMatrixes(Ant first, Ant second){
        for(int i = 0; i < first.pheromoneMatrix.length; i++){
            if(first.pheromoneMatrix[i]==0 | second.pheromoneMatrix[i]==0){
                first.pheromoneMatrix[i]=0;
                first.mergeMatrix[i]=0;
                second.pheromoneMatrix[i]=0;
                second.mergeMatrix[i]=0;
            } else {
                double number = first.mergeMatrix[i];
                first.pheromoneMatrix[i] = number;
                second.pheromoneMatrix[i] = number;
            }
        }
    }

    private static class Ant {
        private double bestValue;
        private int bestNode;
        private int clusterNumber;
        private double[] pheromoneMatrix;
        private double[] mergeMatrix;
        public Ant(int numberOfNodes) {
            this.clusterNumber = -1;
            this.bestValue=0;
            this.pheromoneMatrix= new double[numberOfNodes];
            this.mergeMatrix = new double[numberOfNodes];
            Arrays.fill(pheromoneMatrix,0.2);
            Arrays.fill(mergeMatrix,0);
        }

        /**
         * Generates a solution "String" which is int array for given ant.
         * Uses both exploitation and biased exploration.
         * @param randomFactor
         */
        public void generateSolution(double randomFactor, Object Node,LinkedHashMap<Object, Instance> workflowArchitecture,int position, double evaporationRate) {
            double randomNumber, number;
            for(int i = 0; i<pheromoneMatrix.length; i++) {
                if (i != position) {
                    randomNumber = Math.random();
                    if (randomNumber > randomFactor) {
                        pheromoneMatrix[i] = gaussianKernel(calculateHeuristics(i, Node, workflowArchitecture)/1000,2)+pheromoneMatrix[i];

                    } else {
                        number = ((1-evaporationRate)*pheromoneMatrix[i])/evaporationRate;
                        pheromoneMatrix[i] += number;
                    }
                }
                else{
                    pheromoneMatrix[i]=0;
                }
            }
        }
        public static double clamp(double value) {
            if (value < 0) {
                return 0;
            } else if (value > 1) {
                return 1;
            } else {
                return value;
            }
        }
        public static double gaussianKernel(double x, double h) {
            double u = x / h;
            return (1 / (Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * Math.pow(u, 2));
        }
        /**
         * This function exist so it's easier to change the heuristics.
         * @return heuristic number
         */
        private double calculateHeuristics(int x, Object current, LinkedHashMap<Object, Instance> workflowArchitecture){
            int i=0;
            Object node = null;
            for (Map.Entry<Object, Instance> entry : workflowArchitecture.entrySet()) {
                if(i == x){
                    node= entry.getKey();
                }
                i++;
                if(node == current){
                    return Double.MAX_VALUE;
                }
                if(node != null){
                    break;
                }
            }
            if(current instanceof ComputingAppliance & node instanceof ComputingAppliance){
                ComputingAppliance orchestrator = (ComputingAppliance) current;
                ComputingAppliance node1 = (ComputingAppliance) node;
                return orchestrator.geoLocation.calculateDistance(node1.geoLocation)/1000;
            }
            else if(current instanceof EdgeDevice & node instanceof ComputingAppliance){
                EdgeDevice orchestrator = (EdgeDevice) current;
                ComputingAppliance node1 = (ComputingAppliance) node;
                return orchestrator.geoLocation.calculateDistance(node1.geoLocation)/1000;
            }
            else if(current instanceof ComputingAppliance & node instanceof EdgeDevice){
                ComputingAppliance orchestrator = (ComputingAppliance) current;
                EdgeDevice node1 = (EdgeDevice) node;
                return orchestrator.geoLocation.calculateDistance(node1.geoLocation)/1000;
            }
            else if(current instanceof EdgeDevice & node instanceof EdgeDevice){
                EdgeDevice orchestrator = (EdgeDevice) current;
                EdgeDevice node1 = (EdgeDevice) node;
                return orchestrator.geoLocation.calculateDistance(node1.geoLocation)/1000;
            }
            return Double.MAX_VALUE;
        }
    }
}