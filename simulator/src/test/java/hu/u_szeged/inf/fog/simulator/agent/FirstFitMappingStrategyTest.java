package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.ComponentRequirements;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.FirstFitMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstFitMappingStrategyTest {

    @BeforeEach
    void resetGlobalState() {
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
    }

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

    @Test
    void generateLocalOffers_whenAllComponentsFit_returnsSingleOfferWithAllPlacements() {
        ResourceAgent agent = createAgent("Agent1", true);
        Capacity cap = capacity("Node1", "AWS", "eu-west", false, 10.0, 100L, 100L);
        agent.capacities.put("Node1", cap);

        Component c1 = deployableComponent("C1", 3.0, 10L, 10L, null, null, null);
        Component c2 = deployableComponent("C2", 2.0, 10L, 10L, null, null, null);
        AgentApplication app = application(c1, c2);

        List<LocalOffer> offers = new FirstFitMappingStrategy(true).generateLocalOffers(agent, app);

        assertEquals(1, offers.size());
        assertEquals(2, offers.get(0).placements.size());
        assertSame(c1, offers.get(0).placements.get(0).component);
        assertSame(c2, offers.get(0).placements.get(1).component);
        assertSame(cap, offers.get(0).placements.get(0).capacity);
        assertSame(cap, offers.get(0).placements.get(1).capacity);
        assertEquals(1L, app.localCandidateEvaluationCount);
    }

    @Test
    void generateLocalOffers_firstFitConsumesCapacityAndFallsBackToNextCapacity() {
        ResourceAgent agent = createAgent("Agent1", true);
        Capacity cap1 = capacity("Node1", "AWS", "eu-west", false, 5.0, 100L, 100L);
        Capacity cap2 = capacity("Node2", "AWS", "eu-west", false, 5.0, 100L, 100L);
        agent.capacities.put("Node1", cap1);
        agent.capacities.put("Node2", cap2);

        Component c1 = deployableComponent("C1", 5.0, 10L, 10L, null, null, null);
        Component c2 = deployableComponent("C2", 4.0, 10L, 10L, null, null, null);
        AgentApplication app = application(c1, c2);

        List<LocalOffer> offers = new FirstFitMappingStrategy(true).generateLocalOffers(agent, app);

        assertEquals(1, offers.size());
        assertEquals(2, offers.get(0).placements.size());
        assertSame(cap1, offers.get(0).placements.get(0).capacity);
        assertSame(cap2, offers.get(0).placements.get(1).capacity);
    }

    @Test
    void generateLocalOffers_whenOneComponentDoesNotMatchPreference_returnsPartialOffer() {
        ResourceAgent agent = createAgent("Agent1", true);
        Capacity cap = capacity("Node1", "AWS", "eu-west", true, 10.0, 100L, 100L);
        agent.capacities.put("Node1", cap);

        Component mismatching = deployableComponent("C1", 2.0, 10L, 10L, "Azure", null, null);
        Component matching = deployableComponent("C2", 1.0, 10L, 10L, "AWS", null, null);
        AgentApplication app = application(mismatching, matching);

        List<LocalOffer> offers = new FirstFitMappingStrategy(true).generateLocalOffers(agent, app);

        assertEquals(1, offers.size());
        assertEquals(1, offers.get(0).placements.size());
        assertSame(matching, offers.get(0).placements.get(0).component);
        assertSame(cap, offers.get(0).placements.get(0).capacity);
        assertEquals(1L, app.localCandidateEvaluationCount);
    }

    @Test
    void generateLocalOffers_whenNothingFits_returnsEmptyAndDoesNotIncrementCounter() {
        ResourceAgent agent = createAgent("Agent1", true);
        Capacity cap = capacity("Node1", "AWS", "eu-west", false, 1.0, 10L, 10L);
        agent.capacities.put("Node1", cap);

        Component tooLarge = deployableComponent("C1", 2.0, 20L, 20L, null, null, null);
        AgentApplication app = application(tooLarge);

        List<LocalOffer> offers = new FirstFitMappingStrategy(true).generateLocalOffers(agent, app);

        assertTrue(offers.isEmpty());
        assertEquals(0L, app.localCandidateEvaluationCount);
    }

    @Test
    void generateLocalOffers_doesNotMutateRealCapacity() {
        ResourceAgent agent = createAgent("Agent1", true);
        Capacity cap = capacity("Node1", "AWS", "eu-west", false, 10.0, 100L, 100L);
        agent.capacities.put("Node1", cap);

        AgentApplication app = application(
                deployableComponent("C1", 3.0, 10L, 10L, null, null, null),
                deployableComponent("C2", 3.0, 10L, 10L, null, null, null)
        );

        new FirstFitMappingStrategy(true).generateLocalOffers(agent, app);

        assertEquals(10.0, cap.cpu);
        assertEquals(100L, cap.memory);
        assertEquals(100L, cap.storage);
        assertTrue(cap.utilisations.isEmpty());
    }

    private static ResourceAgent createAgent(String name, boolean descending) {
        return new ResourceAgent(name, 1.0, new FirstFitMappingStrategy(descending), new FloodingMessagingStrategy());
    }

    private static Capacity capacity(
            String nodeName,
            String provider,
            String location,
            boolean edge,
            double cpu,
            long memory,
            long storage) {
        return new Capacity(createNode(nodeName, provider, location, edge), cpu, memory, storage);
    }

    private static ComputingAppliance createNode(String name, String provider, String location, boolean edge) {
        return new ComputingAppliance(
                Config.createNode(name, 10.0, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                        1, 1, 1, 1, 1, new HashMap<>()),
                new GeoLocation(0, 0), location, provider, edge);
    }

    private static AgentApplication application(Component... components) {
        AgentApplication app = new AgentApplication();
        app.name = "App-1";
        app.components = List.of(components);
        return app;
    }

    private static Component deployableComponent(
            String id,
            double cpu,
            long memory,
            long storage,
            String provider,
            String location,
            Boolean edge) {
        Component c = new Component();
        c.id = id;

        ComponentRequirements r = new ComponentRequirements();
        r.cpu = cpu;
        r.memory = memory;
        r.storage = storage;
        r.provider = provider;
        r.location = location;
        r.edge = edge;

        c.requirements = r;
        return c;
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
