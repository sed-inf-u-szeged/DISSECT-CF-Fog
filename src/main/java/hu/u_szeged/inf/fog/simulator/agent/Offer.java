package hu.u_szeged.inf.fog.simulator.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public class Offer {

    @JsonIgnore
    public Map<ResourceAgent, Set<Resource>> agentResourcesMap;
    
    List<Pair<ComputingAppliance, Utilisation>> utilisations;
    
    @JsonIgnore
    public int id;
    
    boolean isRemainingDeploymentStarted;
    


    public Offer(Map<ResourceAgent, Set<Resource>> agentResourcesMap, int id) {
        this.id = id;
        this.agentResourcesMap = agentResourcesMap;
        this.utilisations = new ArrayList<>();
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