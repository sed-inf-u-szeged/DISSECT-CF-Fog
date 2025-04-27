package hu.u_szeged.inf.fog.simulator.node;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

public class NetworkGenerator {

    private NetworkGenerator(){}

    public static void smallWorldNetworkGenerator(ArrayList<ComputingAppliance> nodes, int degree, double probability,
                                                  int minLatency, int maxLatency) throws IOException {
        if (nodes.size() >= degree + 1 && degree > 1) {
            Random random = new Random();

            for (int i = 0; i < nodes.size(); i++) {
                ComputingAppliance center = nodes.get(i);
                for (int j = 1; j <= degree / 2; j++) {
                    int neighborIndex = (i + j) % nodes.size();
                    ComputingAppliance neighbor = nodes.get(neighborIndex);
                    center.addNeighbor(neighbor, random.nextInt(maxLatency - minLatency) + minLatency);
                }
            }

            List<Pair<ComputingAppliance, ComputingAppliance>> edgesToRemove = new ArrayList<>();
            List<Pair<ComputingAppliance, ComputingAppliance>> edgesToAdd = new ArrayList<>();

            for (ComputingAppliance center : nodes) {
                for (ComputingAppliance neighbor : center.neighbors) {
                    if (random.nextDouble() < probability) {
                        List<ComputingAppliance> availableNodes = new ArrayList<>(ComputingAppliance.getAllComputingAppliances());
                        availableNodes.removeAll(center.neighbors);
                        availableNodes.remove(center);
                        ComputingAppliance newNode = availableNodes.get(random.nextInt(availableNodes.size()));

                        if (!edgesToRemove.contains(Pair.of(neighbor, center))) {
                            edgesToRemove.add(Pair.of(center, neighbor));
                            edgesToAdd.add(Pair.of(center, newNode));
                        }
                    }
                }
            }

            removeConnections(edgesToRemove);
            addConnections(edgesToAdd, random, minLatency, maxLatency);

            if (isConnected(ComputingAppliance.allComputingAppliances)) {
                NetworkVisualiser.exportGraphToHtml(ScenarioBase.resultDirectory + File.separator + "graph.html");
            } else {
                SimLogger.logRun("Failed to create a connected graph. Retrying...");
                for (ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
                    ca.neighbors.clear();
                    ca.iaas.repositories.get(0).getLatencies().clear();
                }
                smallWorldNetworkGenerator(nodes, degree, probability, minLatency, maxLatency);

            }


        } else {
            SimLogger.logError("The small-world network cannot be generated with the given degree.");
        }
    }

    public static void removeConnections(List<Pair<ComputingAppliance, ComputingAppliance>> edgesToRemove) {
        for (Pair<ComputingAppliance, ComputingAppliance> pair : edgesToRemove) {
            //System.out.println("R: " + pair.getLeft().name  + " " + pair.getRight().name);
            pair.getLeft().neighbors.remove(pair.getRight());
            pair.getRight().neighbors.remove(pair.getLeft());
            pair.getLeft().iaas.repositories.get(0).removeLatencies(pair.getRight().name);
            pair.getRight().iaas.repositories.get(0).removeLatencies(pair.getLeft().name);
        }
    }

    public static void addConnections(List<Pair<ComputingAppliance, ComputingAppliance>> edgesToAdd, Random random,
                                      int minLatency, int maxLatency) {
        for (Pair<ComputingAppliance, ComputingAppliance> pair : edgesToAdd) {
            //System.out.println("A: " + pair.getLeft().name  + " " + pair.getRight().name);
            pair.getLeft().addNeighbor(pair.getRight(), random.nextInt(maxLatency - minLatency) + minLatency);
        }
    }

    public static boolean isConnected(List<ComputingAppliance> nodes) {
        Set<ComputingAppliance> visited = new HashSet<>();

        ComputingAppliance startNode = nodes.get(0);
        dfs(startNode, visited, nodes);

        return visited.size() == nodes.size();
    }

    private static void dfs(ComputingAppliance node, Set<ComputingAppliance> visited, List<ComputingAppliance> nodes) {
        visited.add(node);

        for (ComputingAppliance neighbor : node.neighbors) {
            if (!visited.contains(neighbor)) {
                dfs(neighbor, visited, nodes);
            }
        }
    }
}