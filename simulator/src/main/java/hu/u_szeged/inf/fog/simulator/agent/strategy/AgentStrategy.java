package hu.u_szeged.inf.fog.simulator.agent.strategy;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Base class for resource allocation strategies used by resource agents.
 * Defines the interface for determining if an agent can fulfill resource requests.
 */
public abstract class AgentStrategy {
    /**
     * Determines if the given agent can fulfill the requested resources.
     * Implementing classes define the specific allocation strategy.
     *
     * @param agent the resource agent attempting to fulfill the request
     * @param resources the list of resources requested by an application
     * @return a list of pairs mapping the agent to resources it can provide
     */
    public abstract List<Pair<ResourceAgent, Resource>> canFulfill(ResourceAgent agent, List<Resource> resources);

    /**
     * Checks if a resource's preferences match a capacity's characteristics.
     * Compares provider, location, and edge preferences.
     *
     * @param resource the resource with potential preference constraints
     * @param capacity the capacity to check against the resource preferences
     * @return true if all specified preferences match, false otherwise
     */
    public boolean isMatchingPreferences(Resource resource, Capacity capacity) {
        boolean providerMatch = (resource.provider == null || resource.provider.equals(capacity.node.provider));
        boolean locationMatch = (resource.location == null || resource.location.equals(capacity.node.location));
        boolean edgeMatch = (resource.edge == null || resource.edge == capacity.node.edge);

        return providerMatch && locationMatch && edgeMatch;
    }
}
