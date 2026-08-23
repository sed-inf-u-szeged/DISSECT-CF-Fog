package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.FirstFitMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.LocalMetrics;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.sa.AtomicCoverageState;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceAgentCombinationGenerationTest {

    @BeforeEach
    void resetGlobalState() {
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
        AgentApplication.allAgentApplications.clear();
    }

    @Test
    void generateNonAtomicOfferCombinations_buildsValidAssignmentsAndReservesResources() throws Exception {
        Component c1 = component("C1");
        Component c2 = component("C2");
        AgentApplication app = application(c1, c2);

        ResourceAgent agentA = createAgent("AgentA");
        ResourceAgent agentB = createAgent("AgentB");
        Capacity capA = capacity("NodeA", 10.0, 100L, 100L);
        Capacity capB = capacity("NodeB", 10.0, 100L, 100L);
        agentA.capacities.put(capA.node.name, capA);
        agentB.capacities.put(capB.node.name, capB);

        LocalOffer offerA = localOffer(agentA, capA, c1, c2);
        LocalOffer offerB = localOffer(agentB, capB, c1, c2);

        invokeReserveLocalOffers(agentA, List.of(offerA));
        invokeReserveLocalOffers(agentB, List.of(offerB));
        invokeGenerateNonAtomicOfferCombinations(agentA, List.of(offerA, offerB), app);

        assertTrue(app.offers.size() >= 1);
        assertTrue(app.offers.stream().allMatch(offer -> !offer.selectedPlacements.isEmpty()));
        assertTrue(capA.utilisations.stream().anyMatch(utilisation -> utilisation.state == Utilisation.State.RESERVED));
        assertTrue(capB.utilisations.stream().anyMatch(utilisation -> utilisation.state == Utilisation.State.RESERVED));
    }

    @Test
    void materializeWinningAtomicReservations_releasesEnvelopeReservationBeforeAssigningSelection() throws Exception {
        Component c1 = component("C1");
        Component c2 = component("C2");
        AgentApplication app = application(c1, c2);

        ResourceAgent agent = createAgent("AgentA");
        Capacity cap = capacity("NodeA", 10.0, 100L, 100L);
        agent.capacities.put(cap.node.name, cap);

        LocalOffer offerA = localOffer(agent, cap, c1);
        LocalOffer offerB = localOffer(agent, cap, c2);
        cap.reserveAtomicOffers(List.of(offerA, offerB), agent, 2.0, 2L, 2L);

        AtomicCoverageState winningState = new AtomicCoverageState(app.components, List.of(offerA, offerB));
        invokeMaterializeWinningAtomicReservations(agent, app, winningState);

        assertEquals(2, cap.utilisations.stream()
                .filter(utilisation -> utilisation.state == Utilisation.State.RESERVED && !utilisation.envelopeReservation)
                .count());
        assertTrue(cap.utilisations.stream().noneMatch(utilisation -> utilisation.state == Utilisation.State.RESERVED && utilisation.envelopeReservation));
    }

    @Test
    void freeReservedResources_onlyReleasesReservationsForTargetApplication() throws Exception {
        Component c1 = component("C1");
        Component c2 = component("C2");
        Component c3 = component("C3");
        AgentApplication app = application(c1, c2);

        ResourceAgent agent = createAgent("AgentA");
        Capacity cap = capacity("NodeA", 10.0, 100L, 100L);
        agent.capacities.put(cap.node.name, cap);

        LocalOffer appOffer1 = localOffer(agent, cap, c1);
        LocalOffer appOffer2 = localOffer(agent, cap, c2);
        LocalOffer unrelatedOffer = localOffer(agent, cap, c3);

        cap.reserveCapacity(c1, agent, appOffer1);
        cap.reserveCapacity(c2, agent, appOffer2);
        cap.reserveCapacity(c3, agent, unrelatedOffer);

        invokeFreeReservedResources(agent, app, cap);

        assertEquals(1, cap.utilisations.size());
        assertEquals(Set.of(c3.id), cap.utilisations.stream()
                .map(utilisation -> utilisation.component.id)
                .collect(Collectors.toSet()));
    }

    private static ResourceAgent createAgent(String name) {
        return new ResourceAgent(name, 1.0, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
    }

    private static AgentApplication application(Component... components) {
        AgentApplication app = new AgentApplication();
        app.name = "App-1";
        app.components = new ArrayList<>(List.of(components));
        return app;
    }

    private static Component component(String id) {
        Component component = new Component();
        component.id = id;
        component.requirements = new AgentApplication.ComponentRequirements();
        component.requirements.cpu = 1.0;
        component.requirements.memory = 1L;
        component.requirements.storage = 1L;
        return component;
    }

    private static Capacity capacity(String nodeName, double cpu, long memory, long storage) {
        return new Capacity(createNode(nodeName), cpu, memory, storage);
    }

    private static ComputingAppliance createNode(String name) {
        return new ComputingAppliance(
                Config.createNode(name, 10.0, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                        1, 1, 1, 1, 1, new HashMap<>()),
                new GeoLocation(0, 0), "loc", "provider", false);
    }

    private static LocalOffer localOffer(ResourceAgent agent, Capacity capacity, Component... components) {
        List<ComponentPlacement> placements = List.of(components).stream()
                .map(component -> new ComponentPlacement(component, capacity))
                .toList();
        return new LocalOffer(agent, placements, new LocalMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static void invokeReserveLocalOffers(ResourceAgent owner, List<LocalOffer> localOffers) throws Exception {
        Method method = ResourceAgent.class.getDeclaredMethod("reserveLocalOffers", List.class);
        method.setAccessible(true);
        method.invoke(owner, localOffers);
    }

    private static void invokeGenerateNonAtomicOfferCombinations(ResourceAgent owner,
                                                                List<LocalOffer> localOffers,
                                                                AgentApplication app)
            throws Exception {
        Method method = ResourceAgent.class.getDeclaredMethod("generateNonAtomicOfferCombinations", List.class, AgentApplication.class);
        method.setAccessible(true);
        method.invoke(owner, localOffers, app);
    }

    private static void invokeMaterializeWinningAtomicReservations(ResourceAgent owner,
                                                                 AgentApplication app,
                                                                 AtomicCoverageState winningState)
            throws Exception {
        Method method = ResourceAgent.class.getDeclaredMethod("materializeWinningAtomicReservations", AgentApplication.class, AtomicCoverageState.class);
        method.setAccessible(true);
        method.invoke(owner, app, winningState);
    }

    private static void invokeFreeReservedResources(ResourceAgent owner,
                                                   AgentApplication app,
                                                   Capacity capacity)
            throws Exception {
        Method method = ResourceAgent.class.getDeclaredMethod("freeReservedResources", AgentApplication.class, Capacity.class);
        method.setAccessible(true);
        method.invoke(owner, app, capacity);
    }
}
