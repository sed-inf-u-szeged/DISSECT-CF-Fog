package hu.u_szeged.inf.fog.simulator.aco;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.StandardResourceAgent;

public class DecentralisedAntApplication {

    int id;

    AgentApplication app;

    public DecentralisedAntApplication(int id) {
        this.id = id;
    }
    
    public void generateSolution(double[] pheromoneVector, int position, double probability, double evaporationRate, AgentApplication app) {
        this.app = app;

        for (int i = 0; i < pheromoneVector.length; i++) {
            if (i != position) {
                double randomNumber = SeedSyncer.centralRnd.nextDouble();

                if (randomNumber < probability) {
                    StandardResourceAgent node = StandardResourceAgent.standardResourceAgents.get(i);
                    StandardResourceAgent center = StandardResourceAgent.standardResourceAgents.get(position);

                    double score = this.calculateHeuristic(node, center);

                    double increment = gaussianKernel(score, 1500);
                    //System.out.println(distance + " " + 1000 * increment +  " " + node.name + " " + center.name);
                    //System.out.println(distance + " " +  " " + node.name + " " + center.name);
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
    
    private double calculateHeuristic(StandardResourceAgent node1, StandardResourceAgent node2) {
        return ClusterSorter.getScore(node1, node2, app);
    }

    public double gaussianKernel(double x, double h) {
        double u = x / h;
        return (1 / (Math.sqrt(2 * Math.PI) * h)) * Math.exp(-0.5 * Math.pow(u, 2));
    }
}
