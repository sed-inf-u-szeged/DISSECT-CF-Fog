package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.List;

import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;

/**
 * Base class for resource allocation strategies used by resource agents.
 * Defines the interface for determining if an agent can fulfill resource requests.
 */
public abstract class MappingStrategy {

    protected static class AvailableCapacity {

        private double cpu;

        private long memory;

        private long storage;

        public AvailableCapacity(Capacity capacity) {
            this.cpu = capacity.cpu;
            this.memory = capacity.memory;
            this.storage = capacity.storage;
        }

        public boolean canHost(Component component) {
            return requiredCpu(component) <= cpu
                    && requiredMemory(component) <= memory
                    && requiredStorage(component) <= storage;
        }

        public void consume(Component component) {
            cpu -= requiredCpu(component);
            memory -= requiredMemory(component);
            storage -= requiredStorage(component);
        }

        public void restore(Component component) {
            cpu += requiredCpu(component);
            memory += requiredMemory(component);
            storage += requiredStorage(component);
        }
    }

    public abstract List<LocalOffer> generateLocalOffers(ResourceAgent agent, AgentApplication application);

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

    public static double requiredCpu(Component component) {
        if (component.requirements == null || component.requirements.cpu == null || component.requirements.cpu <= 0.0) {
            throw new IllegalArgumentException("Component '" + component.id + "' must define a positive CPU requirement.");
        }
        return component.requirements.cpu;
    }

    public static long requiredMemory(Component component) {
        if (component.requirements == null || component.requirements.memory == null || component.requirements.memory <= 0L) {
            throw new IllegalArgumentException("Component '" + component.id + "' must define a positive memory requirement.");
        }
        return component.requirements.memory;
    }

    public static long requiredStorage(Component component) {
        if (component.requirements == null || component.requirements.storage == null || component.requirements.storage <= 0L) {
            throw new IllegalArgumentException("Component '" + component.id + "' must define a positive storage requirement.");
        }
        return component.requirements.storage;
    }
}