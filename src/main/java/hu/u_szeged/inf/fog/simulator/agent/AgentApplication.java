package hu.u_szeged.inf.fog.simulator.agent;

import java.util.HashMap;

public class AgentApplication {
    
    public String name;
    
    public HashMap<String, Object> requirements; 

    public AgentApplication(String name) {
        this.name = name;
        this.requirements = new HashMap<>();
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