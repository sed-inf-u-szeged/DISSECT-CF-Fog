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
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.sa.AtomicCoverageSimulatedAnnealing;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.sa.AtomicCoverageState;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicCoverageSimulatedAnnealingTest {

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
    void optimize_returnsNullWhenNoCompleteValidCoverageExists() {
        AgentApplication app = application(component("C1"), component("C2"));
        LocalOffer onlyC1 = offer(createAgent("AgentA"), componentPlacement(app, "C1"));

        AtomicCoverageState result = new AtomicCoverageSimulatedAnnealing().optimize(
                app,
                List.of(onlyC1),
                5,
                3,
                5,
                0.25,
                10,
                100.0,
                1.0,
                0.9,
                1.0,
                2.0);

        assertNull(result);
    }

    @Test
    void optimize_isReproducibleWithSameSeed() throws Exception {
        AgentApplication app = application(component("C1"), component("C2"), component("C3"));
        app.price = 1.0;
        app.energy = 1.0;
        app.latency = 1.0;
        app.bandwidth = 1.0;
        app.maxCost = 1000.0;
        app.maxEnergyConsumption = 1000.0;
        app.maxLatency = 1000.0;
        app.minBandwidth = 1.0;

        LocalOffer offerA = offer(createAgent("AgentA"), componentPlacement(app, "C1"));
        LocalOffer offerB = offer(createAgent("AgentB"), componentPlacement(app, "C2"));
        LocalOffer offerC = offer(createAgent("AgentC"), componentPlacement(app, "C3"));

        SeedSyncer.setSeed(2024);
        AtomicCoverageState first = new AtomicCoverageSimulatedAnnealing().optimize(
                app,
                List.of(offerA, offerB, offerC),
                10,
                5,
                10,
                0.2,
                100,
                50.0,
                1.0,
                0.8,
                1.0,
                3.0);

        SeedSyncer.setSeed(2024);
        AtomicCoverageState second = new AtomicCoverageSimulatedAnnealing().optimize(
                app,
                List.of(offerA, offerB, offerC),
                10,
                5,
                10,
                0.2,
                100,
                50.0,
                1.0,
                0.8,
                1.0,
                3.0);

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.isStructurallyValid());
        assertTrue(second.isStructurallyValid());
        assertEquals(signature(first), signature(second));
        assertEquals(3, first.selectedOffers.size());
    }

    @Test
    void calculateEnergy_rejectsStructurallyInvalidState() throws Exception {
        AgentApplication app = application(component("C1"), component("C2"));
        ResourceAgent agentA = createAgent("AgentA");
        ResourceAgent agentB = createAgent("AgentB");

        LocalOffer invalidOfferA = offer(agentA, componentPlacement(app, "C1"), componentPlacement(app, "C2"));
        LocalOffer invalidOfferB = offer(agentA, componentPlacement(app, "C2"));

        AtomicCoverageState invalidState = new AtomicCoverageState(app.components, List.of(invalidOfferA, invalidOfferB));
        Method calculateEnergy = AtomicCoverageSimulatedAnnealing.class.getDeclaredMethod(
                "calculateEnergy",
                AgentApplication.class,
                AtomicCoverageState.class,
                double.class,
                double.class,
                double.class,
                double.class,
                double.class);
        calculateEnergy.setAccessible(true);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> calculateEnergy.invoke(
                        new AtomicCoverageSimulatedAnnealing(),
                        app,
                        invalidState,
                        5.0,
                        10.0,
                        1.0,
                        1.0,
                        2.0));

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
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

    private static ComponentPlacement componentPlacement(AgentApplication app, String componentId) {
        Component component = app.components.stream()
                .filter(entry -> entry.id.equals(componentId))
                .findFirst()
                .orElseThrow();
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
        return new LocalOffer(
                agent,
                List.of(placements),
                new LocalMetrics(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 10.0, 100.0));
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
