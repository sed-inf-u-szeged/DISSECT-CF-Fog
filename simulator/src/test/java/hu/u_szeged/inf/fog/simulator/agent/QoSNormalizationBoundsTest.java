package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.ComponentRequirements;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.MappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.pareto.ExhaustiveMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.QoSNormalizationBounds;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QoSNormalizationBoundsTest {

    private static final double EPSILON = 1e-9;

    @BeforeEach
    void resetGlobalState() {
        clearGlobalState();
    }

    @AfterEach
    void cleanupGlobalState() {
        clearGlobalState();
    }

    private static void clearGlobalState() {
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
        AgentApplication.allAgentApplications.clear();
    }

    @Test
    void calculateForComponents_computesExpectedBounds() {
        ResourceAgent agentA = createAgent("AgentA", 10.0);
        ResourceAgent agentB = createAgent("AgentB", 30.0);

        Capacity capA = capacity("NodeA", 10.0, 100L, 1000L, 1200L, 15, 10.0, 20.0, 40.0);
        Capacity capB = capacity("NodeB", 20.0, 300L, 3000L, 2400L, 35, 20.0, 35.0, 70.0);
        agentA.capacities.put(capA.node.name, capA);
        agentB.capacities.put(capB.node.name, capB);

        Component c1 = component("C1", 2.0, 10L, 100L);
        Component c2 = component("C2", 5.0, 20L, 200L);
        List<Component> components = List.of(c1, c2);

        QoSNormalizationBounds bounds = QoSNormalizationBounds.calculateForComponents(components);

        double expectedMinimumCost = 0.0;
        double expectedMaximumCost = 0.0;

        for (Component component : components) {
            double costA = calculateComponentCost(agentA, component);
            double costB = calculateComponentCost(agentB, component);
            expectedMinimumCost += Math.min(costA, costB) * 0.85;
            expectedMaximumCost += Math.max(costA, costB) * 1.15;
        }

        PhysicalMachine machineA = capA.node.iaas.machines.get(0);
        PhysicalMachine machineB = capB.node.iaas.machines.get(0);
        double expectedMinimumEnergy = Math.min(
                machineA.getCurrentPowerBehavior().getMinConsumption(),
                machineB.getCurrentPowerBehavior().getMinConsumption());
        double expectedMaximumEnergy = maxPower(machineA) + maxPower(machineB);

        assertEquals(expectedMinimumCost, bounds.minimumCost, EPSILON);
        assertEquals(expectedMaximumCost, bounds.maximumCost, EPSILON);
        assertEquals(expectedMinimumEnergy, bounds.minimumEnergy, EPSILON);
        assertEquals(expectedMaximumEnergy, bounds.maximumEnergy, EPSILON);
        assertEquals(15.0, bounds.minimumLatency, EPSILON);
        assertEquals(35.0, bounds.maximumLatency, EPSILON);
        assertEquals(1200.0, bounds.minimumBandwidth, EPSILON);
        assertEquals(2400.0, bounds.maximumBandwidth, EPSILON);
    }

    @Test
    void calculateFor_delegatesToApplicationComponents() {
        ResourceAgent agent = createAgent("AgentA", 10.0);
        Capacity capacity = capacity("NodeA", 10.0, 100L, 1000L, 1000L, 10, 10.0, 20.0, 40.0);
        agent.capacities.put(capacity.node.name, capacity);

        Component c1 = component("C1", 2.0, 10L, 100L);
        Component c2 = component("C2", 4.0, 20L, 200L);

        AgentApplication application = new AgentApplication();
        application.components = List.of(c1, c2);

        QoSNormalizationBounds fromApplication = QoSNormalizationBounds.calculateFor(application);
        QoSNormalizationBounds fromCollection = QoSNormalizationBounds.calculateForComponents(application.components);

        assertEquals(fromCollection.minimumCost, fromApplication.minimumCost, EPSILON);
        assertEquals(fromCollection.maximumCost, fromApplication.maximumCost, EPSILON);
        assertEquals(fromCollection.minimumEnergy, fromApplication.minimumEnergy, EPSILON);
        assertEquals(fromCollection.maximumEnergy, fromApplication.maximumEnergy, EPSILON);
        assertEquals(fromCollection.minimumLatency, fromApplication.minimumLatency, EPSILON);
        assertEquals(fromCollection.maximumLatency, fromApplication.maximumLatency, EPSILON);
        assertEquals(fromCollection.minimumBandwidth, fromApplication.minimumBandwidth, EPSILON);
        assertEquals(fromCollection.maximumBandwidth, fromApplication.maximumBandwidth, EPSILON);
    }

    @Test
    void calculateForComponents_whenComponentCollectionIsEmpty_throwsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> QoSNormalizationBounds.calculateForComponents(List.of()));

        assertTrue(exception.getMessage().contains("requires at least one component"));
    }

    @Test
    void calculateForComponents_whenNoResourceAgents_throwsException() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> QoSNormalizationBounds.calculateForComponents(List.of(component("C1", 1.0, 1L, 1L))));

        assertTrue(exception.getMessage().contains("requires at least one ResourceAgent"));
    }

    @Test
    void calculateForComponents_whenAgentsHaveNoNodes_throwsException() {
        createAgent("AgentA", 10.0);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> QoSNormalizationBounds.calculateForComponents(List.of(component("C1", 1.0, 1L, 1L))));

        assertTrue(exception.getMessage().contains("requires at least one resource node"));
    }

    @Test
    void calculateForComponents_whenRepositorySelfLatencyIsMissing_throwsException() {
        ResourceAgent agent = createAgent("AgentA", 10.0);
        Capacity capacity = capacity("NodeA", 10.0, 100L, 1000L, 1000L, 10, 10.0, 20.0, 40.0);
        agent.capacities.put(capacity.node.name, capacity);

        Repository repository = capacity.node.iaas.repositories.get(0);
        repository.getLatencies().remove(repository.getName());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> QoSNormalizationBounds.calculateForComponents(List.of(component("C1", 1.0, 1L, 1L))));

        assertTrue(exception.getMessage().contains("No input latency is configured for repository"));
    }

    @Test
    void calculateForComponents_whenMultipleAgentsShareSameNode_deduplicatesNodeBasedBounds() {
        ResourceAgent agentA = createAgent("AgentA", 10.0);
        ResourceAgent agentB = createAgent("AgentB", 15.0);

        ComputingAppliance sharedNode = createNode("SharedNode", 10.0, 100L, 1000L, 1500L, 25, 10.0, 20.0, 40.0);
        agentA.capacities.put("sharedA", new Capacity(sharedNode, 5.0, 50L, 500L));
        agentB.capacities.put("sharedB", new Capacity(sharedNode, 5.0, 50L, 500L));

        QoSNormalizationBounds bounds = QoSNormalizationBounds.calculateForComponents(
                List.of(component("C1", 1.0, 1L, 1L)));

        PhysicalMachine machine = sharedNode.iaas.machines.get(0);
        Repository repository = sharedNode.iaas.repositories.get(0);
        double expectedMinEnergy = machine.getCurrentPowerBehavior().getMinConsumption();
        double expectedMaxEnergy = maxPower(machine);
        double expectedLatency = repository.getLatencies().get(repository.getName());
        double expectedBandwidth = (repository.getInputbw() + repository.getOutputbw()) / 2.0;

        assertEquals(expectedMinEnergy, bounds.minimumEnergy, EPSILON);
        assertEquals(expectedMaxEnergy, bounds.maximumEnergy, EPSILON);
        assertEquals(expectedLatency, bounds.minimumLatency, EPSILON);
        assertEquals(expectedLatency, bounds.maximumLatency, EPSILON);
        assertEquals(expectedBandwidth, bounds.minimumBandwidth, EPSILON);
        assertEquals(expectedBandwidth, bounds.maximumBandwidth, EPSILON);
    }

    @Test
    void calculateForComponents_whenMoreComponentsThanNodes_limitsMaximumEnergyToNodeCount() {
        ResourceAgent agentA = createAgent("AgentA", 10.0);
        ResourceAgent agentB = createAgent("AgentB", 15.0);

        Capacity capA = capacity("NodeA", 10.0, 100L, 1000L, 1000L, 10, 10.0, 20.0, 40.0);
        Capacity capB = capacity("NodeB", 10.0, 100L, 1000L, 1000L, 10, 30.0, 40.0, 90.0);
        agentA.capacities.put(capA.node.name, capA);
        agentB.capacities.put(capB.node.name, capB);

        List<Component> components = List.of(
                component("C1", 1.0, 1L, 1L),
                component("C2", 1.0, 1L, 1L),
                component("C3", 1.0, 1L, 1L));

        QoSNormalizationBounds bounds = QoSNormalizationBounds.calculateForComponents(components);

        double maxA = maxPower(capA.node.iaas.machines.get(0));
        double maxB = maxPower(capB.node.iaas.machines.get(0));
        double expectedMaximumEnergy = maxA + maxB;

        assertEquals(expectedMaximumEnergy, bounds.maximumEnergy, EPSILON);
    }

    private static double calculateComponentCost(ResourceAgent agent, Component component) {
        double demandShare = agent.calculateDemandShare(
                MappingStrategy.requiredCpu(component),
                MappingStrategy.requiredMemory(component),
                MappingStrategy.requiredStorage(component));

        return agent.baseHourlyPrice * demandShare;
    }

    private static double maxPower(PhysicalMachine machine) {
        return machine.getCurrentPowerBehavior().getMinConsumption()
                + machine.getCurrentPowerBehavior().getConsumptionRange();
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
            int latency,
            double minPower,
            double idlePower,
            double maxPower) {
        Map<String, Integer> latencyMap = new HashMap<>();
        ComputingAppliance node = createNode(nodeName, cpu, memory, storage, bandwidth, latency, minPower, idlePower, maxPower);
        return new Capacity(node, cpu, memory, storage);
    }

    private static ComputingAppliance createNode(
            String nodeName,
            double cpu,
            long memory,
            long storage,
            long bandwidth,
            int latency,
            double minPower,
            double idlePower,
            double maxPower) {
        Map<String, Integer> latencyMap = new HashMap<>();
        return new ComputingAppliance(
                Config.createNode(nodeName, cpu, memory, storage, minPower, idlePower, maxPower, bandwidth, latency, latencyMap),
                new GeoLocation(0, 0), "x", "x", false);
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
