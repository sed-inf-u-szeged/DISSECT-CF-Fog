package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.ComponentRequirements;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.FirstFitMappingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FirstFitTest {

    @Test
    void sortingTest_descending() {
        FirstFitMappingStrategy strategy = new FirstFitMappingStrategy(true);

        List<AgentApplication.Component> components = List.of(
                component("st20", null, 20L),
                component("cpu10", 10.0, null),
                component("none1", null, null),
                component("cpu5", 5.0, null),
                component("st50", null, 50L)
        );

        List<Component> sorted = strategy.sortResourcesByCpuElseStorage(components);

        // Descending: CPU sorted by value (highest first), then storage (highest first), then nulls
        assertEquals("cpu10", sorted.get(0).id, "cpu10 should be first (highest CPU in descending)");
        assertEquals("cpu5",  sorted.get(1).id, "cpu5 should be second");
        assertEquals("st50", sorted.get(2).id, "st50 should be third (highest storage in descending)");
        assertEquals("st20", sorted.get(3).id, "st20 should be fourth");
        assertEquals("none1", sorted.get(4).id, "none1 should be last (no requirements)");
    }

    @Test
    void sortingTest_ascending() {
        FirstFitMappingStrategy strategy = new FirstFitMappingStrategy(false);

        List<AgentApplication.Component> components = List.of(
                component("st20", null, 20L),
                component("cpu10", 10.0, null),
                component("none1", null, null),
                component("cpu5", 5.0, null),
                component("st50", null, 50L)
        );

        List<Component> sorted = strategy.sortResourcesByCpuElseStorage(components);

        // Ascending: CPU sorted by value (lowest first), then storage (lowest first), then nulls
        assertEquals("cpu5",  sorted.get(0).id, "cpu5 should be first (lowest CPU in ascending)");
        assertEquals("cpu10", sorted.get(1).id, "cpu10 should be second");
        assertEquals("st20", sorted.get(2).id, "st20 should be third (lowest storage in ascending)");
        assertEquals("st50", sorted.get(3).id, "st50 should be fourth");
        assertEquals("none1", sorted.get(4).id, "none1 should be last (no requirements)");
    }

    @Test
    void sortingTest_cpuPreference() {
        FirstFitMappingStrategy strategy = new FirstFitMappingStrategy(true);

        List<Component> components = List.of(
                component("st", null, 100L),
                component("cpu", 50.0, null),
                component("both", 30.0, 50L)
        );

        List<Component> sorted = strategy.sortResourcesByCpuElseStorage(components);

        // Components with CPU should come before components with only storage
        assertEquals("cpu", sorted.get(0).id, "CPU component should come first");
        assertEquals("both", sorted.get(1).id, "Component with both CPU and storage should come second");
        assertEquals("st", sorted.get(2).id, "Storage-only component should be last");
    }

    private static Component component(String id, Double cpu, Long storage) {
        Component c = new Component();
        c.id = id;

        ComponentRequirements r = new ComponentRequirements();
        r.cpu = cpu;
        r.storage = storage;

        c.requirements = r;
        return c;
    }
}
