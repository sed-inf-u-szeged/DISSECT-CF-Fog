package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.ComponentRequirements;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.RandomFirstFitMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomFirstFitMappingStrategyTest {

    private int originalSeed;

    @BeforeEach
    void setup() {
        originalSeed = SeedSyncer.getSeed();
        SeedSyncer.setSeed(12345);
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
    }

    @AfterEach
    void restore() {
        SeedSyncer.setSeed(originalSeed);
    }

    @Test
    void generateLocalOffers_isReproducibleWithSameSeed() {
        ResourceAgent agent = agent("Agent1");
        Capacity cap = capacity("Node1", "AWS", "eu-west", false, 10.0, 100L, 100L);
        agent.capacities.put("Node1", cap);
        AgentApplication app = application(component("C1", 2.0, 10L, 10L, null, null, null),
                component("C2", 3.0, 10L, 10L, null, null, null));

        SeedSyncer.setSeed(77);
        List<LocalOffer> first = new RandomFirstFitMappingStrategy().generateLocalOffers(agent, app);

        SeedSyncer.setSeed(77);
        app.localCandidateEvaluationCount = 0;
        List<LocalOffer> second = new RandomFirstFitMappingStrategy().generateLocalOffers(agent, app);

        assertEquals("[C1->Node1, C2->Node1]", signature(first));
        assertEquals(signature(first), signature(second));
    }

    @Test
    void generateLocalOffers_shufflesComponentsAndCapacitiesAndConsumesVirtualCapacity() {
        ResourceAgent agent = agent("Agent1");
        Capacity cap1 = capacity("Node1", "AWS", "eu-west", false, 5.0, 50L, 50L);
        Capacity cap2 = capacity("Node2", "Azure", "eu-west", false, 5.0, 50L, 50L);
        agent.capacities.put("Node1", cap1);
        agent.capacities.put("Node2", cap2);

        AgentApplication app = application(
                component("C1", 4.0, 10L, 10L, null, null, null),
                component("C2", 4.0, 10L, 10L, null, null, null));

        SeedSyncer.setSeed(88);
        List<LocalOffer> offers = new RandomFirstFitMappingStrategy().generateLocalOffers(agent, app);

        assertEquals(1, offers.size());
        assertEquals("[C1->Node1, C2->Node2]", signature(offers));
        assertEquals(1L, app.localCandidateEvaluationCount);
        assertEquals(5.0, cap1.cpu, 1e-9);
        assertEquals(5.0, cap2.cpu, 1e-9);
        assertEquals(50L, cap1.memory);
        assertEquals(50L, cap2.memory);
        assertEquals(50L, cap1.storage);
        assertEquals(50L, cap2.storage);
    }

    @Test
    void generateLocalOffers_meaningfullyChangesPlacementWithDifferentSeed() {
        ResourceAgent agent = agent("Agent1");
        Capacity cap1 = capacity("Node1", "AWS", "eu-west", false, 5.0, 50L, 50L);
        Capacity cap2 = capacity("Node2", "Azure", "eu-west", false, 5.0, 50L, 50L);
        agent.capacities.put("Node1", cap1);
        agent.capacities.put("Node2", cap2);

        AgentApplication app = application(
                component("C1", 4.0, 10L, 10L, null, null, null),
                component("C2", 4.0, 10L, 10L, null, null, null));

        java.util.Set<String> signatures = new java.util.HashSet<>();

        for (int seed = 1; seed <= 100; seed++) {
            app.localCandidateEvaluationCount = 0;
            SeedSyncer.setSeed(seed);
            signatures.add(signature(new RandomFirstFitMappingStrategy().generateLocalOffers(agent, app)));
        }

        assertTrue(signatures.size() >= 2, "Different seeds should exercise different first-fit placement orders");
    }

    @Test
    void generateLocalOffers_returnsPartialOfferAndDoesNotRequireCompleteCoverage() {
        ResourceAgent agent = agent("Agent1");
        Capacity cap = capacity("Node1", "AWS", "eu-west", false, 5.0, 100L, 100L);
        agent.capacities.put("Node1", cap);

        AgentApplication app = application(
                component("C1", 3.0, 10L, 10L, "AWS", null, null),
                component("C2", 3.0, 10L, 10L, "Azure", null, null));

        List<LocalOffer> offers = new RandomFirstFitMappingStrategy().generateLocalOffers(agent, app);

        assertEquals(1, offers.size());
        assertEquals(1, offers.get(0).placements.size());
        assertEquals(1L, app.localCandidateEvaluationCount);
    }

    @Test
    void generateLocalOffers_whenNothingMatchesOrFitsReturnsEmptyAndKeepsCapacity() {
        ResourceAgent agent = agent("Agent1");
        Capacity cap = capacity("Node1", "AWS", "eu-west", false, 1.0, 10L, 10L);
        agent.capacities.put("Node1", cap);

        AgentApplication app = application(component("C1", 2.0, 20L, 20L, "Azure", null, null));

        List<LocalOffer> offers = new RandomFirstFitMappingStrategy().generateLocalOffers(agent, app);

        assertTrue(offers.isEmpty());
        assertEquals(0L, app.localCandidateEvaluationCount);
        assertEquals(1.0, cap.cpu);
        assertEquals(10L, cap.memory);
        assertEquals(10L, cap.storage);
        assertTrue(cap.utilisations.isEmpty());
    }

    private static ResourceAgent agent(String name) {
        return new ResourceAgent(name, 1.0, new RandomFirstFitMappingStrategy(), new FloodingMessagingStrategy());
    }

    private static Capacity capacity(String nodeName, String provider, String location, boolean edge,
                                     double cpu, long memory, long storage) {
        ComputingAppliance node = new ComputingAppliance(
                Config.createNode(nodeName, 10.0, 10 * ScenarioBase.GB_IN_BYTE,
                        10 * ScenarioBase.GB_IN_BYTE, 1, 1, 1, 1, 1, new HashMap<>()),
                new GeoLocation(0, 0), location, provider, edge);
        return new Capacity(node, cpu, memory, storage, 1.0);
    }

    private static AgentApplication application(Component... components) {
        AgentApplication app = new AgentApplication();
        app.name = "Random-App";
        app.components = List.of(components);
        return app;
    }

    private static Component component(String id, double cpu, long memory, long storage,
                                       String provider, String location, Boolean edge) {
        Component component = new Component();
        component.id = id;
        ComponentRequirements requirements = new ComponentRequirements();
        requirements.cpu = cpu;
        requirements.memory = memory;
        requirements.storage = storage;
        requirements.provider = provider;
        requirements.location = location;
        requirements.edge = edge;
        component.requirements = requirements;
        return component;
    }

    private static String signature(List<LocalOffer> offers) {
        if (offers.isEmpty()) {
            return "";
        }
        return offers.get(0).placements.stream()
                .map(placement -> placement.component.id + "->" + placement.capacity.node.name)
                .sorted()
                .toList()
                .toString();
    }
}
