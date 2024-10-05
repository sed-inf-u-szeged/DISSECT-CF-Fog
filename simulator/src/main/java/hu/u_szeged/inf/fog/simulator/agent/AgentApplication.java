package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.node.AgentComputingAppliance;
import java.util.HashMap;
import java.util.HashSet;

public class AgentApplication {
    
    public String name;
    
    public HashMap<String, Object> requirements; 
    
    public int bcastCounter;

    public HashSet<AgentComputingAppliance>  offerList;
    
    public AgentApplication(String name) {
        this.name = name;
        this.requirements = new HashMap<>();
        this.offerList = new HashSet<>();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Agent Application: " + name + "\nRequirements:\n");
        for (HashMap.Entry<String, Object> entry : requirements.entrySet()) {
            sb.append(" - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
}