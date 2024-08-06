package hu.u_szeged.inf.fog.simulator.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;

public class ResourceAgent {

    static ArrayList < ResourceAgent > agentList = new ArrayList < > ();

    Repository repo;
    
    int acknowledgement;

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
        // checking demand fulfillment 
        if (serviceable) {
            bm.checkFulfillment(this);
        } else {
        	bm.alreadyVisitedAgents.add(this); 
        }
        if (bm.demands.size() == 0) {
            System.out.println("A solution found: " + bm.solution + " on this path: " + bm.alreadyVisitedAgents);
            solutionFound = true;
        }
        if (solutionFound == false && bm.alreadyVisitedAgents.size() == ResourceAgent.agentList.size()) {
            System.out.println("Demands cannot be fulfilled on this path: " + bm.alreadyVisitedAgents);
            deadEnd = true;
        }
        
        ArrayList<ResourceAgent> agentsToBeAcknowledged = new ArrayList<>(bm.alreadyVisitedAgents);
    	    	
        if(solutionFound == true || deadEnd == true) {
        	agentsToBeAcknowledged.remove(bm.alreadyVisitedAgents.size()-1);
        	this.decreaseAcknowledgement(agentsToBeAcknowledged);
        }

        if (solutionFound == false && deadEnd == false) {
            // broadcasting message to the neighbors
            ArrayList < ResourceAgent > neighbors = new ArrayList < > (ResourceAgent.agentList);
            neighbors.removeAll(bm.alreadyVisitedAgents);
            this.decreaseAcknowledgement(agentsToBeAcknowledged);
            for (ResourceAgent agent : neighbors) {
                String name = UUID.randomUUID().toString();
                BroadcastMessage bmsg = new BroadcastMessage(bm, name);
                this.repo.registerObject(bmsg);
                try {
                    this.repo.requestContentDelivery(name, agent.repo, new ConsumptionEventAdapter() {

                        @Override
                        public void conComplete() {
                        	increaseAcknowledgemnet(agentsToBeAcknowledged); 
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
    
	private void increaseAcknowledgemnet(ArrayList<ResourceAgent> agentsToBeAcknowledged) {
		for(ResourceAgent agent : agentsToBeAcknowledged) {
			agent.acknowledgement++;
		}
		
	}

    private void decreaseAcknowledgement(ArrayList<ResourceAgent> agentsToBeAcknowledged) {
    	for(ResourceAgent agent : agentsToBeAcknowledged) {
			String name = UUID.randomUUID().toString();
			StorageObject so = new StorageObject(name, 1, false);
			 this.repo.registerObject(so);
			 try {
					this.repo.requestContentDelivery(name, agent.repo, new ConsumptionEventAdapter() {

					    @Override
					    public void conComplete() {				    	
					    	agent.acknowledgement--;
					        repo.deregisterObject(so);
					    }
					});
					
				} catch (NetworkException e) {
					e.printStackTrace();
				}
		}
	}

	@Override
    public String toString() {
        return "RA(" + repo.getName() + ")";
    }

}