package hu.u_szeged.inf.fog.simulator.aco;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.StandardResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.decision.DecentralizedAntBasedDecisionMaker;

import java.util.*;

public class ClusterMessengerApplication extends Timed {

    private final AgentApplication app;

    private final HashMap<StandardResourceAgent, Long> messageLogger;

    public static int clusterMessageCount;

    //The cluster set in WorkflowComputingAppliance
    public HashMap<StandardResourceAgent, TreeSet<String>> clusterMap;

    DecentralizedAntBasedDecisionMaker sender;

    public ClusterMessengerApplication(double[][] globalPheromoneMatrix, ArrayList<StandardResourceAgent> nodes, long freq, DecentralizedAntBasedDecisionMaker sender, AgentApplication app) {
        this.messageLogger = new HashMap<>();
        this.clusterMap = new HashMap<>();
        this.sender = sender;
        this.app = app;

        int[] assignments = new DecentralisedAntOptimiserApplication().assignClusters(globalPheromoneMatrix);
        for (int i = 0; i < nodes.size(); i++) {
            StandardResourceAgent from = nodes.get(i);
            StandardResourceAgent to = nodes.get(nodes.indexOf(nodes.get(assignments[i])));

            //from.cluster.add(from.name);
            //from.cluster.add(to.name);
            clusterMap.computeIfAbsent(from, k -> new TreeSet<>()).add(from.name);
            clusterMap.computeIfAbsent(from, k -> new TreeSet<>()).add(to.name);

            this.sendMsg(from, to);
        }
        subscribe(freq);
    }

    private StandardResourceAgent getRAByName(String name) {
        for (StandardResourceAgent agent : StandardResourceAgent.standardResourceAgents) {
            if (agent.name.equals(name)) {
                return agent;
            }
        }

        return null;
    }
    
    private void sendMsg(StandardResourceAgent from, StandardResourceAgent to) {
        if (from != to) {
            StorageObject message = new StorageObject(UUID.randomUUID().toString(), 2048, false);
            from.hostNode.iaas.repositories.get(0).registerObject(message);
            try {
                from.hostNode.iaas.repositories.get(0)
                    .requestContentDelivery(message.id, to.hostNode.iaas.repositories.get(0), new ConsumptionEventAdapter() {

                        @Override
                        public void conComplete() {         
                            clusterMessageCount++;
                            messageLogger.put(to, Timed.getFireCount());

                            Set<String> onlyInCollectionFrom = new HashSet<>(clusterMap.get(from));
                            onlyInCollectionFrom.removeAll(clusterMap.get(to));

                            Set<String> onlyInCollectionTo = new HashSet<>(clusterMap.get(to));
                            onlyInCollectionTo.removeAll(clusterMap.get(from));
                        
                            //from.cluster.addAll(to.cluster);
                            clusterMap.computeIfAbsent(from, k -> new TreeSet<>()).addAll(clusterMap.getOrDefault(to, new TreeSet<>()));

                            //to.cluster.addAll(from.cluster);
                            clusterMap.computeIfAbsent(to, k -> new TreeSet<>()).addAll(clusterMap.getOrDefault(from, new TreeSet<>()));


                            if (!onlyInCollectionFrom.isEmpty() && !onlyInCollectionTo.isEmpty()) {
                                for (String item : clusterMap.get(to)) {
                                    sendMsg(to, getRAByName(item));
                                }
                            } else if (onlyInCollectionFrom.isEmpty() && !onlyInCollectionTo.isEmpty()) {
                                for (String item : onlyInCollectionTo) {
                                    sendMsg(to, getRAByName(item));
                                }
                            } else if (!onlyInCollectionFrom.isEmpty() && onlyInCollectionTo.isEmpty()) {
                                for (String item : onlyInCollectionFrom) {
                                    sendMsg(to, getRAByName(item));
                                }
                            }
                        }            
                    });
            } catch (NetworkException e) {
                e.printStackTrace();
            }
        }
    }
    
    private boolean checkLastReceived() {
        for (Map.Entry<StandardResourceAgent, Long> entry : messageLogger.entrySet()) {
            Long value = entry.getValue();
            StandardResourceAgent key = entry.getKey();
            System.out.println(key.name + "  "  + value);
            if (Timed.getFireCount() - value < this.getFrequency()) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public void tick(long fires) {
        if (checkLastReceived()) {
            unsubscribe();
            sender.clusterAssignments = createClusters();
            sender.processClusters(app);
        }
    }

    private HashMap<Integer, ArrayList<StandardResourceAgent>> createClusters() {
        HashMap<StandardResourceAgent, ArrayList<StandardResourceAgent>> thisClusterMap = new HashMap<>();
        HashSet<StandardResourceAgent> visitedNodes = new HashSet<>();

        for (StandardResourceAgent agent : StandardResourceAgent.standardResourceAgents) {
            if (!visitedNodes.contains(agent)) {
                ArrayList<StandardResourceAgent> clusterMembers = new ArrayList<>();

                for (StandardResourceAgent otherAgent : StandardResourceAgent.standardResourceAgents) {
                    if (clusterMap.get(agent).contains(otherAgent.name)) {
                        clusterMembers.add(otherAgent);
                    }
                }
                thisClusterMap.put(agent, clusterMembers);
                clusterMembers.remove(agent);
                visitedNodes.addAll(clusterMembers);
            }
        }
        
        HashMap<Integer, ArrayList<StandardResourceAgent>> thisClusterMapWithIds = new HashMap<>();
        int counter = 0;
        for (Map.Entry<StandardResourceAgent, ArrayList<StandardResourceAgent>> entry : thisClusterMap.entrySet()) {
            ArrayList<StandardResourceAgent> value = entry.getValue();
            value.add(entry.getKey());

            thisClusterMapWithIds.put(counter++, value);
        }
        
        return thisClusterMapWithIds;
    }
}
