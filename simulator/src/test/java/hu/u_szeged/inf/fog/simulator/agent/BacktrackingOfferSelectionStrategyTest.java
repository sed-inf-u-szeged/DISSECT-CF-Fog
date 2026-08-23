package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.ComponentRequirements;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.FirstFitMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.LocalMetrics;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.selection.BacktrackingOfferSelectionStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktrackingOfferSelectionStrategyTest {

    @BeforeEach
    void resetGlobalState() {
        ResourceAgent.allResourceAgents.clear();
        ComputingAppliance.allComputingAppliances.clear();
        AgentApplication.allAgentApplications.clear();
    }

    @Test
    void selectOffers_whenOnlyFirstOfferEnabled_returnsSingleFeasibleOffer() {
        Component c1 = component("C1", 1.0, 1L, 1L);
        Component c2 = component("C2", 1.0, 1L, 1L);
        AgentApplication app = application(c1, c2);

        ResourceAgent agentA = createAgent("AgentA", 1.0);
        ResourceAgent agentB = createAgent("AgentB", 2.0);

        Capacity capA = capacity("NodeA", "AWS", "eu-west", false, 10.0, 100L, 100L);
        Capacity capB = capacity("NodeB", "Azure", "eu-west", false, 10.0, 100L, 100L);
        agentA.capacities.put(capA.node.name, capA);
        agentB.capacities.put(capB.node.name, capB);

        List<LocalOffer> localOffers = List.of(
                localOffer(agentA, capA, c1, c2),
                localOffer(agentB, capB, c1, c2)
        );

        List<Offer> offers = new BacktrackingOfferSelectionStrategy(true).selectOffers(localOffers, app);

        assertEquals(1, offers.size());
        assertEquals(1L, app.globalCoverageEvaluationCount);
        assertEquals(Set.of("C1", "C2"), offerComponentIds(offers.get(0)));
    }

    @Test
    void selectOffers_whenOnlyFirstOfferDisabled_findsEveryAssignmentAsUniqueSignature() {
        Component c1 = component("C1", 1.0, 1L, 1L);
        Component c2 = component("C2", 1.0, 1L, 1L);
        AgentApplication app = application(c1, c2);

        ResourceAgent agentA = createAgent("AgentA", 1.0);
        ResourceAgent agentB = createAgent("AgentB", 2.0);

        Capacity capA = capacity("NodeA", "AWS", "eu-west", false, 10.0, 100L, 100L);
        Capacity capB = capacity("NodeB", "Azure", "eu-west", false, 10.0, 100L, 100L);
        agentA.capacities.put(capA.node.name, capA);
        agentB.capacities.put(capB.node.name, capB);

        List<LocalOffer> localOffers = List.of(
                localOffer(agentA, capA, c1, c2),
                localOffer(agentB, capB, c1, c2)
        );

        List<Offer> offers = new BacktrackingOfferSelectionStrategy(false).selectOffers(localOffers, app);

        assertEquals(4, offers.size());
        assertEquals(4L, app.globalCoverageEvaluationCount);
        assertEquals(4, offers.stream().map(this::offerSignature).collect(Collectors.toSet()).size());
    }

    @Test
    void selectOffers_withProviderCountConstraint_filtersHardRequirementViolations() {
        Component c1 = component("C1", 1.0, 1L, 1L);
        Component c2 = component("C2", 1.0, 1L, 1L);
        AgentApplication app = application(c1, c2);
        app.maxProviderCount = 1;

        ResourceAgent agentA = createAgent("AgentA", 1.0);
        ResourceAgent agentB = createAgent("AgentB", 1.0);

        Capacity capA = capacity("NodeA", "AWS", "eu-west", false, 10.0, 100L, 100L);
        Capacity capB = capacity("NodeB", "Azure", "eu-west", false, 10.0, 100L, 100L);
        agentA.capacities.put(capA.node.name, capA);
        agentB.capacities.put(capB.node.name, capB);

        List<LocalOffer> localOffers = List.of(
                localOffer(agentA, capA, c1, c2),
                localOffer(agentB, capB, c1, c2)
        );

        List<Offer> offers = new BacktrackingOfferSelectionStrategy(false).selectOffers(localOffers, app);

        assertEquals(2, offers.size());
        assertEquals(4L, app.globalCoverageEvaluationCount);
        assertTrue(offers.stream().allMatch(offer -> offer.metrics.providerCount == 1));
    }

    @Test
    void selectOffers_whenCoverageIsImpossible_returnsNoOffer() {
        Component c1 = component("C1", 1.0, 1L, 1L);
        Component c2 = component("C2", 1.0, 1L, 1L);
        AgentApplication app = application(c1, c2);

        ResourceAgent agent = createAgent("AgentA", 1.0);
        Capacity cap = capacity("NodeA", "AWS", "eu-west", false, 10.0, 100L, 100L);
        agent.capacities.put(cap.node.name, cap);

        List<LocalOffer> localOffers = List.of(localOffer(agent, cap, c1));

        List<Offer> offers = new BacktrackingOfferSelectionStrategy(false).selectOffers(localOffers, app);

        assertTrue(offers.isEmpty());
        assertEquals(0L, app.globalCoverageEvaluationCount);
    }

    private static ResourceAgent createAgent(String name, double hourlyPrice) {
        return new ResourceAgent(name, hourlyPrice, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
    }

    private static Capacity capacity(
            String nodeName,
            String provider,
            String location,
            boolean edge,
            double cpu,
            long memory,
            long storage) {
        return new Capacity(createNode(nodeName, provider, location, edge), cpu, memory, storage);
    }

    private static ComputingAppliance createNode(String name, String provider, String location, boolean edge) {
        return new ComputingAppliance(
                Config.createNode(name, 10.0, 10 * ScenarioBase.GB_IN_BYTE, 10 * ScenarioBase.GB_IN_BYTE,
                        1, 1, 1, 1, 1, new HashMap<>()),
                new GeoLocation(0, 0), location, provider, edge);
    }

    private static LocalOffer localOffer(ResourceAgent agent, Capacity capacity, Component... components) {
        List<ComponentPlacement> placements = List.of(components).stream()
                .map(component -> new ComponentPlacement(component, capacity))
                .toList();
        return new LocalOffer(agent, placements, new LocalMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static AgentApplication application(Component... components) {
        AgentApplication app = new AgentApplication();
        app.name = "App-1";
        app.components = List.of(components);
        return app;
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

    private static Set<String> offerComponentIds(Offer offer) {
        return offer.selectedPlacements.stream()
                .map(placement -> placement.component.id)
                .collect(Collectors.toSet());
    }

    private String offerSignature(Offer offer) {
        return offer.selectedPlacements.stream()
                .map(placement -> placement.component.id + "->" + placement.capacity.node.name)
                .sorted()
                .collect(Collectors.joining("|"));
    }
}
