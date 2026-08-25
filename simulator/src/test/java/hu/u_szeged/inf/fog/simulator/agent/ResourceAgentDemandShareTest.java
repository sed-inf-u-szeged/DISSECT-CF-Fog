package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.FirstFitMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceAgentDemandShareTest {

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
    void calculateDemandShare_whenResourcesExist_returnsAverageDemandShareAcrossDimensions() {
        ResourceAgent agent = createAgent("AgentA");
        agent.capacities.put("NodeA", capacity("NodeA", 10.0, 100L, 1000L));
        agent.capacities.put("NodeB", capacity("NodeB", 20.0, 200L, 2000L));

        double share = agent.calculateDemandShare(3.0, 30L, 300L);

        assertEquals(0.1, share, EPSILON);
    }

    @Test
    void calculateDemandShare_whenAllDimensionsAreZeroAndNoDemand_returnsZero() {
        ResourceAgent agent = createAgent("AgentA");
        agent.capacities.put("NodeA", capacity("NodeA", 0.0, 0L, 0L));

        assertEquals(0.0, agent.calculateDemandShare(0.0, 0L, 0L), EPSILON);
    }

    @Test
    void calculateDemandShare_whenCpuIsRequestedWithoutCpuCapacity_throwsException() {
        ResourceAgent agent = createAgent("AgentA");
        agent.capacities.put("NodeA", capacity("NodeA", 0.0, 100L, 1000L));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> agent.calculateDemandShare(1.0, 0L, 0L));

        assertTrue(exception.getMessage().contains("has no CPU capacity but CPU was requested"));
    }

    @Test
    void calculateDemandShare_whenMemoryIsRequestedWithoutMemoryCapacity_throwsException() {
        ResourceAgent agent = createAgent("AgentA");
        agent.capacities.put("NodeA", capacity("NodeA", 10.0, 0L, 1000L));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> agent.calculateDemandShare(0.0, 1L, 0L));

        assertTrue(exception.getMessage().contains("has no memory capacity but memory was requested"));
    }

    @Test
    void calculateDemandShare_whenStorageIsRequestedWithoutStorageCapacity_throwsException() {
        ResourceAgent agent = createAgent("AgentA");
        agent.capacities.put("NodeA", capacity("NodeA", 10.0, 100L, 0L));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> agent.calculateDemandShare(0.0, 0L, 1L));

        assertTrue(exception.getMessage().contains("has no storage capacity but storage was requested"));
    }

    private static ResourceAgent createAgent(String name) {
        return new ResourceAgent(name, 1.0, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
    }

    private static Capacity capacity(String nodeName, double cpu, long memory, long storage) {
        return new Capacity(
                new ComputingAppliance(
                        Config.createNode(nodeName, 10, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                                1, 1, 1, 1, 1, new HashMap<>()),
                        new GeoLocation(0, 0), "X", "X", false),
                cpu, memory, storage);
    }
}
