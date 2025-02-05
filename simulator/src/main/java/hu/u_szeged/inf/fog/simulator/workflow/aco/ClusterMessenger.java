package hu.u_szeged.inf.fog.simulator.workflow.aco;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.WorkflowComputingAppliance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ClusterMessenger extends Timed {
    
    private HashMap<WorkflowComputingAppliance, Long> messageLogger;
    
    public static int clusterMessageCount;
    
    public HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> clusterAssignments;

    public ClusterMessenger(double[][] globalPheromoneMatrix, ArrayList<ComputingAppliance> nodes, long freq) {
        this.messageLogger = new HashMap<>();
        int[] assignments = DecentralisedAntOptimiser.assignClusters(globalPheromoneMatrix);
        for (int i = 0; i < nodes.size(); i++) {
            WorkflowComputingAppliance from = (WorkflowComputingAppliance) nodes.get(i);
            WorkflowComputingAppliance to = (WorkflowComputingAppliance) nodes.get(nodes.indexOf(nodes.get(assignments[i])));
            from.cluster.add(from.name);
            from.cluster.add(to.name);
            this.sendMsg(from, to);
        }
        subscribe(freq);
    }
    
    private void sendMsg(WorkflowComputingAppliance from, WorkflowComputingAppliance to) {
        if (from != to) {
            StorageObject message = new StorageObject(UUID.randomUUID().toString(), 2048, false);
            from.iaas.repositories.get(0).registerObject(message);
            try {
                from.iaas.repositories.get(0)
                    .requestContentDelivery(message.id, to.iaas.repositories.get(0), new ConsumptionEventAdapter() {

                        @Override
                        public void conComplete() {         
                            clusterMessageCount++;
                            messageLogger.put(to, Timed.getFireCount());
                            Set<String> onlyInCollectionFrom = new HashSet<>(from.cluster);
                            onlyInCollectionFrom.removeAll(to.cluster); 
                            Set<String> onlyInCollectionTo = new HashSet<>(to.cluster);
                            onlyInCollectionTo.removeAll(from.cluster);
                        
                            from.cluster.addAll(to.cluster);
                            to.cluster.addAll(from.cluster);
                        
                            if (!onlyInCollectionFrom.isEmpty() && !onlyInCollectionTo.isEmpty()) {
                                for (String item : to.cluster) {
                                    sendMsg(to, (WorkflowComputingAppliance) WorkflowComputingAppliance.getNodeByName(item));
                                }
                            } else if (onlyInCollectionFrom.isEmpty() && !onlyInCollectionTo.isEmpty()) {
                                for (String item : onlyInCollectionFrom) {
                                    sendMsg(to, (WorkflowComputingAppliance) WorkflowComputingAppliance.getNodeByName(item));
                                }
                            } else if (!onlyInCollectionFrom.isEmpty() && onlyInCollectionTo.isEmpty()) {
                                for (String item : onlyInCollectionTo) {
                                    sendMsg(to, (WorkflowComputingAppliance) WorkflowComputingAppliance.getNodeByName(item));
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
        for (Map.Entry<WorkflowComputingAppliance, Long> entry : messageLogger.entrySet()) {
            Long value = entry.getValue();
            WorkflowComputingAppliance key = entry.getKey();
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
            this.clusterAssignments = createClusters();
        }
    }

    private HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> createClusters() {
        HashMap<WorkflowComputingAppliance, ArrayList<WorkflowComputingAppliance>> clusterMap = new HashMap<>();
        HashSet<ComputingAppliance> visitedNodes = new HashSet<>();
        
        for (ComputingAppliance appliance : ComputingAppliance.allComputingAppliances) {
            if (!visitedNodes.contains(appliance)) {
                ArrayList<WorkflowComputingAppliance> clusterMembers = new ArrayList<>();

                for (ComputingAppliance otherAppliance : ComputingAppliance.allComputingAppliances) {
                    WorkflowComputingAppliance wca = (WorkflowComputingAppliance) appliance;
                    if (wca.cluster.contains(otherAppliance.name)) {
                        clusterMembers.add((WorkflowComputingAppliance) otherAppliance);
                    }
                }
                clusterMap.put((WorkflowComputingAppliance) appliance, clusterMembers);
                clusterMembers.remove(appliance);
                visitedNodes.addAll(clusterMembers);
            }
        }
        
        return clusterMap;
    }
}
