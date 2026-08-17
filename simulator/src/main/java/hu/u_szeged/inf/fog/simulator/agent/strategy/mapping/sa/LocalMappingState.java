package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.sa;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocalMappingState {

    public final List<Component> applicationComponents;

    public final Map<Component, Capacity> assignments;

    public LocalMappingState(List<Component> applicationComponents, Map<Component, Capacity> assignments) {
        this.applicationComponents = List.copyOf(applicationComponents);

        Map<Component, Capacity> copiedAssignments = new LinkedHashMap<>();

        for (Map.Entry<Component, Capacity> entry : assignments.entrySet()) {
            Component component = entry.getKey();
            Capacity capacity = entry.getValue();

            if (!this.applicationComponents.contains(component)) {
                throw new IllegalArgumentException("The mapping contains a component from another application.");
            }

            if (capacity == null) {
                throw new IllegalArgumentException("Assigned capacity cannot be null.");
            }

            copiedAssignments.put(component, capacity);
        }

        this.assignments = Collections.unmodifiableMap(copiedAssignments);
    }

    public int getMissingComponentCount() {
        return applicationComponents.size() - assignments.size();
    }

    public boolean isEmpty() {
        return assignments.isEmpty();
    }

    public List<ComponentPlacement> toPlacements() {
        List<ComponentPlacement> placements = new ArrayList<>();

        for (Map.Entry<Component, Capacity> entry : assignments.entrySet()) {

            placements.add(new ComponentPlacement(entry.getKey(), entry.getValue()));
        }

        return List.copyOf(placements);
    }
}
