package hu.u_szeged.inf.fog.simulator.workflow.aco;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import java.util.Arrays;

public class DistributedAnt  {
    
    int id;
    
    public DistributedAnt(int id) {
        this.id = id;
    }
    
    double bestValue;
    int bestNode;
    int clusterNumber;
    double[] pheromoneMatrix;
    double[] mergeMatrix;
    WorkflowComputingAppliance node;
    Instance instance;

    public DistributedAnt(int numberOfNodes, WorkflowComputingAppliance node, Instance instance) {
        this.node = node;
        this.instance = instance;
        this.clusterNumber = -1;
        this.bestValue = 0;
        this.pheromoneMatrix = new double[numberOfNodes];
        this.mergeMatrix = new double[numberOfNodes];
        Arrays.fill(pheromoneMatrix, 0.2);
        Arrays.fill(mergeMatrix, 0);
    }

    public void generateSolution(double[] pheromoneVector, int position, double probability, double evaporationRate) {
        for (int i = 0; i < pheromoneVector.length; i++) {
            if (i != position) {
                double randomNumber = SeedSyncer.centralRnd.nextDouble();
                if (randomNumber > probability) {
                    pheromoneVector[i] += gaussianKernel(
                            this.calculateHeuristic((WorkflowComputingAppliance) ComputingAppliance.allComputingAppliances.get(i), 
                                    (WorkflowComputingAppliance) ComputingAppliance.allComputingAppliances.get(position)), 2);

                } else {
                    double number = (evaporationRate * pheromoneVector[i]) / (1 - evaporationRate);
                    pheromoneVector[i] += number;
                }
            } else {
                pheromoneVector[i] = -1;
            }
        }
    }
    
    private double calculateHeuristic(WorkflowComputingAppliance node, WorkflowComputingAppliance center) {
        return center.geoLocation.calculateDistance(node.geoLocation) / 1000;
    }
    
    public double gaussianKernel(double x, double h) {
        double u = x / h;
        return (1 / (Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * Math.pow(u, 2));
    }
}
