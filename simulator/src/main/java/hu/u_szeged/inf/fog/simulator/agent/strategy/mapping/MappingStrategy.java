package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Base class for resource allocation strategies used by resource agents.
 * Defines the interface for determining if an agent can fulfill resource requests.
 */
public abstract class MappingStrategy {

    /**
     * Determines if the given agent can fulfill the requested resources.
     * Implementing classes define the specific allocation strategy.
     *
     * @param agent the resource agent attempting to fulfill the request
     * @param components the list of resources requested by an application
     * @return a list of pairs mapping the agent to resources it can provide
     */
    public abstract List<Pair<ResourceAgent, Component>> canFulfill(ResourceAgent agent, List<Component> components);

    /**
     * Checks if a resource's preferences match a capacity's characteristics.
     * Compares provider, location, and edge preferences.
     *
     * @param component the resource with potential preference constraints
     * @param capacity the capacity to check against the resource preferences
     * @return true if all specified preferences match, false otherwise
     */
    public boolean isMatchingPreferences(Component component, Capacity capacity) {
        boolean providerMatch = (component.requirements.provider == null || component.requirements.provider.equals(capacity.node.provider));
        boolean locationMatch = (component.requirements.location == null || component.requirements.location.equals(capacity.node.location));
        boolean edgeMatch = (component.requirements.edge == null || component.requirements.edge == capacity.node.edge);

        return providerMatch && locationMatch && edgeMatch;
    }
}