package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork.partition;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.io.IOException;
import java.util.*;

import static hu.u_szeged.inf.fog.simulator.node.NetworkGenerator.smallWorldNetworkGenerator;

/**
 * GeoPartition class implements the PartitionStrategy interface.
 * It partitions nodes based on their geographical location within a specified radius.
 */
public class GeoPartition implements PartitionStrategy {

    private final long mRadius;
    private ComputingAppliance startNode;
    private boolean isDebugMode = false;

    /**
     * Constructor for GeoPartition.
     *
     * @param mRadius the radius within which nodes are selected
     */
    public GeoPartition(int mRadius) {
        this.mRadius = mRadius;
    }

    /**
     * Gets the radius.
     *
     * @return the radius
     */
    public long getmRadius() {
        return mRadius;
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
     * Sets the debug mode. Use this to enable or disable debug output.
     * Debug output can be used at this <a href="https://mobisoftinfotech.com/tools/plot-multiple-points-on-map/">GeoPlotter</a> website.
     *
     * @param debugMode the debug mode to set
     */
    public void setDebugMode(boolean debugMode) {
        isDebugMode = debugMode;
    }

    /**
     * Prints the location of a node if debug mode is enabled.
     *
     * @param node the node whose location is to be printed
     * @param color the color to use for the marker
     */
    public void printLocation(ComputingAppliance node, String color) {
        if(!isDebugMode){
            return;
        }
        double latitude = node.geoLocation.latitude;
        double longitude = node.geoLocation.longitude;
        System.out.printf(Locale.US, "%.4f,%.4f,%s,marker%n", latitude, longitude, color);
    }

    /**
     * Selects nodes within the specified radius of the start node.
     *
     * @param nodes the list of nodes to select from
     * @return the list of selected nodes
     */
    @Override
    public List<ComputingAppliance> selectNodes(List<ComputingAppliance> nodes) {
        if (startNode == null) {
            throw new NullPointerException("startNode is null");
        }
        if (mRadius < 0) {
            throw new IllegalArgumentException("mRadius must be greater than 0");
        }
        if (mRadius == 0) {
            return List.of(startNode);
        }
        Set<ComputingAppliance> selectedNodes = new HashSet<>();
        selectedNodes.add(startNode);
        for (ComputingAppliance node : nodes) {
            if (node == startNode) {
                printLocation(startNode, "yellow"); //start node
                continue;
            }
            if (isNodeWithinRadius(startNode, node, mRadius)) {
                selectedNodes.add(node);
                printLocation(node, "red"); //within radius
                continue;
            }

            printLocation(node, "green"); //out of radius
        }
        SimLogger.logRun("[GeoPartition] Found " + (selectedNodes.size() - 1) + " nodes within " + mRadius + "m of " + startNode.name);
        return new ArrayList<>(selectedNodes);
    }

    /**
     * Checks if a node is within the specified radius of another node.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @param radius the radius to check within
     * @return true if the second node is within the radius of the first node, false otherwise
     */
    private static boolean isNodeWithinRadius(ComputingAppliance node1, ComputingAppliance node2, long radius) {
        GeoLocation location1 = node1.geoLocation;
        GeoLocation location2 = node2.geoLocation;
        double distance = location1.calculateDistance(location2);
        return distance <= radius;
    }

    /**
     * Main method for testing the GeoPartition class.
     *
     * @param args command line arguments
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
        ArrayList<ComputingAppliance> nodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ComputingAppliance cloud = new ComputingAppliance(ScenarioBase.resourcePath + "LPDS_original.xml", "cloud" + i, GeoLocation.generateRandomGeoLocation(), 0);
            nodes.add(cloud);
        }
        smallWorldNetworkGenerator(nodes, 4, 0.3, 30, 50);
        GeoPartition partition = new GeoPartition(5_000_000);
        partition.setStartNode(nodes.get(0));
        partition.setDebugMode(true);
        List<ComputingAppliance> result = partition.selectNodes(nodes);
    }
}