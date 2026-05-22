package hu.u_szeged.inf.fog.simulator.agent.decision;

import hu.u_szeged.inf.fog.simulator.agent.*;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

public abstract class DecisionMaker {
    public CBBAResourceAgent CBBASender;
    public StandardResourceAgent standardSender;

    public abstract void start(AgentApplication app);

    protected abstract void generateOffers(AgentApplication app);

    protected void generateUniqueOfferCombinations(List<Pair<ResourceAgent, Component>> pairs, AgentApplication app) {
        Set<Set<Pair<ResourceAgent, Component>>> uniqueCombinations = new LinkedHashSet<>();

        generateCombinations(pairs, app.components.size(), uniqueCombinations,
                new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());

        for (Set<Pair<ResourceAgent, Component>> combination : uniqueCombinations) {
            Map<ResourceAgent, Set<Component>> agentResourcesMap = new HashMap<>();

            for (Pair<ResourceAgent, Component> pair : combination) {
                ResourceAgent agent = pair.getLeft();
                Component component = pair.getRight();

                agentResourcesMap.putIfAbsent(agent, new LinkedHashSet<>());
                agentResourcesMap.get(agent).add(component);
            }

            app.offers.add(new Offer(agentResourcesMap, app.offers.size()));
        }
    }

    protected void generateCombinations(List<Pair<ResourceAgent, Component>> pairs, int componentCount,
                                        Set<Set<Pair<ResourceAgent, Component>>> uniqueCombinations,
                                        Set<Pair<ResourceAgent, Component>> currentCombination,
                                        Set<Component> includedComponents,
                                        Set<String> seenStates) {

        if (includedComponents.size() == componentCount) {
            uniqueCombinations.add(new LinkedHashSet<>(currentCombination));
            return;
        }

        String stateKey = includedComponents.stream()
                .map(r -> r.id)
                .sorted()
                .collect(Collectors.joining(","));
        if (!seenStates.add(stateKey)) {
            return;
        }

        for (Pair<ResourceAgent, AgentApplication.Component> pair : pairs) {
            if (!currentCombination.contains(pair) && !includedComponents.contains(pair.getRight())) {
                currentCombination.add(pair);
                includedComponents.add(pair.getRight());

                generateCombinations(pairs, componentCount, uniqueCombinations, currentCombination, includedComponents, seenStates);

                currentCombination.remove(pair);
                includedComponents.remove(pair.getRight());
            }
        }
    }
}
