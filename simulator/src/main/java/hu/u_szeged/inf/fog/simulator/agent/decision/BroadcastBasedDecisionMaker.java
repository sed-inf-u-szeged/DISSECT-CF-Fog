package hu.u_szeged.inf.fog.simulator.agent.decision;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class BroadcastBasedDecisionMaker extends DecisionMaker {

    @Override
    public void deploy(AgentApplication app) {
        this.generateOffers(app);
    }

    @Override
    void generateOffers(AgentApplication app) {
        List<Pair<ResourceAgent, AgentApplication.Resource>> agentResourcePairs = new ArrayList<>();

        for (ResourceAgent agent : ResourceAgent.resourceAgents) {
            agentResourcePairs.addAll(agent.agentStrategy.canFulfill(agent, app.resources));
        }

        /*
        for (Pair<ResourceAgent, Resource> pair : agentResourcePairs) {
            System.out.println(pair.getLeft().name + " " + pair.getRight().name);
        }
        */

        generateUniqueOfferCombinations(agentResourcePairs, app);

        /* TODO: only for debugging, needs to be deleted
        System.out.println(app.name);
        for (Offer o : app.offers) {
            System.out.println(o);
        }
        */
    }
}
