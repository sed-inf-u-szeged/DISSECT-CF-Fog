package hu.u_szeged.inf.fog.simulator.agent;

import java.util.ArrayList;
import java.util.UUID;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import hu.u_szeged.inf.fog.simulator.workflow.DecentralizedWorkflowExecutor;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

public class ResourceAgent {

    static ArrayList<ResourceAgent> agentList = new ArrayList<>();

    Repository repo;

    /**
     * Agent's capabilities.
     */
    ArrayList<Constraint> constraints;

    public ResourceAgent(Repository repo, ArrayList<Constraint> constraints){
    	this.repo = repo;
        this.constraints = constraints;
        ResourceAgent.agentList.add(this);
    }
    
	public void broadcast(BroadcastMessage bm, boolean serviceable) {
		
		// checking demand fulfillment (can the first RA used as well? - currently yes!)
		if(serviceable) {
			bm.checkFulfillment(this);
		}
		// if demand == empty then solution found
		if(bm.demands.size() == 0) {
			System.out.println("Solution found:");
		}
		// else if neighbors.size == alreadyvisitedagents then demand cannot be fulfilled
		if(bm.alreadyVisitedAgents.size() == ResourceAgent.agentList.size()) {
			System.out.println("Solution cannot be set for this path:");
		}
		// broadcasting message to the neighbors
		ArrayList<ResourceAgent> neighbors = new ArrayList<>(ResourceAgent.agentList);
		neighbors.removeAll(bm.alreadyVisitedAgents);
		for(ResourceAgent agent : neighbors) {
			String name = UUID.randomUUID().toString();
			BroadcastMessage bmsg = new BroadcastMessage(bm, name);
			this.repo.registerObject(bmsg);
			try {
				this.repo.requestContentDelivery("bm", repo, new ConsumptionEventAdapter() {
					
					@Override
                    public void conComplete() {
						agent.broadcast(bmsg, serviceable);
                    }
				});
			} catch (NetworkException e) {

				e.printStackTrace();
			}
			
		}
	}
	
	
       
}