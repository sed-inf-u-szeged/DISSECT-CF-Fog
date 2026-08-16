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

class LocalMetricsCalculatorTest {

    private static final double EPSILON = 1e-9;

    @BeforeEach
    void resetGlobalState() {
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
    }

    @Test
    void calculate_emptyPlacements_throwsException() {
        ResourceAgent agent = createAgent("Agent1", 100.0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new LocalMetricsCalculator().calculate(agent, List.of()));

        assertEquals(
                "LocalOffer metrics cannot be calculated for empty placements.",
                exception.getMessage());
    }

    @Test
    void calculate_missingLatencyForRepository_throwsException() {
        ResourceAgent agent = createAgent("Agent1", 100.0);

        Capacity capacity = capacity("Node1", 10.0, 100L, 1000L, 2000L, 25);
        agent.capacities.put("Node1", capacity);

        Component component = component("C1", 2.0, 10L, 100L);
        List<ComponentPlacement> placements = List.of(new ComponentPlacement(component, capacity));

        Repository repository = capacity.node.iaas.repositories.get(0);
        repository.getLatencies().remove(repository.getName());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new LocalMetricsCalculator().calculate(agent, placements));

        assertEquals(
                "No input latency is configured for repository: " + repository.getName(),
                exception.getMessage());
    }

    @Test
    void calculate_populatedScenario_returnsExpectedMetrics() {
        ResourceAgent agent = createAgent("Agent1", 100.0);

        Capacity capA = capacity("NodeA", 10.0, 100L, 1000L, 1000L, 10);
        Capacity capB = capacity("NodeB", 20.0, 200L, 2000L, 4000L, 40);
        agent.capacities.put("NodeA", capA);
        agent.capacities.put("NodeB", capB);

        Component comp1 = component("C1", 2.0, 10L, 100L);
        Component comp2 = component("C2", 3.0, 20L, 200L);
        Component comp3 = component("C3", 4.0, 40L, 400L);

        List<ComponentPlacement> placements = List.of(
                new ComponentPlacement(comp1, capA),
                new ComponentPlacement(comp2, capA),
                new ComponentPlacement(comp3, capB));

        LocalMetrics metrics = new LocalMetricsCalculator().calculate(agent, placements);

        assertEquals(0.9, metrics.balance, EPSILON);
        assertEquals(0.25555555555555554, metrics.utilisation, EPSILON);
        assertEquals(1.0, metrics.fragmentation, EPSILON);
        assertEquals(0.8, metrics.compactness, EPSILON);
        assertEquals(25.555555555555554, metrics.cost, EPSILON);
        assertEquals(20.0, metrics.latency, EPSILON);
        assertEquals(2000.0, metrics.bandwidth, EPSILON);

        double expectedEnergy = expectedProjectedEnergy(placements);
        assertEquals(expectedEnergy, metrics.energy, EPSILON);
    }

    private static double expectedProjectedEnergy(List<ComponentPlacement> placements) {
        Set<ComputingAppliance> affectedNodes = new LinkedHashSet<>();
        for (ComponentPlacement placement : placements) {
            affectedNodes.add(placement.capacity.node);
        }

        double totalProjectedPower = 0.0;
        for (ComputingAppliance node : affectedNodes) {
            PhysicalMachine machine = node.iaas.machines.get(0);
            double totalCpu = machine.getCapacities().getTotalProcessingPower();
            double currentlyFreeCpu = machine.freeCapacities.getTotalProcessingPower();

            double offerCpuDemand = 0.0;
            for (ComponentPlacement placement : placements) {
                if (placement.capacity.node == node) {
                    offerCpuDemand += placement.component.requirements.cpu;
                }
            }

            double projectedCpuUtilisation = 1.0 - (currentlyFreeCpu - offerCpuDemand) / totalCpu;
            double projectedPower = machine.getCurrentPowerBehavior().getMinConsumption()
                    + machine.getCurrentPowerBehavior().getConsumptionRange() * projectedCpuUtilisation;
            totalProjectedPower += projectedPower;
        }

        return totalProjectedPower;
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
