package hu.u_szeged.inf.fog.simulator.agent.decision;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.*;
import org.apache.commons.lang3.tuple.Pair;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;

import java.util.*;

public class CBBABasedDecisionMaker extends DecisionMaker {
    public List<Pair<ResourceAgent, Component>> winners = new ArrayList<>();

    @Override
    public void start(AgentApplication app) {
        CBBAResourceAgent.minimumsMaximums();

        app.deploymentStartedTime = Timed.getFireCount();
        app.normalizePriorities();

        new CBBAExecutor(app, 100, this);
    }

    @Override
    public void generateOffers(AgentApplication app) {
        for (Map.Entry<Component, ResourceAgent> entry : CBBASender.winners.entrySet()) {
            //Since agent has all resources from all apps in the winners list,
            // we have to do a check to make sure that the resource belongs to
            // the app that we are currently working on

            if (app.components.contains(entry.getKey())) {
                winners.add(Pair.of(entry.getValue(), entry.getKey()));
            }
        }
        //Winners should now have the tasks for the specific RAs

        //Stream the List -> Pair -> (RA -> Resource) into a Map -> (RA -> Set(Resources))
        Map<ResourceAgent, Set<Component>> agentResourcesMap = new HashMap<>();

        for (Pair<ResourceAgent, Component> pair : winners) {
            agentResourcesMap.putIfAbsent(pair.getLeft(), new HashSet<>());
            agentResourcesMap.get(pair.getLeft()).add(pair.getRight());
        }
        //We have the tasks for the resource agents in the same format as the standard offer generator thing (we only have one offer)

        if (agentResourcesMap.isEmpty()) {
            throw new RuntimeException(app.name + ", empty offer: The application could not be deployed. Either no capacity, or requirements could not be satisfied.");
        }

        app.offers.add(new Offer(agentResourcesMap, app.offers.size()));

        System.out.println("Finished offer: " + app.offers);
        CBBASender.processAppOffer(app);
    }
}
