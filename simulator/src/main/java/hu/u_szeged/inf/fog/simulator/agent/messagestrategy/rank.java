package hu.u_szeged.inf.fog.simulator.agent.messagestrategy;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class rank {

    /**
     * Rank ResourceAgents by total bandwidth (input + output). Higher is better.
     */
    public List<ResourceAgent> rankByBandwidth(List<ResourceAgent> agents) {
        return agents.stream()
                .sorted((a1, a2) -> Long.compare(
                        getTotalBandwidth(a2),
                        getTotalBandwidth(a1)
                ))
                .collect(Collectors.toList());
    }

    /**
     * Rank ResourceAgents by latency to a target NetworkNode. Lower is better.
     */
    public List<ResourceAgent> rankByLatency(List<ResourceAgent> agents, NetworkNode targetNode) {
        return agents.stream()
                .sorted((a1, a2) -> Integer.compare(
                        getLatencyToNode(a1, targetNode),
                        getLatencyToNode(a2, targetNode)
                ))
                .collect(Collectors.toList());
    }

    /**
     * Rank ResourceAgents by power consumption. Lower is better.
     */
    public List<ResourceAgent> rankByPowerConsumption(List<ResourceAgent> agents) {
        return agents.stream()
                .sorted((a1, a2) -> Double.compare(
                        calculatePowerConsumption(a1),
                        calculatePowerConsumption(a2)
                ))
                .collect(Collectors.toList());
    }

    /**
     * Custom rank: lower latency first, then higher bandwidth if equal.
     */
    public List<ResourceAgent> rankCustom(List<ResourceAgent> agents, NetworkNode targetNode) {
        return agents.stream()
                .sorted((a1, a2) -> {
                    int latencyComp = Integer.compare(
                            getLatencyToNode(a1, targetNode),
                            getLatencyToNode(a2, targetNode)
                    );
                    if (latencyComp != 0) return latencyComp;

                    return Long.compare(
                            getTotalBandwidth(a2),
                            getTotalBandwidth(a1)
                    );
                })
                .collect(Collectors.toList());
    }

    public long getTotalBandwidth(ResourceAgent agent) {
        try {
            NetworkNode node = agent.hostNode.iaas.repositories.get(0);
            return node.getInputbw() + node.getOutputbw();
        } catch (Exception e) {
            System.out.println("Failed to read bandwidth for agent " + agent.name);
            return 0;
        }
    }

    public int getLatencyToNode(ResourceAgent agent, NetworkNode targetNode) {
        try {
            String targetName = targetNode.getName();
            Map<String, Integer> latencies = agent.hostNode.iaas.repositories.get(0).getLatencies();

            return latencies.getOrDefault(targetName, Integer.MAX_VALUE);
        } catch (Exception e) {
            System.out.println("Failed to read latency for agent " + agent.name);
            return Integer.MAX_VALUE;
        }
    }

    public double calculatePowerConsumption(ResourceAgent agent) {
        try {
            VirtualMachine vm = agent.service;

            if (vm.getState() == VirtualMachine.State.RUNNING) {
                EnergyDataCollector edc = EnergyDataCollector.getEnergyCollector(agent.hostNode.iaas);
                if (edc != null) {
                    return edc.energyConsumption / 1000.0;
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to read power consumption for agent " + agent.name);
        }

        return 0.0;
    }
}
