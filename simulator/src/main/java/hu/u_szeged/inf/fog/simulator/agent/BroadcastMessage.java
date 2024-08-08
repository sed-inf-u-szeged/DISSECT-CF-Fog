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
    
    int broadcastMessageSize;

    public BroadcastMessage(ArrayList<Constraint> demands, int broadcastMessageSize) {
        super("bm", broadcastMessageSize, false);
        this.solution = new ArrayList<Pair>();
        this.alreadyVisitedAgents = new ArrayList<ResourceAgent>();
        this.demands = demands;
        this.broadcastMessageSize = broadcastMessageSize;
    }
    
    public BroadcastMessage(BroadcastMessage other, String name) {
        super(name, other.broadcastMessageSize, false);

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
}