package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import java.util.Map;
import java.util.Set;

public class Offer {

    Map<ResourceAgent, Set<Resource>> agentResourcesMap;
    
    int id;

    public Offer(Map<ResourceAgent, Set<Resource>> agentResourcesMap, int id) {
        this.id = id;
        this.agentResourcesMap = agentResourcesMap;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(id).append(System.lineSeparator());
        for (Map.Entry<ResourceAgent, Set<Resource>> entry : agentResourcesMap.entrySet()) {
            ResourceAgent agent = entry.getKey();
            Set<Resource> resources = entry.getValue();
            sb.append(agent.name).append(": ");

            for (Resource resource : resources) {
                sb.append(resource.name).append(" ");
            }
            sb.append(System.lineSeparator()); 
        }
        return sb.toString();
    }
}