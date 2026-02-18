package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class FirstFitMappingStrategy extends MappingStrategy {
    
    private final boolean descending;

    public FirstFitMappingStrategy(boolean descending) {
        this.descending = descending;
    }

    public List<Pair<ResourceAgent, Component>> canFulfill(ResourceAgent agent, List<Component> components) {

        List<Component> sortedComponent = sortResourcesByCpuElseStorage(components);
        List<Pair<ResourceAgent, Component>> agentResourcePairs = new ArrayList<>();

        for (Component component : sortedComponent) {
            double requiredCpu = 
                    (component.requirements.cpu != null && component.requirements.cpu > 0) ? component.requirements.cpu : 0;
            long requiredMemory = 
                    (component.requirements.memory != null && component.requirements.memory > 0) ? component.requirements.memory : 0;
            long requiredStorage = 
                    (component.requirements.storage != null && component.requirements.storage > 0) ? component.requirements.storage : 0;

            for (Capacity capacity : agent.capacities.values()) {
                if (isMatchingPreferences(component, capacity) && requiredCpu <= capacity.cpu
                            && requiredMemory <= capacity.memory && requiredStorage <= capacity.storage) {

                    capacity.reserveCapacity(component);
                    agentResourcePairs.add(Pair.of(agent, component));
                }
            }
        }

        return agentResourcePairs;
    }

    public List<Component> sortResourcesByCpuElseStorage(List<Component> originalComponents) {
        List<Component> sorted = new ArrayList<>(originalComponents);

        sorted.sort((c1, c2) -> {
            Double cpu1 = c1.requirements.cpu;
            Double cpu2 = c2.requirements.cpu;

            if (cpu1 != null && cpu2 != null) {
                return descending
                        ? Double.compare(cpu2, cpu1)
                        : Double.compare(cpu1, cpu2);
            }

            if (cpu1 != null) {
                return -1; 
            }
            if (cpu2 != null) {
                return 1;  
            }

            Long st1 = c1.requirements.storage;
            Long st2 = c2.requirements.storage;

            if (st1 != null && st2 != null) {
                return descending
                        ? Double.compare(st2, st1)
                        : Double.compare(st1, st2);
            }

            if (st1 != null) {
                return -1; 
            }
            if (st2 != null) {
                return 1;  
            }

            return 0;
        });

        return sorted;
    }
}