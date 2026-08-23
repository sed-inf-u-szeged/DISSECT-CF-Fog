package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.ComponentRequirements;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.pareto.ExhaustiveMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ExhaustiveMappingStrategyTest {

    @BeforeEach
    void resetGlobalState() {
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
        AgentApplication.allAgentApplications.clear();
    }

    @Test
    void emptyComponents_returnsEmptyListAndKeepsCountersAtZero() {
        ResourceAgent agent = createAgent("Agent1");
        agent.capacities.put("cap1", capacity("Node1", 10.0, 10L, 10L));
        AgentApplication app = application();

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertTrue(offers.isEmpty());
        assertEquals(0L, app.localCandidateEvaluationCount);
        assertEquals(0L, app.localOffersBeforePareto);
        assertEquals(0L, app.localOffersAfterPareto);
    }

    @Test
    void singleComponent_singleCapacity_fits_returnsOneOffer() {
        ResourceAgent agent = createAgent("Agent1");
        Capacity cap = capacity("Node1", 10.0, 10L, 10L);
        agent.capacities.put("cap1", cap);

        Component c = component("C1", 5.0, 1L, 1L);
        AgentApplication app = application(c);

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertEquals(1, offers.size());
        assertEquals(1, offers.get(0).placements.size());
        assertSame(c, offers.get(0).placements.get(0).component);
        assertSame(cap, offers.get(0).placements.get(0).capacity);
        assertEquals(1L, app.localCandidateEvaluationCount);
        assertEquals(1L, app.localOffersBeforePareto);
        assertEquals(1L, app.localOffersAfterPareto);
    }

    @Test
    void singleComponent_singleCapacity_doesNotFit_returnsEmptyList() {
        ResourceAgent agent = createAgent("Agent1");
        agent.capacities.put("cap1", capacity("Node1", 3.0, 10L, 10L));

        Component c = component("C1", 5.0, 1L, 1L);
        AgentApplication app = application(c);

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertTrue(offers.isEmpty());
        assertEquals(0L, app.localCandidateEvaluationCount);
        assertEquals(0L, app.localOffersBeforePareto);
        assertEquals(0L, app.localOffersAfterPareto);
    }

    @Test
    void twoComponents_singleCapacity_bothFit_returnsThreeOffers() {
        ResourceAgent agent = createAgent("Agent1");
        agent.capacities.put("cap1", capacity("Node1", 10.0, 10L, 10L));

        Component c1 = component("C1", 3.0, 1L, 1L);
        Component c2 = component("C2", 3.0, 1L, 1L);
        AgentApplication app = application(c1, c2);

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertEquals(3, offers.size());
        assertEquals(3L, app.localCandidateEvaluationCount);
        assertEquals(3L, app.localOffersBeforePareto);
        assertEquals(3L, app.localOffersAfterPareto);
    }

    @Test
    void twoComponents_singleCapacity_cannotHostBothSimultaneously_returnsTwoOffers() {
        ResourceAgent agent = createAgent("Agent1");
        agent.capacities.put("cap1", capacity("Node1", 10.0, 10L, 10L));

        Component c1 = component("C1", 8.0, 1L, 1L);
        Component c2 = component("C2", 8.0, 1L, 1L);
        AgentApplication app = application(c1, c2);

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertEquals(2, offers.size());
        assertEquals(2L, app.localCandidateEvaluationCount);
        assertEquals(2L, app.localOffersBeforePareto);
        assertEquals(2L, app.localOffersAfterPareto);
    }

    @Test
    void twoComponents_twoCapacities_allFit_tracksCountersAndReturnsExpectedParetoOffers() {
        ResourceAgent agent = createAgent("Agent1");
        Capacity cap1 = capacity("Node1", 10.0, 10L, 10L);
        Capacity cap2 = capacity("Node2", 10.0, 10L, 10L);
        agent.capacities.put("cap1", cap1);
        agent.capacities.put("cap2", cap2);

        Component c1 = component("C1", 1.0, 1L, 1L);
        Component c2 = component("C2", 1.0, 1L, 1L);
        AgentApplication app = application(c1, c2);

        Map<Capacity, String> capLabels = Map.of(cap1, "cap1", cap2, "cap2");

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertEquals(6, offers.size());
        assertEquals(8L, app.localCandidateEvaluationCount);
        assertEquals(8L, app.localOffersBeforePareto);
        assertEquals(6L, app.localOffersAfterPareto);

        Set<String> signatures = offers.stream()
                .map(offer -> offer.placements.stream()
                        .sorted(Comparator.comparing(p -> p.component.id))
                        .map(p -> p.component.id + "->" + capLabels.get(p.capacity))
                        .collect(Collectors.joining(",")))
                .collect(Collectors.toSet());
        assertEquals(6, signatures.size(), "Duplicate offers detected");
        assertEquals(6, signatures.size(), "Duplicate offers detected");

        Set<String> expected = Set.of(
                "C2->cap1",
                "C2->cap2",
                "C1->cap1",
                "C1->cap1,C2->cap1",
                "C1->cap2",
                "C1->cap2,C2->cap2"
        );
        assertEquals(expected, signatures);
    }

    @Test
    void allOffersReferenceCorrectAgent() {
        ResourceAgent agent = createAgent("Agent1");
        agent.capacities.put("cap1", capacity("Node1", 10.0, 10L, 10L));

        Component c1 = component("C1", 3.0, 1L, 1L);
        Component c2 = component("C2", 3.0, 1L, 1L);
        AgentApplication app = application(c1, c2);

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertTrue(offers.stream().allMatch(offer -> offer.agent == agent));
    }

    @Test
    void capacityIsNotMutatedByStrategy() {
        ResourceAgent agent = createAgent("Agent1");
        Capacity cap = capacity("Node1", 10.0, 100L, 200L);
        agent.capacities.put("cap1", cap);

        Component c1 = component("C1", 4.0, 30L, 50L);
        Component c2 = component("C2", 4.0, 30L, 50L);
        AgentApplication app = application(c1, c2);

        new ExhaustiveMappingStrategy().generateLocalOffers(agent, app);

        assertEquals(10.0, cap.cpu, "CPU must not be modified");
        assertEquals(100L, cap.memory, "Memory must not be modified");
        assertEquals(200L, cap.storage, "Storage must not be modified");
        assertTrue(cap.utilisations.isEmpty(), "The strategy must not create reservations");
    }

    @Test
    void memoryConstraint_preventsCoPlacement() {
        ResourceAgent agent = createAgent("Agent1");
        agent.capacities.put("cap1", capacity("Node1", 100.0, 10L, 100L));

        Component c1 = component("C1", 1.0, 6L, 1L);
        Component c2 = component("C2", 1.0, 6L, 1L);
        AgentApplication app = application(c1, c2);

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertEquals(2, offers.size());
        assertTrue(offers.stream().allMatch(o -> o.placements.size() == 1),
                "No offer should contain both components on the same capacity");
    }

    @Test
    void storageConstraint_preventsCoPlacement() {
        ResourceAgent agent = createAgent("Agent1");
        agent.capacities.put("cap1", capacity("Node1", 100.0, 100L, 10L));

        Component c1 = component("C1", 1.0, 1L, 8L);
        Component c2 = component("C2", 1.0, 1L, 8L);
        AgentApplication app = application(c1, c2);

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertEquals(2, offers.size());
        assertTrue(offers.stream().allMatch(o -> o.placements.size() == 1),
                "No offer should contain both components given storage overflow");
    }

    @Test
    void preferenceFiltering_matchingProvider_returnsOffer() {
        ResourceAgent agent = createAgent("Agent1");
        ComputingAppliance node = createNode("Node1", "AWS", "us-east", false);
        agent.capacities.put("Node1", new Capacity(node, 10.0, 10L, 10L));

        Component c = component("C1", 1.0, 1L, 1L);
        c.requirements.provider = "AWS";
        AgentApplication app = application(c);

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertEquals(1, offers.size());
    }

    @Test
    void preferenceFiltering_nonMatchingProvider_returnsEmptyList() {
        ResourceAgent agent = createAgent("Agent1");
        ComputingAppliance node = createNode("Node1", "Azure", "eu-west", false);
        agent.capacities.put("Node1", new Capacity(node, 10.0, 10L, 10L));

        Component c = component("C1", 1.0, 1L, 1L);
        c.requirements.provider = "AWS";
        AgentApplication app = application(c);

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertTrue(offers.isEmpty());
    }

    @Test
    void preferenceFiltering_twoCapacities_onlyMatchingProviderUsed() {
        ResourceAgent agent = createAgent("Agent1");
        ComputingAppliance awsNode = createNode("AwsNode", "AWS", "us-east", false);
        ComputingAppliance azureNode = createNode("AzureNode", "Azure", "eu-west", false);
        Capacity awsCap = new Capacity(awsNode, 10.0, 10L, 10L);
        Capacity azureCap = new Capacity(azureNode, 10.0, 10L, 10L);
        agent.capacities.put("AwsNode", awsCap);
        agent.capacities.put("AzureNode", azureCap);

        Component c = component("C1", 1.0, 1L, 1L);
        c.requirements.provider = "AWS";
        AgentApplication app = application(c);

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertEquals(1, offers.size());
        assertSame(awsCap, offers.get(0).placements.get(0).capacity);
    }

    @Test
    void preferenceFiltering_edgeConstraint_onlyEdgeCapacityUsed() {
        ResourceAgent agent = createAgent("Agent1");
        ComputingAppliance edgeNode = createNode("EdgeNode", "X", "X", true);
        ComputingAppliance cloudNode = createNode("CloudNode", "X", "X", false);
        Capacity edgeCap = new Capacity(edgeNode, 10.0, 10L, 10L);
        Capacity cloudCap = new Capacity(cloudNode, 10.0, 10L, 10L);
        agent.capacities.put("EdgeNode", edgeCap);
        agent.capacities.put("CloudNode", cloudCap);

        Component c = component("C1", 1.0, 1L, 1L);
        c.requirements.edge = true;
        AgentApplication app = application(c);

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertEquals(1, offers.size());
        assertSame(edgeCap, offers.get(0).placements.get(0).capacity);
    }

    @Test
    void preferenceFiltering_locationConstraint_onlyMatchingLocationUsed() {
        ResourceAgent agent = createAgent("Agent1");
        ComputingAppliance euNode = createNode("EuNode", "X", "eu-west", false);
        ComputingAppliance usNode = createNode("UsNode", "X", "us-east", false);
        Capacity euCap = new Capacity(euNode, 10.0, 10L, 10L);
        Capacity usCap = new Capacity(usNode, 10.0, 10L, 10L);
        agent.capacities.put("EuNode", euCap);
        agent.capacities.put("UsNode", usCap);

        Component c = component("C1", 1.0, 1L, 1L);
        c.requirements.location = "eu-west";
        AgentApplication app = application(c);

        List<LocalOffer> offers = new ExhaustiveMappingStrategy()
                .generateLocalOffers(agent, app);

        assertEquals(1, offers.size());
        assertSame(euCap, offers.get(0).placements.get(0).capacity);
    }

    private static ResourceAgent createAgent(String name) {
        return new ResourceAgent(name, 1, new ExhaustiveMappingStrategy(), new FloodingMessagingStrategy());
    }

    private static Capacity capacity(String nodeName, double cpu, long memory, long storage) {
        return new Capacity(createNode(nodeName, "X", "X", false), cpu, memory, storage);
    }

    private static AgentApplication application(Component... components) {
        AgentApplication app = new AgentApplication();
        app.name = "App-1";
        app.components = List.of(components);
        return app;
    }

    private static Component component(String id, Double cpu, Long memory, Long storage) {
        Component c = new Component();
        c.id = id;
        ComponentRequirements r = new ComponentRequirements();
        r.cpu = cpu;
        r.memory = memory;
        r.storage = storage;
        c.requirements = r;
        return c;
    }

    private static ComputingAppliance createNode(String name, String provider, String location, boolean edge) {
        return new ComputingAppliance(
                Config.createNode(name, 10, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                        1, 1, 1, 1, 1, new HashMap<>()),
                new GeoLocation(0, 0), location, provider, edge);
    }
}