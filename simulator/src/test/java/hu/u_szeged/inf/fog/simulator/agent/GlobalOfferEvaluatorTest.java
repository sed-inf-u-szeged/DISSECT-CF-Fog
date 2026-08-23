package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.ComponentRequirements;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalMetricsCalculator;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.LocalMetrics;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.pareto.ExhaustiveMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.GlobalOfferEvaluator;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.GlobalOfferMetrics;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalOfferEvaluatorTest {

    private static final double EPSILON = 1e-9;

    @BeforeEach
    void resetGlobalState() {
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
        AgentApplication.allAgentApplications.clear();
    }

    @Test
    void evaluateOffer_aggregatesMetricsPerAgentAndPlacementCount() {
        ResourceAgent agentA = createAgent("AgentA", 10.0);
        ResourceAgent agentB = createAgent("AgentB", 20.0);

        Capacity capA = capacity("NodeA", 10.0, 100L, 1000L, 1000L, 10);
        Capacity capB = capacity("NodeB", 20.0, 200L, 2000L, 2000L, 20);
        agentA.capacities.put(capA.node.name, capA);
        agentB.capacities.put(capB.node.name, capB);

        Component c1 = component("C1", 2.0, 10L, 100L);
        Component c2 = component("C2", 3.0, 20L, 200L);
        Component c3 = component("C3", 4.0, 40L, 400L);

        Offer offer = new Offer(Map.of(
                agentA, Set.of(c1, c2),
                agentB, Set.of(c3)
        ), 0);

        offer.selectedPlacements.addAll(List.of(
                new ComponentPlacement(c1, capA),
                new ComponentPlacement(c2, capA),
                new ComponentPlacement(c3, capB)));

        GlobalOfferEvaluator evaluator = new GlobalOfferEvaluator();
        GlobalOfferMetrics metrics = evaluator.evaluate(offer);

        double expectedCost = 0.0;
        double expectedLatencyWeighted = 0.0;
        double expectedBandwidthWeighted = 0.0;
        int expectedPlacementCount = 0;

        List<ComponentPlacement> placementsForA = List.of(
                new ComponentPlacement(c1, capA),
                new ComponentPlacement(c2, capA));

        LocalMetrics localA = new LocalMetricsCalculator().calculate(agentA, placementsForA);
        expectedCost += localA.cost;
        expectedLatencyWeighted += localA.latency * placementsForA.size();
        expectedBandwidthWeighted += localA.bandwidth * placementsForA.size();
        expectedPlacementCount += placementsForA.size();

        List<ComponentPlacement> placementsForB = List.of(new ComponentPlacement(c3, capB));
        LocalMetrics localB = new LocalMetricsCalculator().calculate(agentB, placementsForB);
        expectedCost += localB.cost;
        expectedLatencyWeighted += localB.latency * placementsForB.size();
        expectedBandwidthWeighted += localB.bandwidth * placementsForB.size();
        expectedPlacementCount += placementsForB.size();

        assertEquals(2, metrics.providerCount);
        assertEquals(expectedCost, metrics.cost, EPSILON);
        assertEquals(expectedLatencyWeighted / expectedPlacementCount, metrics.latency, EPSILON);
        assertEquals(expectedBandwidthWeighted / expectedPlacementCount, metrics.bandwidth, EPSILON);
        assertEquals(new LocalMetricsCalculator().calculateProjectedPower(offer.selectedPlacements), metrics.energy, EPSILON);
    }

    @Test
    void calculateHardRequirementViolation_sumsEveryExceededRequirement() {
        AgentApplication app = new AgentApplication();
        app.minProviderCount = 2;
        app.maxProviderCount = 1;
        app.maxCost = 10.0;
        app.maxLatency = 20.0;
        app.minBandwidth = 100.0;
        app.maxEnergyConsumption = 3.0;

        GlobalOfferMetrics metrics = new GlobalOfferMetrics(
                2,
                18.0,
                5.0,
                25.0,
                80.0);

        double violation = new GlobalOfferEvaluator().calculateHardRequirementViolation(app, metrics);

        assertEquals(2.9166666666666665, violation, EPSILON);
    }

    @Test
    void calculateQosUtility_returnsNormalizedWeightedScore() {
        AgentApplication app = new AgentApplication();
        app.price = 0.5;
        app.energy = 0.3;
        app.latency = 0.2;
        app.bandwidth = 0.1;
        app.maxCost = 20.0;
        app.maxEnergyConsumption = 40.0;
        app.maxLatency = 50.0;
        app.minBandwidth = 100.0;

        GlobalOfferMetrics metrics = new GlobalOfferMetrics(2, 10.0, 25.0, 30.0, 200.0);

        double expected = (
                app.price * (metrics.cost / app.maxCost)
                        + app.energy * (metrics.energy / app.maxEnergyConsumption)
                        + app.latency * (metrics.latency / app.maxLatency)
                        + app.bandwidth * (app.minBandwidth / Math.max(metrics.bandwidth, 1e-9)))
                / (app.price + app.energy + app.latency + app.bandwidth);

        assertEquals(expected, new GlobalOfferEvaluator().calculateQosUtility(app, metrics), EPSILON);
    }

    @Test
    void calculateQosUtility_whenWeightedRequirementReferenceIsMissing_throwsException() {
        AgentApplication app = new AgentApplication();
        app.price = 1.0;
        app.energy = 0.0;
        app.latency = 0.0;
        app.bandwidth = 0.0;
        app.maxCost = null;

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new GlobalOfferEvaluator().calculateQosUtility(app, new GlobalOfferMetrics(1, 10.0, 5.0, 10.0, 50.0)));

        assertTrue(exception.getMessage().contains("maxCost must be positive"));
    }

    @Test
    void evaluateOffer_whenAgentHasNoSelectedPlacements_throwsException() {
        ResourceAgent agent = createAgent("AgentA", 10.0);
        Capacity capacity = capacity("NodeA", 10.0, 100L, 1000L, 1000L, 10);
        agent.capacities.put(capacity.node.name, capacity);

        Component c1 = component("C1", 2.0, 10L, 100L);
        Offer offer = new Offer(Map.of(agent, Set.of(c1)), 0);
        offer.selectedPlacements = List.of();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new GlobalOfferEvaluator().evaluate(offer));

        assertTrue(exception.getMessage().contains("Offer contains an agent without selected placements: AgentA"));
    }

    private static ResourceAgent createAgent(String name, double hourlyPrice) {
        return new ResourceAgent(name, hourlyPrice, new ExhaustiveMappingStrategy(), new FloodingMessagingStrategy());
    }

    private static Capacity capacity(
            String nodeName,
            double cpu,
            long memory,
            long storage,
            long bandwidth,
            int latency) {
        Map<String, Integer> latencyMap = new HashMap<>();
        ComputingAppliance node = new ComputingAppliance(
                Config.createNode(nodeName, cpu, memory, storage, 10, 20, 50, bandwidth, latency, latencyMap),
                new GeoLocation(0, 0), "x", "x", false);
        return new Capacity(node, cpu, memory, storage);
    }

    private static Component component(String id, Double cpu, Long memory, Long storage) {
        Component component = new Component();
        component.id = id;

        ComponentRequirements requirements = new ComponentRequirements();
        requirements.cpu = cpu;
        requirements.memory = memory;
        requirements.storage = storage;
        component.requirements = requirements;
        return component;
    }
}
