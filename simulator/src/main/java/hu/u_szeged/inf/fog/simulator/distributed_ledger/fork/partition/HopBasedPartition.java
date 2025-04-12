package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork.partition;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.node.NetworkVisualiser;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static hu.u_szeged.inf.fog.simulator.node.NetworkGenerator.removeConnections;
import static hu.u_szeged.inf.fog.simulator.node.NetworkGenerator.smallWorldNetworkGenerator;

/**
 * HopBasedPartition class implements the PartitionStrategy interface.
 * It partitions nodes based on the number of hops from a start node.
 */
public class HopBasedPartition implements PartitionStrategy {

    private final int numberOfHops;
    private ComputingAppliance startNode;
    private boolean isDebugMode = false;

    /**
     * Constructor for HopBasedPartition.
     *
     * @param numberOfHops the number of hops to consider for partitioning
     */
    public HopBasedPartition(int numberOfHops) {
        this.numberOfHops = numberOfHops;
    }

    /**
     * Gets the number of hops.
     *
     * @return the number of hops
     */
    public int getNumberOfHops() {
        return numberOfHops;
    }

    /**
     * Gets the start node.
     *
     * @return the start node
     */
    public ComputingAppliance getStartNode() {
        return startNode;
    }

    /**
     * Sets the start node.
     *
     * @param startNode the start node to set
     */
    public void setStartNode(ComputingAppliance startNode) {
        this.startNode = startNode;
    }

    /**
     * Checks if debug mode is enabled.
     *
     * @return true if debug mode is enabled, false otherwise
     */
    public boolean isDebugMode() {
        return isDebugMode;
    }

    /**
     * Sets the debug mode.
     *
     * @param debugMode the debug mode to set
     */
    public void setDebugMode(boolean debugMode) {
        isDebugMode = debugMode;
    }

    /**
     * Selects nodes within the specified number of hops from the start node.
     *
     * @param nodes the list of nodes to select from
     * @return the list of selected nodes
     */
    @Override
    public List<ComputingAppliance> selectNodes(List<ComputingAppliance> nodes) {
        if (startNode == null) {
            throw new IllegalArgumentException("startNode is null");
        }
        if (numberOfHops < 0) {
            throw new IllegalArgumentException("numberOfHops must be >= 0");
        }
        if (numberOfHops == 0) {
            return List.of(startNode);
        }

        Set<ComputingAppliance> selectedNodes = new HashSet<>();
        selectedNodes.add(startNode);
        addNeighborNodes(startNode, selectedNodes, 0);

        return new ArrayList<>(selectedNodes);
    }

    /**
     * Adds neighbor nodes recursively up to the specified number of hops.
     *
     * @param node          the current node
     * @param selectedNodes the set of selected nodes
     * @param counter       the current hop count
     */
    private void addNeighborNodes(ComputingAppliance node, Set<ComputingAppliance> selectedNodes, int counter) {
        selectedNodes.addAll(node.neighbors);
        if (isDebugMode) {
            SimLogger.logRun("counter: " + counter);
            SimLogger.logRun("addNeighborNodes: " + node.name);
            for (ComputingAppliance nodes : selectedNodes) {
                SimLogger.logRun("    selectedNode:  " + nodes.name);
            }
        }

        counter++;
        if (counter >= numberOfHops) {
            return;
        }
        for (ComputingAppliance neighbor : node.neighbors) {
            addNeighborNodes(neighbor, selectedNodes, counter);
        }
    }

    /**
     * Main method for testing the HopBasedPartition class.
     *
     * @param args command line arguments
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
        ArrayList<ComputingAppliance> nodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ComputingAppliance cloud = new ComputingAppliance(ScenarioBase.resourcePath + "LPDS_original.xml", "cloud" + i, new GeoLocation(47.45, 21.3), 0);
            nodes.add(cloud);
        }
        smallWorldNetworkGenerator(nodes, 4, 0.3, 30, 50);
        HopBasedPartition hop = new HopBasedPartition(2);
        hop.setStartNode(nodes.get(0));

        ComputingAppliance.allComputingAppliances = new ArrayList<>(hop.selectNodes(nodes));

        List<Pair<ComputingAppliance, ComputingAppliance>> connectionsToRemove = new ArrayList<>();

        for (ComputingAppliance a : ComputingAppliance.allComputingAppliances) {
            for (ComputingAppliance b : new ArrayList<>(a.neighbors)) {
                if (!ComputingAppliance.allComputingAppliances.contains(b)) {
                    connectionsToRemove.add(Pair.of(a, b));
                    connectionsToRemove.add(Pair.of(b, a));
                }
            }
        }
        removeConnections(connectionsToRemove);
        NetworkVisualiser.exportGraphToHtml(ScenarioBase.resultDirectory + File.separator + "graph_after.html");
    }
}