package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import java.util.ArrayList;
import java.util.UUID;

public class ResourceAgent {

    public static ArrayList<ResourceAgent> agentList = new ArrayList<ResourceAgent>();

    Repository repo;
    
    public int acknowledgement;

    /**
     * Agent's capabilities.
     */
    ArrayList<Constraint> constraints;

    public ResourceAgent(Repository repo, ArrayList<Constraint> constraints) {
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
        
        ArrayList<ResourceAgent> agentsToBeAcknowledged = new ArrayList<>(bm.alreadyVisitedAgents);
        if (bm.demands.size() == 0) {
            System.out.println("A solution found: " + bm.solution + " on this path: " + bm.alreadyVisitedAgents);

            this.decreaseAcknowledgement(agentsToBeAcknowledged, bm.solution, bm.size);
            solutionFound = true;
        }
        if (solutionFound == false && bm.alreadyVisitedAgents.size() == ResourceAgent.agentList.size()) {
            System.out.println("Demands cannot be fulfilled on this path: " + bm.alreadyVisitedAgents);
            this.decreaseAcknowledgement(agentsToBeAcknowledged, null, bm.size);
            deadEnd = true;
        }

        if (solutionFound == false && deadEnd == false) {
            // broadcasting message to the neighbors
            ArrayList<ResourceAgent> neighbors = new ArrayList<>(ResourceAgent.agentList);
            neighbors.removeAll(bm.alreadyVisitedAgents);
            
            this.decreaseAcknowledgement(agentsToBeAcknowledged, null, bm.size);

            for (ResourceAgent agent : neighbors) {
                String name = UUID.randomUUID().toString();
                BroadcastMessage bmsg = new BroadcastMessage(bm, name);
                final long initTime = Timed.getFireCount();
                this.repo.registerObject(bmsg);
                try {
                    this.repo.requestContentDelivery(name, agent.repo, new ConsumptionEventAdapter() {

                        @Override
                        public void conComplete() {
                            agent.increaseAcknowledgement(agentsToBeAcknowledged, bm.size); 
                            repo.deregisterObject(bmsg);
                            agent.broadcast(bmsg, true);
                            BroadcastMessage.totalByteOnNetwork += bm.size;
                            BroadcastMessage.broadcastByteOnNetwork += bm.size;
                            BroadcastMessage.totalTimeOnNetwork += Timed.getFireCount() - initTime;
                            BroadcastMessage.broadcastTimeOnNetwork += Timed.getFireCount() - initTime;
                            BroadcastMessage.totalMessageCount++;
                            BroadcastMessage.broadcastMessageCount++;
                        }
                    });
                } catch (NetworkException e) {
                    e.printStackTrace();
                }
            }
        }
    }    
    
    private void increaseAcknowledgement(ArrayList<ResourceAgent> agentsToBeAcknowledged, long msgSize) {
        //System.out.println(this.repo.getName() + " sends inc. to: " + agentsToBeAcknowledged);
        //for (ResourceAgent agent : agentsToBeAcknowledged) {
            String name = UUID.randomUUID().toString();
            StorageObject so = new StorageObject(name, msgSize / 2, false); 
            this.repo.registerObject(so);
            final long initTime = Timed.getFireCount();
            try {
                
                this.repo.requestContentDelivery(name, agentsToBeAcknowledged.get(0).repo, new ConsumptionEventAdapter() {

                    @Override
                    public void conComplete() {
                        agentsToBeAcknowledged.get(0).acknowledgement++;
                        repo.deregisterObject(so);
                        BroadcastMessage.totalByteOnNetwork += so.size;
                        BroadcastMessage.totalTimeOnNetwork += Timed.getFireCount() - initTime;
                        BroadcastMessage.totalMessageCount++;
                    }
                });
            } catch (NetworkException e) {
                e.printStackTrace();
            }
       // }
    }

    private void decreaseAcknowledgement(ArrayList<ResourceAgent> agentsToBeAcknowledged, ArrayList<Pair> solution, 
            long msgSize) {
        //System.out.println(this.repo.getName() + " sends dec. to: " + agentsToBeAcknowledged);
        //for (ResourceAgent agent : agentsToBeAcknowledged) {
            String name = UUID.randomUUID().toString();
            StorageObject so = new StorageObject(name, msgSize / 2, false); 
            final long initTime = Timed.getFireCount();
            this.repo.registerObject(so);
            try {
                this.repo.requestContentDelivery(name, agentsToBeAcknowledged.get(0).repo, new ConsumptionEventAdapter() {

                    @Override
                    public void conComplete() {
                        agentsToBeAcknowledged.get(0).acknowledgement--;
                        repo.deregisterObject(so);
                        BroadcastMessage.totalTimeOnNetwork += Timed.getFireCount() - initTime;
                        BroadcastMessage.totalMessageCount++;
                        BroadcastMessage.totalByteOnNetwork += so.size;
                        if (solution != null ) {
                            Orchestration.solutions.add(solution);
                            if (agentsToBeAcknowledged.get(0).acknowledgement == 0) {
                                Orchestration.removeDuplicates();
                            }
                        }
                    }
                });
            } catch (NetworkException e) {
                e.printStackTrace();
            }
        //}
    }
    /*
    @Override
    public String toString() {
        return "ResourceAgent [repo=" + repo.getName() + ", constraints=" + constraints + "]";
    }
    */
    
    @Override
    public String toString() {
        return "RA(" + repo.getName() + ")";
    }
}