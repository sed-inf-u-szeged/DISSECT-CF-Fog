package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.ComponentRequirements;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.FirstFitMappingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FirstFitTest {

    @Test
    void sortingTest() {
        FirstFitMappingStrategy strategy1 = new FirstFitMappingStrategy(true);

        List<AgentApplication.Component> components = List.of(
                component("st20", null, 20L),
                component("cpu10", 10.0, null),
                component("none1", null, null),
                component("cpu5", 5.0, null),
                component("st50", null, 50L)
        );

        List<Component> sorted = strategy1.sortResourcesByCpuElseStorage(components);

        assertEquals("cpu10", sorted.get(0).id);
        assertEquals("cpu5",  sorted.get(1).id);
        assertEquals("st50", sorted.get(2).id);
        assertEquals("st20", sorted.get(3).id);
        assertEquals("none1", sorted.get(4).id);

        FirstFitMappingStrategy strategy2 = new FirstFitMappingStrategy(false);

        sorted = strategy2.sortResourcesByCpuElseStorage(components);

        assertEquals("cpu5",  sorted.get(0).id);
        assertEquals("cpu10", sorted.get(1).id);
        assertEquals("st20", sorted.get(2).id);
        assertEquals("st50", sorted.get(3).id);
        assertEquals("none1", sorted.get(4).id);
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
