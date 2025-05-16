package hu.u_szeged.inf.fog.simulator.workflow.aco;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

/**
 * An agent to build a solution.
 */
public class CentralisedAnt implements Comparable<CentralisedAnt> {
    
    int id;
    
    int[] solution;
    
    double fitness;
    
    public CentralisedAnt(int id) {
        this.id = id;
    }

    @Override
    public int compareTo(CentralisedAnt other) {
        if (this.fitness < other.fitness) {
            return -1;
        }
        if (this.fitness > other.fitness) {
            return 1;
        }
        return 0;
    }

    public void generateSolution(double[][] pheromoneMatrix, int numberOfNodes, double probability) {
        int[] localSolution = new int[numberOfNodes];
        
        double[] randomSolution = new double[numberOfNodes];
        for (int i = 0; i < numberOfNodes; i++) {
            randomSolution[i] = SeedSyncer.centralRnd.nextDouble();
        }
        
        int maxIndex = 0;
        for (int i = 0; i < numberOfNodes; i++) {
            if (randomSolution[i] < probability) {
                double max = Double.MIN_VALUE;
                
                for (int j = 0; j < pheromoneMatrix[i].length; j++) {
                    if (pheromoneMatrix[i][j] > max) {
                        max = pheromoneMatrix[i][j];
                        maxIndex = j;
                    }
                }
            } else {
                //maxIndex = (int) (SeedSyncer.centralRnd.nextInt(pheromoneMatrix[i].length));
                double sum = 0.0;

                for (int j = 0; j < pheromoneMatrix[i].length; j++) {
                    sum += pheromoneMatrix[i][j];
                }

                double randomNum = SeedSyncer.centralRnd.nextDouble() * sum;
                double cumulative = 0.0;

                for (int j = 0; j < pheromoneMatrix[i].length; j++) {
                    cumulative += pheromoneMatrix[i][j];
                    if (randomNum <= cumulative) {
                        maxIndex = j;
                        break;
                    }
                }
            }
            localSolution[i] = maxIndex;
        }
        //System.out.println(Arrays.toString(solution));
        this.solution = localSolution;
    }
}
