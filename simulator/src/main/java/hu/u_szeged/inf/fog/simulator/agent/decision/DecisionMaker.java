package hu.u_szeged.inf.fog.simulator.agent.decision;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.Offer;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

public abstract class DecisionMaker {
    public abstract void deploy(AgentApplication app);

    abstract void generateOffers(AgentApplication app);

    protected void generateUniqueOfferCombinations(List<Pair<ResourceAgent, AgentApplication.Resource>> pairs, AgentApplication app) {
        Set<Set<Pair<ResourceAgent, AgentApplication.Resource>>> uniqueCombinations = new LinkedHashSet<>();

        generateCombinations(pairs, app.resources.size(), uniqueCombinations,
                new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());

        for (Set<Pair<ResourceAgent, AgentApplication.Resource>> combination : uniqueCombinations) {
            Map<ResourceAgent, Set<AgentApplication.Resource>> agentResourcesMap = new HashMap<>();

            for (Pair<ResourceAgent, AgentApplication.Resource> pair : combination) {
                ResourceAgent agent = pair.getLeft();
                AgentApplication.Resource resource = pair.getRight();

                agentResourcesMap.putIfAbsent(agent, new LinkedHashSet<>());
                agentResourcesMap.get(agent).add(resource);
            }

            app.offers.add(new Offer(agentResourcesMap, app.offers.size()));
        }
    }

    protected void generateCombinations(List<Pair<ResourceAgent, AgentApplication.Resource>> pairs, int resourceCount,
                                      Set<Set<Pair<ResourceAgent, AgentApplication.Resource>>> uniqueCombinations,
                                      Set<Pair<ResourceAgent, AgentApplication.Resource>> currentCombination,
                                      Set<AgentApplication.Resource> includedResources,
                                      Set<String> seenStates) {

        if (includedResources.size() == resourceCount) {

            uniqueCombinations.add(new LinkedHashSet<>(currentCombination));
            return;
        }

        String stateKey = includedResources.stream()
                .map(r -> r.name)
                .sorted()
                .collect(Collectors.joining(","));
        if (!seenStates.add(stateKey)) {
            return;
        }

        for (Pair<ResourceAgent, AgentApplication.Resource> pair : pairs) {
            if (!currentCombination.contains(pair) && !includedResources.contains(pair.getRight())) {
                currentCombination.add(pair);
                includedResources.add(pair.getRight());

                generateCombinations(pairs, resourceCount, uniqueCombinations, currentCombination, includedResources, seenStates);

                currentCombination.remove(pair);
                includedResources.remove(pair.getRight());
            }
        }
    }
}
