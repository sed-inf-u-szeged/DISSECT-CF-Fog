package hu.u_szeged.inf.fog.simulator.agent;

import java.util.ArrayList;
import java.util.UUID;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

public class ResourceAgent {

    static ArrayList < ResourceAgent > agentList = new ArrayList < > ();

    Repository repo;

    /**
     * Agent's capabilities.
     */
    ArrayList < Constraint > constraints;

    public ResourceAgent(Repository repo, ArrayList < Constraint > constraints) {
        this.repo = repo;
        this.constraints = constraints;
        try {
            this.repo.setState(NetworkNode.State.RUNNING);
        } catch (NetworkException e) {
            e.printStackTrace();
        }
        ResourceAgent.agentList.add(this);
    }

    public void broadcast(BroadcastMessage bm, boolean serviceable) {
        boolean solutionFound = false;
        boolean deadEnd = false;
        // checking demand fulfillment (can the first RA used as well? - currently yes!)
        if (serviceable) {
            bm.checkFulfillment(this);
        }
        // if demand == empty then solution found
        //System.out.println(bm.demands.size() == 0);
        if (bm.demands.size() == 0) {
            System.out.println("Solution found:" + bm.solution + " " + bm.id);
            solutionFound = true;
        }
        // else if agentList.size == alreadyvisitedagents then demand cannot be fulfilled
        //System.out.println(bm.alreadyVisitedAgents.size() == ResourceAgent.agentList.size());
        if (bm.alreadyVisitedAgents.size() == ResourceAgent.agentList.size()) {
            System.out.println("Solution cannot be set for this path: " + bm.alreadyVisitedAgents);
            deadEnd = true;
        }

        if (solutionFound == false && deadEnd == false) {
            // broadcasting message to the neighbors
            ArrayList < ResourceAgent > neighbors = new ArrayList < > (ResourceAgent.agentList);
            neighbors.removeAll(bm.alreadyVisitedAgents);
            //System.out.println(neighbors);
            for (ResourceAgent agent: neighbors) {
                String name = UUID.randomUUID().toString();
                BroadcastMessage bmsg = new BroadcastMessage(bm, name);
                this.repo.registerObject(bmsg);
                try {
                    this.repo.requestContentDelivery(name, agent.repo, new ConsumptionEventAdapter() {

                        @Override
                        public void conComplete() {
                            repo.deregisterObject(bmsg);
                            agent.broadcast(bmsg, true);
                        }
                    });
                } catch (NetworkException e) {

                    e.printStackTrace();
                }
            }
        }

    }

    @Override
    public String toString() {
        return "ResourceAgent: " + repo.getName();
    }

}