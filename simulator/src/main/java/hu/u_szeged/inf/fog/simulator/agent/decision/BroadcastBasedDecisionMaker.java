package hu.u_szeged.inf.fog.simulator.agent.decision;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class BroadcastBasedDecisionMaker extends DecisionMaker {

    @Override
    public void start(AgentApplication app) {
        this.generateOffers(app);
    }

    @Override
    protected void generateOffers(AgentApplication app) {
        List<Pair<ResourceAgent, Component>> agentResourcePairs = new ArrayList<>();

        //offer generating agents is populated by the message handler (which adds everything),
        // this used to be hard coded from standardResourceAgent/cbbaResourceAgent
        app.offerGeneratingAgents.add(standardSender); //this was 'this' from ResourceAgent, changed to standardSender, CBBAs cannot use this
        for (ResourceAgent agent : app.offerGeneratingAgents) {
            agentResourcePairs.addAll(agent.agentStrategy.canFulfill(agent, app.components));
        }

        //agentResourcePairs.forEach(p ->
        //        System.out.println(
        //                "Agent: " + p.getLeft().name +
        //                        " | Resource: " + p.getRight().id
        //        )
        //);

        generateUniqueOfferCombinations(agentResourcePairs, app);

        standardSender.processAppOffer(app);

        // System.out.println("Offers for: " + app.name);
        // for (Offer o : app.offers) {
        //     System.out.println(o);
        // }
    }
}
