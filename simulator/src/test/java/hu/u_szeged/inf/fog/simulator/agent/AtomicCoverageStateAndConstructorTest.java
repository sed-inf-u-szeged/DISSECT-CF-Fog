package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.ComponentRequirements;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.FirstFitMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.LocalMetrics;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.sa.AtomicCoverageConstructor;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.sa.AtomicCoverageState;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicCoverageStateAndConstructorTest {

    private static final AtomicInteger NODE_SEQUENCE = new AtomicInteger();

    @BeforeEach
    void resetGlobalState() {
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
        AgentApplication.allAgentApplications.clear();
        NODE_SEQUENCE.set(0);
        SeedSyncer.setSeed(12345);
    }

    @Test
    void state_tracksCoverageAndAgentCountsForCompleteUniqueSelection() {
        AgentApplication app = application(component("C1"), component("C2"), component("C3"));
        ResourceAgent agentA = createAgent("AgentA");
        ResourceAgent agentB = createAgent("AgentB");

        Component c1 = componentById(app, "C1");
        Component c2 = componentById(app, "C2");
        Component c3 = componentById(app, "C3");

        LocalOffer offerA = offer(agentA, componentPlacement(c1), componentPlacement(c2));
        LocalOffer offerB = offer(agentB, componentPlacement(c3));

        AtomicCoverageState state = new AtomicCoverageState(app.components, List.of(offerA, offerB));

        assertEquals(1, state.coverageCounts.get(componentById(app, "C1")));
        assertEquals(1, state.coverageCounts.get(componentById(app, "C2")));
        assertEquals(1, state.coverageCounts.get(componentById(app, "C3")));
        assertEquals(1, state.selectedOfferCountsByAgent.get(agentA));
        assertEquals(1, state.selectedOfferCountsByAgent.get(agentB));
        assertTrue(state.hasAtMostOneOfferPerAgent());
        assertTrue(state.isCompleteAndUnique());
        assertTrue(state.isStructurallyValid());
    }

    @Test
    void state_detectsDuplicateCoverageAndMultipleOffersPerAgent() {
        AgentApplication app = application(component("C1"), component("C2"));
        ResourceAgent agentA = createAgent("AgentA");
        ResourceAgent agentB = createAgent("AgentB");

        Component c1 = componentById(app, "C1");
        Component c2 = componentById(app, "C2");

        LocalOffer offerA = offer(agentA, componentPlacement(c1), componentPlacement(c2));
        LocalOffer offerB = offer(agentA, componentPlacement(c2));
        LocalOffer offerC = offer(agentB, componentPlacement(c1));

        AtomicCoverageState state = new AtomicCoverageState(app.components, List.of(offerA, offerB, offerC));

        assertEquals(2, state.coverageCounts.get(componentById(app, "C2")));
        assertEquals(2, state.selectedOfferCountsByAgent.get(agentA));
        assertTrue(!state.hasAtMostOneOfferPerAgent());
        assertTrue(!state.isCompleteAndUnique());
        assertTrue(!state.isStructurallyValid());
    }

    @Test
    void constructor_buildsCompleteValidCoverageAndIsReproducibleWithSeed() {
        AgentApplication app = application(component("C1"), component("C2"), component("C3"));
        ResourceAgent agentA = createAgent("AgentA");
        ResourceAgent agentB = createAgent("AgentB");
        ResourceAgent agentC = createAgent("AgentC");

        Component c1 = componentById(app, "C1");
        Component c2 = componentById(app, "C2");
        Component c3 = componentById(app, "C3");

        List<LocalOffer> availableOffers = List.of(
                offer(agentA, componentPlacement(c1)),
                offer(agentB, componentPlacement(c2)),
                offer(agentC, componentPlacement(c3))
        );

        SeedSyncer.setSeed(101);
        AtomicCoverageState first = new AtomicCoverageConstructor().constructCoverage(app, availableOffers, 10);

        SeedSyncer.setSeed(101);
        AtomicCoverageState second = new AtomicCoverageConstructor().constructCoverage(app, availableOffers, 10);

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.isStructurallyValid());
        assertTrue(second.isStructurallyValid());
        assertEquals(signature(first), signature(second));
        assertEquals(3, first.selectedOffers.size());
        assertEquals(1, first.coverageCounts.get(componentById(app, "C1")));
        assertEquals(1, first.coverageCounts.get(componentById(app, "C2")));
        assertEquals(1, first.coverageCounts.get(componentById(app, "C3")));
    }

    @Test
    void constructor_repairCoverage_keepsRetainedOffersAndCompletesMissingCoverage() {
        AgentApplication app = application(component("C1"), component("C2"), component("C3"));
        ResourceAgent agentA = createAgent("AgentA");
        ResourceAgent agentB = createAgent("AgentB");
        ResourceAgent agentC = createAgent("AgentC");

        Component c1 = componentById(app, "C1");
        Component c2 = componentById(app, "C2");
        Component c3 = componentById(app, "C3");

        LocalOffer retained = offer(agentA, componentPlacement(c1), componentPlacement(c2));
        List<LocalOffer> availableOffers = List.of(
                retained,
                offer(agentB, componentPlacement(c3)),
                offer(agentC, componentPlacement(c2))
        );

        AtomicCoverageState repaired = new AtomicCoverageConstructor().repairCoverage(app, availableOffers, List.of(retained), 10);

        assertNotNull(repaired);
        assertTrue(repaired.isStructurallyValid());
        assertEquals(2, repaired.selectedOffers.size());
        assertEquals(1, repaired.coverageCounts.get(componentById(app, "C1")));
        assertEquals(1, repaired.coverageCounts.get(componentById(app, "C2")));
        assertEquals(1, repaired.coverageCounts.get(componentById(app, "C3")));
        assertEquals(1, repaired.selectedOfferCountsByAgent.get(agentA));
    }

    private static ResourceAgent createAgent(String name) {
        return new ResourceAgent(name, 1.0, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
    }

    private static AgentApplication application(Component... components) {
        AgentApplication app = new AgentApplication();
        app.name = "App";
        app.components = List.of(components);
        return app;
    }

    private static Component component(String id) {
        Component component = new Component();
        component.id = id;
        ComponentRequirements requirements = new ComponentRequirements();
        requirements.cpu = 1.0;
        requirements.memory = 1L;
        requirements.storage = 1L;
        component.requirements = requirements;
        return component;
    }

    private static Component componentById(AgentApplication app, String id) {
        return app.components.stream()
                .filter(component -> component.id.equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static ComponentPlacement componentPlacement(Component component) {
        return new ComponentPlacement(component, capacity(component.id + "Node" + NODE_SEQUENCE.incrementAndGet()));
    }

    private static Capacity capacity(String nodeName) {
        return new Capacity(createNode(nodeName), 10.0, 100L, 100L);
    }

    private static ComputingAppliance createNode(String name) {
        return new ComputingAppliance(
                Config.createNode(name, 10.0, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                        1, 1, 1, 1, 1, new HashMap<>()),
                new GeoLocation(0, 0), "loc", "provider", false);
    }

    private static LocalOffer offer(ResourceAgent agent, ComponentPlacement... placements) {
        return new LocalOffer(agent, List.of(placements), new LocalMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static String signature(AtomicCoverageState state) {
        return state.selectedOffers.stream()
                .map(offer -> offer.agent.name + ":" + offer.placements.stream()
                        .map(placement -> placement.component.id)
                        .sorted()
                        .toList())
                .toList()
                .toString();
    }
}
