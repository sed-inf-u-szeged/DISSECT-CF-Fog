package hu.u_szeged.inf.fog.simulator.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public class Offer {

    @JsonIgnore
    public Map<ResourceAgent, Set<Component>> agentComponentsMap;
    
    List<Pair<ComputingAppliance, Utilisation>> utilisations;
    
    @JsonIgnore
    public int id;
    
    boolean isRemainingDeploymentStarted;

    public Offer(Map<ResourceAgent, Set<Component>> agentResourcesMap, int id) {
        this.id = id;
        this.agentComponentsMap = agentResourcesMap;
        this.utilisations = new ArrayList<>();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(id).append(System.lineSeparator());
        for (Map.Entry<ResourceAgent, Set<Component>> entry : agentComponentsMap.entrySet()) {
            ResourceAgent agent = entry.getKey();
            Set<Component> components = entry.getValue();
            sb.append(agent.name).append(": ");

            for (Component component : components) {
                sb.append(component.id).append(" ");
            }
            sb.append(System.lineSeparator()); 
        }
        return sb.toString();
    }
}