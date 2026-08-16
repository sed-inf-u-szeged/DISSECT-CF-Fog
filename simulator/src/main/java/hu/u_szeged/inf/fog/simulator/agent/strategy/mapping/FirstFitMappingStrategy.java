package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;

public class FirstFitMappingStrategy extends MappingStrategy {
    
    private final boolean descending;

    public FirstFitMappingStrategy(boolean descending) {
        this.descending = descending;
    }

    @Override
    public List<LocalOffer> generateLocalOffers(ResourceAgent agent, AgentApplication application) {
        List<Component> sortedComponents = sortResourcesByCpuElseStorage(application.components);
        List<ComponentPlacement> placements = new ArrayList<>();

        Map<Capacity, AvailableCapacity> availableCapacities = new LinkedHashMap<>();

        for (Capacity capacity : agent.capacities.values()) {
            availableCapacities.put(capacity, new AvailableCapacity(capacity));
        }

        for (Component component : sortedComponents) {
            for (Capacity capacity : agent.capacities.values()) {
                AvailableCapacity availableCapacity = availableCapacities.get(capacity);

                if (isMatchingPreferences(component, capacity) && availableCapacity.canHost(component)) {
                    availableCapacity.consume(component);
                    placements.add(new ComponentPlacement(component, capacity));

                    break;
                }
            }
        }

        if (placements.isEmpty()) {
            return List.of();
        }

        return List.of(new LocalOffer(agent, placements,null));
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