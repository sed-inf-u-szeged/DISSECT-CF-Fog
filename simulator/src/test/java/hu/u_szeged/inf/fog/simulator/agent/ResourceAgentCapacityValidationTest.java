package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.FirstFitMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ResourceAgentCapacityValidationTest {

    @BeforeAll
    static void enableLoggingOnce() {
        SimLogger.setLogging(0, true);
    }

    @BeforeEach
    void resetGlobalState() {
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
    }

    @Test
    void throwsIfExceedsNodeTest() {
        ComputingAppliance node1 = new ComputingAppliance(
                Config.createNode("Node1", 10, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                        1, 1, 1, 1, 1, new HashMap<>()),
                new GeoLocation(0, -0), "X", "X", false);

        ComputingAppliance node2 = new ComputingAppliance(
                Config.createNode("Node2", 10, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                        1, 1, 1, 1, 1, new HashMap<>()),
                new GeoLocation(0, -0), "X", "X", false);

        VirtualAppliance resourceAgentVa = new VirtualAppliance("x", 1, 0, false, 1);
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 1);

        ResourceAgent agentA = new ResourceAgent("AgentA", 1, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        agentA.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node1, 2, 2 * ScenarioBase.GB_IN_BYTE, 2 * ScenarioBase.GB_IN_BYTE),
                new Capacity(node2, 2, 2 * ScenarioBase.GB_IN_BYTE, 2 * ScenarioBase.GB_IN_BYTE));

        ResourceAgent agentB = new ResourceAgent("AgentB", 1, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());

        assertThrows(IllegalStateException.class, () -> agentB.initResourceAgent(
                resourceAgentVa, resourceAgentArc,
                new Capacity(node2, 10, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE)
        ));
    }

    @Test
    void succeedsIfFitsRemainingTest() {
        ComputingAppliance node1 = new ComputingAppliance(
                Config.createNode("Node1", 10, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                        1, 1, 1, 1, 1, new HashMap<>()),
                new GeoLocation(0, -0), "X", "X", false);

        ComputingAppliance node2 = new ComputingAppliance(
                Config.createNode("Node2", 10, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                        1, 1, 1, 1, 1, new HashMap<>()),
                new GeoLocation(0, -0), "X", "X", false);

        VirtualAppliance resourceAgentVa = new VirtualAppliance("x", 1, 0, false, 1);
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 1);

        ResourceAgent agentA = new ResourceAgent("AgentA", 1, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        agentA.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node1, 2, 2 * ScenarioBase.GB_IN_BYTE, 2 * ScenarioBase.GB_IN_BYTE),
                new Capacity(node2, 2, 2 * ScenarioBase.GB_IN_BYTE, 2 * ScenarioBase.GB_IN_BYTE));

        ResourceAgent agentB = new ResourceAgent("AgentB", 1, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());

        assertDoesNotThrow(() -> agentB.initResourceAgent(
                resourceAgentVa, resourceAgentArc,
                new Capacity(node2, 8, 8 * ScenarioBase.GB_IN_BYTE, 8 * ScenarioBase.GB_IN_BYTE)
        ));
    }

    @Test
    void throwsIfCombinedExceedsTest() {
        ComputingAppliance node1 = new ComputingAppliance(
                Config.createNode("Node1", 10, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                        1, 1, 1, 1, 1, new HashMap<>()),
                new GeoLocation(0, -0), "X", "X", false);

        ComputingAppliance node2 = new ComputingAppliance(
                Config.createNode("Node2", 10, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                        1, 1, 1, 1, 1, new HashMap<>()),
                new GeoLocation(0, -0), "X", "X", false);

        VirtualAppliance resourceAgentVa = new VirtualAppliance("x", 1, 0, false, 1);
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 1);

        ResourceAgent agentA = new ResourceAgent("AgentA", 1, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());

        assertThrows(IllegalStateException.class, () -> agentA.initResourceAgent(
                resourceAgentVa, resourceAgentArc,
                new Capacity(node1, 2, 2 * ScenarioBase.GB_IN_BYTE, 2 * ScenarioBase.GB_IN_BYTE),
                new Capacity(node2, 5, 5 * ScenarioBase.GB_IN_BYTE, 5 * ScenarioBase.GB_IN_BYTE),
                new Capacity(node2, 5, 5 * ScenarioBase.GB_IN_BYTE, 5 * ScenarioBase.GB_IN_BYTE)
        ));
    }
}
