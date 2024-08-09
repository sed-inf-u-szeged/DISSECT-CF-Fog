package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import java.util.ArrayList;

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
    
    public static int calculateTotalMessages(int n) {
        if (n == 2) {
            return 1;
        } else {
            return (n - 1) + (n - 1) * calculateTotalMessages(n - 1); 
        }
    }
 
}