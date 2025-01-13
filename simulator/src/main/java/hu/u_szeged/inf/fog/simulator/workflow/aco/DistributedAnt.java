package hu.u_szeged.inf.fog.simulator.workflow.aco;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

public class DistributedAnt {
    
    int id;
    
    public DistributedAnt(int id) {
        this.id = id;
    }
    
    public void generateSolution(double[] pheromoneVector, int position, double probability, double evaporationRate) {
        for (int i = 0; i < pheromoneVector.length; i++) {
            if (i != position) {
                double randomNumber = SeedSyncer.centralRnd.nextDouble();

                if (randomNumber < probability) {
                    ComputingAppliance node = ComputingAppliance.allComputingAppliances.get(i);
                    ComputingAppliance center = ComputingAppliance.allComputingAppliances.get(position);

                    double distance = this.calculateHeuristic(node, center);
                    
                    double increment = gaussianKernel(distance, 1500);
                    //System.out.println(distance + " " + 1000 * increment +  " " + node.name + " " + center.name);
                    pheromoneVector[i] += 1000 * increment;

                } else {
                    double number = (evaporationRate * pheromoneVector[i]) / (1 - evaporationRate);
                    //System.out.println(number / 100);
                    pheromoneVector[i] += number / 100;
                }
            } else {
                pheromoneVector[i] = -1;
            }
        }
    }
    
    private double calculateHeuristic(ComputingAppliance node, ComputingAppliance center) {
        return center.geoLocation.calculateDistance(node.geoLocation) / 1000;
    }
    
    public double gaussianKernel(double x, double h) {
        double u = x / h;
        return (1 / (Math.sqrt(2 * Math.PI) * h)) * Math.exp(-0.5 * Math.pow(u, 2));
    }
}
