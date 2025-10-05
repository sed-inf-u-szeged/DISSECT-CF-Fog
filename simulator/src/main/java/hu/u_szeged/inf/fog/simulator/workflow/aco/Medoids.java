package hu.u_szeged.inf.fog.simulator.workflow.aco;

import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;

public class Medoids {

    public static HashMap<Integer, ArrayList<WorkflowComputingAppliance>> runOptimiser(ArrayList<Integer> medoids,
            ArrayList<WorkflowComputingAppliance> nodesToBeClustered) {
        
        ArrayList<Integer> medoidsCopy = new ArrayList<>(medoids);
        HashMap<Integer, ArrayList<WorkflowComputingAppliance>> clusters =
            assignNodes(medoidsCopy, nodesToBeClustered);
        ArrayList<Integer> newMedoids = findNewMedoids(clusters, nodesToBeClustered);

        int iterationCounter = 1;

        while (true) {
            ArrayList<Integer> list1Copy = new ArrayList<>(medoidsCopy);
            ArrayList<Integer> list2Copy = new ArrayList<>(newMedoids);
            Collections.sort(list1Copy);
            Collections.sort(list2Copy);

            if (list1Copy.equals(list2Copy)) {
                break;
            }

            medoidsCopy = new ArrayList<>(newMedoids);
            clusters = assignNodes(medoidsCopy, nodesToBeClustered); 
            newMedoids = findNewMedoids(clusters, nodesToBeClustered);
            iterationCounter++;
        }

        System.out.println("Iterations: " + iterationCounter);
        System.out.println("Medoids: "  + newMedoids);
        CentralisedAntOptimiser.printClusterAssignments(clusters);
        //System.exit(0);
        return clusters;
    }
    
    private static HashMap<Integer, ArrayList<WorkflowComputingAppliance>> assignNodes(ArrayList<Integer> medoids,
            ArrayList<WorkflowComputingAppliance> nodesToBeClustered) {
        
        HashMap<Integer, ArrayList<WorkflowComputingAppliance>> clusters = new HashMap<>();
        
        for (int medoid : medoids) {
            clusters.put(medoid, new ArrayList<>());
        }
        
        for (WorkflowComputingAppliance node : nodesToBeClustered) {
            int bestMedoid = -1;
            double bestLatency = Double.MAX_VALUE;
            
            for (int medoid : medoids) {
                double latency = CentralisedAntOptimiser.calculateHeuristic(nodesToBeClustered.get(medoid), node);
                if (latency < bestLatency) {
                    bestLatency = latency;
                    bestMedoid = medoid;
                }
            }
            clusters.get(bestMedoid).add(node);
        }
        
        return clusters;
    }
    
    private static ArrayList<Integer> findNewMedoids(HashMap<Integer, ArrayList<WorkflowComputingAppliance>> clusters, 
            ArrayList<WorkflowComputingAppliance> nodesToBeClustered) {
        
        ArrayList<Integer> newMedoids = new ArrayList<>();
        
        for (Entry<Integer, ArrayList<WorkflowComputingAppliance>> entry : clusters.entrySet()) {
            ArrayList<WorkflowComputingAppliance> clusterNodes = entry.getValue();
            int bestMedoid = entry.getKey();
            double minTotalLatency = Double.MAX_VALUE;

            for (WorkflowComputingAppliance candidate : clusterNodes) {
                double totalLatency = 0;
                for (WorkflowComputingAppliance other : clusterNodes) {
                    totalLatency += CentralisedAntOptimiser.calculateHeuristic(candidate, other); 
                }
                if (totalLatency < minTotalLatency) {
                    minTotalLatency = totalLatency;
                    bestMedoid = nodesToBeClustered.indexOf(candidate);
                }
            }
            newMedoids.add(bestMedoid);
        }
        
        return newMedoids;
    }
}