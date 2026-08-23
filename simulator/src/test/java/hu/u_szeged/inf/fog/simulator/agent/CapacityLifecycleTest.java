package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.ComponentRequirements;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.FirstFitMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapacityLifecycleTest {

    private static final double EPSILON = 1e-9;

    @BeforeAll
    static void disableLogging() {
        SimLogger.setLogging(0, false);
    }

    @BeforeEach
    void resetGlobalState() {
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
        AgentApplication.allAgentApplications.clear();
    }

    @Test
    void reserveCapacity_createsReservedUtilisationAndConsumesResources() {
        ResourceAgent agent = createAgent("Agent1", 12.0);
        Capacity capacity = capacity("Node1", 10.0, 100L, 100L);
        agent.capacities.put("Node1", capacity);

        Component component = component("C1", 2.0, 20L, 10L);
        LocalOffer localOffer = localOffer(agent, component, capacity);

        capacity.reserveCapacity(component, agent, localOffer);

        assertEquals(8.0, capacity.cpu, EPSILON);
        assertEquals(80L, capacity.memory);
        assertEquals(90L, capacity.storage);
        assertEquals(1, capacity.utilisations.size());

        Utilisation utilisation = capacity.utilisations.get(0);
        assertEquals(Utilisation.State.RESERVED, utilisation.state);
        assertFalse(utilisation.envelopeReservation);
        assertSame(component, utilisation.component);
        assertEquals(2.0, utilisation.utilisedCpu, EPSILON);
        assertEquals(20L, utilisation.utilisedMemory);
        assertEquals(10L, utilisation.utilisedStorage);
        assertSame(agent, utilisation.resourceAgent);
        assertEquals(1, utilisation.coveredOffers.size());
        assertSame(localOffer, utilisation.coveredOffers.get(0));
        assertEquals(2.0, utilisation.actualCost, EPSILON);
    }

    @Test
    void assignPlacement_reservedUtilisationTransitionsToAssignedAndIsAttachedToOffer() {
        ResourceAgent agent = createAgent("Agent1", 10.0);
        Capacity capacity = capacity("Node1", 10.0, 100L, 100L);
        agent.capacities.put("Node1", capacity);

        Component component = component("C1", 1.0, 10L, 10L);
        LocalOffer localOffer = localOffer(agent, component, capacity);
        capacity.reserveCapacity(component, agent, localOffer);
        Utilisation utilisation = capacity.utilisations.get(0);

        Offer offer = new Offer(new HashMap<>(), 7);
        ComponentPlacement placement = new ComponentPlacement(component, capacity);
        capacity.assignPlacement(placement, offer);

        assertEquals(Utilisation.State.ASSIGNED, utilisation.state);
        assertEquals(1, offer.utilisations.size());
        assertSame(capacity.node, offer.utilisations.get(0).getLeft());
        assertSame(utilisation, offer.utilisations.get(0).getRight());
    }

    @Test
    void assignPlacement_withDifferentCapacity_throwsIllegalArgumentException() {
        ResourceAgent agent = createAgent("Agent1", 10.0);
        Capacity capacity = capacity("Node1", 10.0, 100L, 100L);
        Capacity otherCapacity = capacity("Node2", 10.0, 100L, 100L);
        agent.capacities.put("Node1", capacity);
        agent.capacities.put("Node2", otherCapacity);

        Component component = component("C1", 1.0, 10L, 10L);
        capacity.reserveCapacity(component, agent, localOffer(agent, component, capacity));

        Offer offer = new Offer(new HashMap<>(), 8);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> capacity.assignPlacement(new ComponentPlacement(component, otherCapacity), offer));

        assertEquals("The placement belongs to a different capacity.", exception.getMessage());
    }

    @Test
    void releaseReservation_onlyReservedEntriesAreReleased() {
        ResourceAgent agent = createAgent("Agent1", 10.0);
        Capacity capacity = capacity("Node1", 10.0, 100L, 100L);
        agent.capacities.put("Node1", capacity);

        Component cReserved = component("C1", 2.0, 10L, 10L);
        Component cAssigned = component("C2", 1.0, 10L, 10L);
        capacity.reserveCapacity(cReserved, agent, localOffer(agent, cReserved, capacity));
        capacity.reserveCapacity(cAssigned, agent, localOffer(agent, cAssigned, capacity));
        Utilisation assignedUtilisation = capacity.utilisations.get(1);
        assignedUtilisation.state = Utilisation.State.ASSIGNED;

        Utilisation reservedUtilisation = capacity.utilisations.get(0);
        capacity.releaseReservation(reservedUtilisation);
        capacity.releaseReservation(assignedUtilisation);

        assertEquals(9.0, capacity.cpu, EPSILON);
        assertEquals(90L, capacity.memory);
        assertEquals(90L, capacity.storage);
        assertEquals(1, capacity.utilisations.size());
        assertSame(assignedUtilisation, capacity.utilisations.get(0));
    }

    @Test
    void reserveAtomicOffers_successCreatesEnvelopeReservationAndConsumesResources() {
        ResourceAgent agent = createAgent("Agent1", 10.0);
        Capacity capacity = capacity("Node1", 10.0, 100L, 100L);
        agent.capacities.put("Node1", capacity);

        Component component = component("C1", 1.0, 1L, 1L);
        LocalOffer coveredOffer = localOffer(agent, component, capacity);

        capacity.reserveAtomicOffers(List.of(coveredOffer), agent, 4.0, 30L, 20L);

        assertEquals(6.0, capacity.cpu, EPSILON);
        assertEquals(70L, capacity.memory);
        assertEquals(80L, capacity.storage);
        assertEquals(1, capacity.utilisations.size());

        Utilisation utilisation = capacity.utilisations.get(0);
        assertEquals(Utilisation.State.RESERVED, utilisation.state);
        assertTrue(utilisation.envelopeReservation);
        assertEquals(4.0, utilisation.utilisedCpu, EPSILON);
        assertEquals(30L, utilisation.utilisedMemory);
        assertEquals(20L, utilisation.utilisedStorage);
        assertEquals(0.0, utilisation.actualCost, EPSILON);
        assertSame(agent, utilisation.resourceAgent);
        assertEquals(List.of(coveredOffer), utilisation.coveredOffers);
    }

    @Test
    void reserveAtomicOffers_invalidInputsThrow() {
        ResourceAgent agent = createAgent("Agent1", 10.0);
        Capacity capacity = capacity("Node1", 10.0, 100L, 100L);
        agent.capacities.put("Node1", capacity);

        Component component = component("C1", 1.0, 1L, 1L);
        LocalOffer coveredOffer = localOffer(agent, component, capacity);

        assertThrows(IllegalArgumentException.class,
                () -> capacity.reserveAtomicOffers(List.of(), agent, 1.0, 1L, 1L));

        assertThrows(IllegalArgumentException.class,
                () -> capacity.reserveAtomicOffers(List.of(coveredOffer), agent, -1.0, 1L, 1L));

        assertThrows(IllegalStateException.class,
                () -> capacity.reserveAtomicOffers(List.of(coveredOffer), agent, 20.0, 1L, 1L));
    }

    @Test
    void releaseAllocatedCapacityForShutdown_withMissingVm_throwsAndKeepsAllocation() {
        ResourceAgent agent = createAgent("Agent1", 10.0);
        Capacity capacity = capacity("Node1", 10.0, 100L, 100L);
        agent.capacities.put("Node1", capacity);

        Component component = component("C1", 2.0, 20L, 30L);
        capacity.reserveCapacity(component, agent, localOffer(agent, component, capacity));
        Utilisation utilisation = capacity.utilisations.get(0);
        utilisation.setToAllocated();
        utilisation.vm = null;

        assertThrows(IllegalStateException.class,
                () -> capacity.releaseAllocatedCapacityForShutdown(component));

        assertEquals(8.0, capacity.cpu, EPSILON);
        assertEquals(80L, capacity.memory);
        assertEquals(70L, capacity.storage);
        assertEquals(Utilisation.State.ALLOCATED, utilisation.state);
        assertEquals(1, capacity.utilisations.size());
    }

    @Test
    void releaseAllocatedCapacityForShutdown_doesNothingForOtherComponent() {
        ResourceAgent agent = createAgent("Agent1", 10.0);
        Capacity capacity = capacity("Node1", 10.0, 100L, 100L);
        agent.capacities.put("Node1", capacity);

        Component c1 = component("C1", 2.0, 20L, 30L);
        Component c2 = component("C2", 2.0, 20L, 30L);
        capacity.reserveCapacity(c1, agent, localOffer(agent, c1, capacity));
        Utilisation utilisation = capacity.utilisations.get(0);
        utilisation.setToAllocated();

        capacity.releaseAllocatedCapacityForShutdown(c2);

        assertEquals(8.0, capacity.cpu, EPSILON);
        assertEquals(80L, capacity.memory);
        assertEquals(70L, capacity.storage);
        assertEquals(Utilisation.State.ALLOCATED, utilisation.state);
    }

    private static ResourceAgent createAgent(String name, double price) {
        return new ResourceAgent(name, price, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
    }

    private static Capacity capacity(String nodeName, double cpu, long memory, long storage) {
        return new Capacity(
                new ComputingAppliance(
                        Config.createNode(nodeName, 10, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                                1, 1, 1, 1, 1, new HashMap<>()),
                        new GeoLocation(0, 0), "X", "X", false),
                cpu, memory, storage);
    }

    private static Component component(String id, double cpu, long memory, long storage) {
        Component component = new Component();
        component.id = id;
        ComponentRequirements requirements = new ComponentRequirements();
        requirements.cpu = cpu;
        requirements.memory = memory;
        requirements.storage = storage;
        component.requirements = requirements;
        return component;
    }

    private static LocalOffer localOffer(ResourceAgent agent, Component component, Capacity capacity) {
        return new LocalOffer(agent, List.of(new ComponentPlacement(component, capacity)), null);
    }
}
