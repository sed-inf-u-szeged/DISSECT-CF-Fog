package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork.partition;

import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.util.*;

public class HopBasedPartitionManager implements PartitionStrategy {

    private int numberOfHops;
    private ComputingAppliance startNode;


    public HopBasedPartitionManager(int numberOfHops) {
        this.numberOfHops = numberOfHops;
    }

    public int getNumberOfHops() {
        return numberOfHops;
    }

    public void setNumberOfHops(int numberOfHops) {
        this.numberOfHops = numberOfHops;
    }

    public ComputingAppliance getStartNode() {
        return startNode;
    }

    public void setStartNode(ComputingAppliance startNode) {
        this.startNode = startNode;
    }

    @Override
    public List<ComputingAppliance> selectValidators(List<ComputingAppliance> nodes) {
        if (startNode == null) {
            throw new RuntimeException("startNode is null");
        }
        if (numberOfHops < 0) {
            throw new RuntimeException("startNode is null");
        }
        if (numberOfHops == 0) {
            return List.of(startNode);
        }
        Set<ComputingAppliance> selectedNodes = new HashSet<>();
        int counter = 0;
        Stack<ComputingAppliance> toDoNodes = new Stack<>();
        toDoNodes.push(startNode);
        while (counter < numberOfHops || toDoNodes.isEmpty()) {
            ComputingAppliance actual = toDoNodes.pop();
            selectedNodes.addAll(actual.neighbors);
            for (ComputingAppliance node : actual.neighbors) {
                if(toDoNodes.contains(node)){
                    continue;
                }
                toDoNodes.push(node);
            }
            counter++;
        }
        return List.of();
    }
}
