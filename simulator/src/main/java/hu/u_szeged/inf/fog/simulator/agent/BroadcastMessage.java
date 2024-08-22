package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import java.util.ArrayList;
import java.util.Collections;

public class BroadcastMessage extends StorageObject {
    
    ArrayList<ResourceAgent> alreadyVisitedAgents;
    
    ArrayList<Pair> solution;
    
    /**
     * Application's constraints.
     */
    ArrayList<Constraint> demands;
    
    public static long broadcastTimeOnNetwork = 0;
    
    public static long broadcastByteOnNetwork = 0;
    
    public static int broadcastMessageCount = 0;
    
    public static long totalTimeOnNetwork = 0;
    
    public static long totalByteOnNetwork = 0;
    
    public static int totalMessageCount = 0;

    public BroadcastMessage(ArrayList<Constraint> demands, int broadcastMessageSize) {
        super("bm", broadcastMessageSize, false);
        this.solution = new ArrayList<Pair>();
        this.alreadyVisitedAgents = new ArrayList<ResourceAgent>();
        this.demands = demands;
    }
    
    public BroadcastMessage(BroadcastMessage other, String name) {
        super(name, other.size, false);
        
        this.alreadyVisitedAgents = new ArrayList<>();
        for (ResourceAgent agent : other.alreadyVisitedAgents) {
            this.alreadyVisitedAgents.add(agent);
        }
        
        this.solution = new ArrayList<>();
        for (Pair pair : other.solution) {
            this.solution.add(pair);
        }
        
        this.demands = new ArrayList<>();
        for (Constraint constraint : other.demands) {
            this.demands.add(constraint);
        }
    }

    public void checkFulfillment(ResourceAgent agent) {
        ArrayList<Constraint> fulfilledConstrains = new ArrayList<Constraint>();
        
        for (Constraint demand : demands) {
            for (Constraint agentConstraint : agent.constraints) {
                if (agentConstraint.name.equals(demand.name) && agentConstraint.value >= demand.value) {
                    fulfilledConstrains.add(demand);
                    Pair p = new Pair(agent, demand);
                    this.solution.add(p);
                    break;
                }
            }
        }
        
        for (Constraint constraint : fulfilledConstrains) {
            demands.remove(constraint);
        }
        alreadyVisitedAgents.add(agent); 
    }
    
    public static int calculateTotalBrMessages(int n) {
        if (n == 2) {
            return 1;
        } else {
            return (n - 1) + (n - 1) * calculateTotalBrMessages(n - 1); 
        }
    }
    
    public static ArrayList<Integer> countEdgesPerLevel(int n) {
        ArrayList<Integer> edgeCounts = new ArrayList<>();
        
        int currentEdges = n - 1;
        
        while (currentEdges > 0) {
            if (edgeCounts.isEmpty()) {
                edgeCounts.add(currentEdges);
            } else {
                edgeCounts.add(edgeCounts.get(edgeCounts.size() - 1) * currentEdges);
            }
            currentEdges -= 1;
        }
        Collections.reverse(edgeCounts);
        return edgeCounts;
    } 
    
    public static int calculateAcknowledgementMessages(int n) {
        ArrayList<Integer> edgeCounts = countEdgesPerLevel(n);
        
        int totalMessageCount = 0;
        int previousSum = 2;
        
        for (int i = 0; i < edgeCounts.size(); i++) {
            totalMessageCount += edgeCounts.get(i) * previousSum;
            previousSum = (i + 1) * previousSum + 2;
        }
        return totalMessageCount;
    }
}