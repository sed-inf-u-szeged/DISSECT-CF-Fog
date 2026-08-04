package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.FirstFitMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceAgentCombinationGenerationTest {

    @BeforeEach
    void resetGlobalState() {
        ResourceAgent.allResourceAgents.clear();
    }

    @Test
    void generateCombinationsFindsAllPossibleAssignments() throws Exception {
        ResourceAgent agentA = createAgent("AgentA");
        ResourceAgent agentB = createAgent("AgentB");

        Component c1 = createComponent("C1");
        Component c2 = createComponent("C2");
        Component c3 = createComponent("C3");
        List<Component> orderedComponents = List.of(c1, c2, c3);

        List<Pair<ResourceAgent, Component>> pairs = List.of(
                Pair.of(agentA, c1), Pair.of(agentB, c1),
                Pair.of(agentA, c2), Pair.of(agentB, c2),
                Pair.of(agentA, c3), Pair.of(agentB, c3)
        );

        Set<Set<Pair<ResourceAgent, Component>>> uniqueCombinations = new LinkedHashSet<>();
        invokeGenerateCombinations(agentA, pairs, orderedComponents.size(), uniqueCombinations);

        assertEquals(8, uniqueCombinations.size());

        Set<String> actualSignatures = uniqueCombinations.stream()
                .map(combination -> combinationSignature(combination, orderedComponents))
                .collect(Collectors.toSet());

        assertEquals(expectedSignatures(orderedComponents, List.of(agentA, agentB)), actualSignatures);
    }

    @Test
    void generateUniqueOfferCombinationsCreatesAnOfferForEachPossibleAssignment() throws Exception {
        ResourceAgent agentA = createAgent("AgentA");
        ResourceAgent agentB = createAgent("AgentB");

        Component c1 = createComponent("C1");
        Component c2 = createComponent("C2");
        Component c3 = createComponent("C3");
        List<Component> orderedComponents = List.of(c1, c2, c3);

        List<Pair<ResourceAgent, Component>> pairs = List.of(
                Pair.of(agentA, c1), Pair.of(agentB, c1),
                Pair.of(agentA, c2), Pair.of(agentB, c2),
                Pair.of(agentA, c3), Pair.of(agentB, c3)
        );

        AgentApplication app = new AgentApplication();
        app.components = new ArrayList<>(orderedComponents);

        invokeGenerateUniqueOfferCombinations(agentA, pairs, app);

        assertEquals(8, app.offers.size());
        assertTrue(app.offers.stream().allMatch(offer -> offer.agentComponentsMap.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet())
                .equals(new LinkedHashSet<>(orderedComponents))));

        Set<String> actualSignatures = app.offers.stream()
                .map(offer -> offerSignature(offer, orderedComponents))
                .collect(Collectors.toSet());

        assertEquals(expectedSignatures(orderedComponents, List.of(agentA, agentB)), actualSignatures);
    }

    private static ResourceAgent createAgent(String name) {
        return new ResourceAgent(name, 1, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
    }

    private static Component createComponent(String id) {
        Component component = new Component();
        component.id = id;
        return component;
    }

    private static void invokeGenerateUniqueOfferCombinations(ResourceAgent owner,
                                                              List<Pair<ResourceAgent, Component>> pairs,
                                                              AgentApplication app)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = ResourceAgent.class.getDeclaredMethod("generateUniqueOfferCombinations", List.class, AgentApplication.class);
        method.setAccessible(true);
        method.invoke(owner, pairs, app);
    }

    private static void invokeGenerateCombinations(ResourceAgent owner,
                                                   List<Pair<ResourceAgent, Component>> pairs,
                                                   int componentCount,
                                                   Set<Set<Pair<ResourceAgent, Component>>> uniqueCombinations)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = ResourceAgent.class.getDeclaredMethod(
                "generateCombinations",
                List.class,
                int.class,
                Set.class,
                Set.class,
                Set.class,
                Set.class,
                AtomicBoolean.class
        );
        method.setAccessible(true);
        method.invoke(
                owner,
                pairs,
                componentCount,
                uniqueCombinations,
                new LinkedHashSet<Pair<ResourceAgent, Component>>(),
                new LinkedHashSet<Component>(),
                new LinkedHashSet<String>(),
                new AtomicBoolean(false)
        );
    }

    private static Set<String> expectedSignatures(List<Component> orderedComponents, List<ResourceAgent> agents) {
        int possibleCombinationCount = (int) Math.pow(agents.size(), orderedComponents.size());
        Set<String> signatures = new LinkedHashSet<>();

        for (int i = 0; i < possibleCombinationCount; i++) {
            int value = i;
            StringBuilder sb = new StringBuilder();
            for (int componentIndex = 0; componentIndex < orderedComponents.size(); componentIndex++) {
                Component component = orderedComponents.get(componentIndex);
                ResourceAgent selectedAgent = agents.get(value % agents.size());
                value = value / agents.size();
                if (componentIndex > 0) {
                    sb.append("|");
                }
                sb.append(component.id).append("=").append(selectedAgent.name);
            }
            signatures.add(sb.toString());
        }

        return signatures;
    }

    private static String combinationSignature(Set<Pair<ResourceAgent, Component>> combination,
                                               List<Component> orderedComponents) {
        Map<Component, ResourceAgent> assignment = combination.stream()
                .collect(Collectors.toMap(Pair::getRight, Pair::getLeft));
        return orderedComponents.stream()
                .map(component -> component.id + "=" + assignment.get(component).name)
                .collect(Collectors.joining("|"));
    }

    private static String offerSignature(Offer offer, List<Component> orderedComponents) {
        Map<Component, ResourceAgent> assignment = offer.agentComponentsMap.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(component -> Map.entry(component, entry.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return orderedComponents.stream()
                .map(component -> component.id + "=" + assignment.get(component).name)
                .collect(Collectors.joining("|"));
    }
}
