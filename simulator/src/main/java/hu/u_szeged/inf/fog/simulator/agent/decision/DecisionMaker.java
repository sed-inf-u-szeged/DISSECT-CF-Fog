package hu.u_szeged.inf.fog.simulator.agent.decision;

import hu.u_szeged.inf.fog.simulator.agent.*;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

public abstract class DecisionMaker {
    public CBBAResourceAgent CBBASender;
    public StandardResourceAgent standardSender;

    public abstract void start(AgentApplication app);

    protected abstract void generateOffers(AgentApplication app);

    protected void generateUniqueOfferCombinations(List<Pair<ResourceAgent, Resource>> pairs, AgentApplication app) {
        Set<Set<Pair<ResourceAgent, Resource>>> uniqueCombinations = new LinkedHashSet<>();

        generateCombinations(pairs, app.resources.size(), uniqueCombinations,
                new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());

        for (Set<Pair<ResourceAgent, Resource>> combination : uniqueCombinations) {
            Map<ResourceAgent, Set<Resource>> agentResourcesMap = new HashMap<>();

            for (Pair<ResourceAgent, Resource> pair : combination) {
                ResourceAgent agent = pair.getLeft();
                Resource resource = pair.getRight();

                agentResourcesMap.putIfAbsent(agent, new LinkedHashSet<>());
                agentResourcesMap.get(agent).add(resource);
            }

            app.offers.add(new Offer(agentResourcesMap, app.offers.size()));
        }
    }

    protected void generateCombinations(List<Pair<ResourceAgent, Resource>> pairs, int resourceCount,
                                      Set<Set<Pair<ResourceAgent, Resource>>> uniqueCombinations,
                                      Set<Pair<ResourceAgent, Resource>> currentCombination,
                                      Set<Resource> includedResources,
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

        for (Pair<ResourceAgent, Resource> pair : pairs) {
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
