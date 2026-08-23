package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.ComponentRequirements;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.sa.SimulatedAnnealingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedAnnealingStrategyTest {

    @BeforeEach
    void resetGlobalState() {
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
        AgentApplication.allAgentApplications.clear();
        SeedSyncer.setSeed(12345);
    }

    @Test
    void generateLocalOffers_noFeasiblePlacement_returnsEmpty() {
        ResourceAgent agent = createAgent("Agent1");
        agent.capacities.put("Node1", capacity("Node1", "AWS", "eu-west", false, 1.0, 1L, 1L));

        AgentApplication app = application(
                component("C1", 2.0, 2L, 2L, null, null, null));

        List<LocalOffer> offers = new SimulatedAnnealingStrategy().generateLocalOffers(agent, app);

        assertTrue(offers.isEmpty());
        assertTrue(app.localCandidateEvaluationCount > 0L);
    }

    @Test
    void generateLocalOffers_feasibleScenario_returnsSingleOfferAndDoesNotMutateRealCapacity() {
        ResourceAgent agent = createAgent("Agent1");
        Capacity cap1 = capacity("Node1", "AWS", "eu-west", false, 10.0, 100L, 100L);
        Capacity cap2 = capacity("Node2", "AWS", "eu-west", false, 10.0, 100L, 100L);
        agent.capacities.put("Node1", cap1);
        agent.capacities.put("Node2", cap2);

        Component c1 = component("C1", 3.0, 10L, 10L, null, null, null);
        Component c2 = component("C2", 3.0, 10L, 10L, null, null, null);
        AgentApplication app = application(c1, c2);

        List<LocalOffer> offers = new SimulatedAnnealingStrategy().generateLocalOffers(agent, app);

        assertEquals(1, offers.size());
        assertFalse(offers.get(0).placements.isEmpty());
        assertSame(agent, offers.get(0).agent);
        assertTrue(app.localCandidateEvaluationCount > 0L);
        assertEquals(10.0, cap1.cpu);
        assertEquals(100L, cap1.memory);
        assertEquals(100L, cap1.storage);
        assertEquals(10.0, cap2.cpu);
        assertEquals(100L, cap2.memory);
        assertEquals(100L, cap2.storage);
    }

    @Test
    void generateLocalOffers_respectsProviderPreference() {
        ResourceAgent agent = createAgent("Agent1");
        Capacity aws = capacity("AwsNode", "AWS", "eu-west", false, 10.0, 100L, 100L);
        Capacity azure = capacity("AzureNode", "Azure", "eu-west", false, 10.0, 100L, 100L);
        agent.capacities.put("AwsNode", aws);
        agent.capacities.put("AzureNode", azure);

        Component onlyAws = component("C1", 2.0, 10L, 10L, "AWS", null, null);
        AgentApplication app = application(onlyAws);

        List<LocalOffer> offers = new SimulatedAnnealingStrategy().generateLocalOffers(agent, app);

        assertEquals(1, offers.size());
        assertEquals(1, offers.get(0).placements.size());
        assertSame(aws, offers.get(0).placements.get(0).capacity);
    }

    @Test
    void generateLocalOffers_sameSeedAndInput_producesSamePlacementSignature() {
        ResourceAgent agent1 = createAgent("Agent1");
        agent1.capacities.put("Node1", capacity("Node1", "AWS", "eu-west", false, 10.0, 100L, 100L));
        agent1.capacities.put("Node2", capacity("Node2", "AWS", "eu-west", false, 10.0, 100L, 100L));
        AgentApplication app1 = application(
                component("C1", 2.0, 10L, 10L, null, null, null),
                component("C2", 2.0, 10L, 10L, null, null, null),
                component("C3", 2.0, 10L, 10L, null, null, null));

        List<LocalOffer> offers1 = new SimulatedAnnealingStrategy().generateLocalOffers(agent1, app1);
        String signature1 = placementSignature(offers1);

        resetGlobalState();

        ResourceAgent agent2 = createAgent("Agent1");
        agent2.capacities.put("Node1", capacity("Node1", "AWS", "eu-west", false, 10.0, 100L, 100L));
        agent2.capacities.put("Node2", capacity("Node2", "AWS", "eu-west", false, 10.0, 100L, 100L));
        AgentApplication app2 = application(
                component("C1", 2.0, 10L, 10L, null, null, null),
                component("C2", 2.0, 10L, 10L, null, null, null),
                component("C3", 2.0, 10L, 10L, null, null, null));

        List<LocalOffer> offers2 = new SimulatedAnnealingStrategy().generateLocalOffers(agent2, app2);
        String signature2 = placementSignature(offers2);

        assertEquals(signature1, signature2);
    }

    @Test
    void generateLocalOffers_positiveQosWeightWithoutReference_throwsException() {
        ResourceAgent agent = createAgent("Agent1");
        agent.capacities.put("Node1", capacity("Node1", "AWS", "eu-west", false, 10.0, 100L, 100L));

        AgentApplication app = application(component("C1", 2.0, 10L, 10L, null, null, null));
        app.price = 1.0;
        app.maxCost = null;

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SimulatedAnnealingStrategy().generateLocalOffers(agent, app));

        assertTrue(exception.getMessage().contains("maxCost must be positive"));
    }

    private static String placementSignature(List<LocalOffer> offers) {
        if (offers.isEmpty()) {
            return "EMPTY";
        }
        return offers.get(0).placements.stream()
                .map(p -> p.component.id + "->" + p.capacity.node.name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static ResourceAgent createAgent(String name) {
        return new ResourceAgent(name, 1.0, new SimulatedAnnealingStrategy(), new FloodingMessagingStrategy());
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

    private static Component component(
            String id,
            double cpu,
            long memory,
            long storage,
            String provider,
            String location,
            Boolean edge) {
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
}
