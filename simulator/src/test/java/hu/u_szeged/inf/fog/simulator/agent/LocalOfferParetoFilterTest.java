package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.LocalMetrics;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.pareto.LocalOfferParetoFilter;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.pareto.ExhaustiveMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalOfferParetoFilterTest {

    @BeforeEach
    void resetGlobalState() {
        ResourceAgent.allResourceAgents.clear();
    }

    @Test
    void filter_emptyInput_returnsEmptyList() {
        List<LocalOffer> result = new LocalOfferParetoFilter().filter(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void filter_sameComponentSet_dominatedOfferIsRemoved() {
        ResourceAgent agent = createAgent("Agent1");
        Component c1 = component("C1");

        LocalOffer dominant = offer(agent, List.of(c1), metrics(0.9, 0.8, 0.2, 0.7, 10, 20, 30, 100));
        LocalOffer dominated = offer(agent, List.of(c1), metrics(0.8, 0.7, 0.3, 0.6, 12, 22, 40, 90));

        List<LocalOffer> result = new LocalOfferParetoFilter().filter(List.of(dominated, dominant));

        assertEquals(1, result.size());
        assertTrue(result.contains(dominant));
    }

    @Test
    void filter_sameComponentSet_tradeoffOffersAreKept() {
        ResourceAgent agent = createAgent("Agent1");
        Component c1 = component("C1");

        LocalOffer betterPerformance = offer(agent, List.of(c1), metrics(0.9, 0.85, 0.35, 0.75, 18, 22, 35, 100));
        LocalOffer cheaper = offer(agent, List.of(c1), metrics(0.8, 0.8, 0.3, 0.7, 10, 18, 30, 90));

        List<LocalOffer> result = new LocalOfferParetoFilter().filter(List.of(betterPerformance, cheaper));

        assertEquals(2, result.size());
        assertTrue(result.contains(betterPerformance));
        assertTrue(result.contains(cheaper));
    }

    @Test
    void filter_differentComponentSets_areComparedSeparately() {
        ResourceAgent agent = createAgent("Agent1");
        Component c1 = component("C1");
        Component c2 = component("C2");

        LocalOffer singleComponent = offer(agent, List.of(c1), metrics(0.7, 0.7, 0.3, 0.7, 20, 30, 40, 80));
        LocalOffer twoComponents = offer(agent, List.of(c1, c2), metrics(1.0, 1.0, 0.1, 1.0, 1, 1, 1, 1000));

        List<LocalOffer> result = new LocalOfferParetoFilter().filter(List.of(singleComponent, twoComponents));

        assertEquals(2, result.size());
        assertTrue(result.contains(singleComponent));
        assertTrue(result.contains(twoComponents));
    }

    @Test
    void filter_epsilonDifferenceDoesNotCreateDominance() {
        ResourceAgent agent = createAgent("Agent1");
        Component c1 = component("C1");

        LocalOffer base = offer(agent, List.of(c1), metrics(0.8, 0.8, 0.3, 0.8, 10, 20, 30, 100));
        LocalOffer almostSame = offer(agent, List.of(c1), metrics(
                0.8 + 5e-10,
                0.8,
                0.3,
                0.8,
                10 - 5e-10,
                20,
                30,
                100));

        List<LocalOffer> result = new LocalOfferParetoFilter().filter(List.of(base, almostSame));

        assertEquals(2, result.size());
        assertTrue(result.contains(base));
        assertTrue(result.contains(almostSame));
    }

    private static ResourceAgent createAgent(String name) {
        return new ResourceAgent(name, 1.0, new ExhaustiveMappingStrategy(), new FloodingMessagingStrategy());
    }

    private static Component component(String id) {
        Component component = new Component();
        component.id = id;
        return component;
    }

    private static LocalOffer offer(ResourceAgent agent, List<Component> components, LocalMetrics metrics) {
        List<ComponentPlacement> placements = components.stream()
                .map(component -> new ComponentPlacement(component, null))
                .toList();
        return new LocalOffer(agent, placements, metrics);
    }

    private static LocalMetrics metrics(double balance, double utilisation, double fragmentation,
                                        double compactness, double cost, double energy,
                                        double latency, double bandwidth) {
        return new LocalMetrics(balance, utilisation, fragmentation, compactness, cost, energy, latency, bandwidth);
    }
}
