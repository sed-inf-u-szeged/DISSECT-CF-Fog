package hu.u_szeged.inf.fog.simulator.agent.strategy.selection;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.MappingStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;

import java.util.*;

public class QoSNormalizationBounds {

    public final double minimumCost;
    public final double maximumCost;

    public final double minimumEnergy;
    public final double maximumEnergy;

    public final double minimumLatency;
    public final double maximumLatency;

    public final double minimumBandwidth;
    public final double maximumBandwidth;

    public QoSNormalizationBounds(
            double minimumCost,
            double maximumCost,
            double minimumEnergy,
            double maximumEnergy,
            double minimumLatency,
            double maximumLatency,
            double minimumBandwidth,
            double maximumBandwidth) {

        this.minimumCost = minimumCost;
        this.maximumCost = maximumCost;
        this.minimumEnergy = minimumEnergy;
        this.maximumEnergy = maximumEnergy;
        this.minimumLatency = minimumLatency;
        this.maximumLatency = maximumLatency;
        this.minimumBandwidth = minimumBandwidth;
        this.maximumBandwidth = maximumBandwidth;
    }

    public static QoSNormalizationBounds calculateFor(AgentApplication application) {
        return calculateForComponents(application.components);
    }

    public static QoSNormalizationBounds calculateForComponents(Collection<Component> components) {
        if (components.isEmpty()) {
            throw new IllegalArgumentException("QoS normalization requires at least one component.");
        }

        if (ResourceAgent.allResourceAgents.isEmpty()) {
            throw new IllegalStateException("QoS normalization requires at least one ResourceAgent.");
        }

        Set<ComputingAppliance> nodes = collectResourceNodes();

        if (nodes.isEmpty()) {
            throw new IllegalStateException("QoS normalization requires at least one resource node.");
        }

        return new QoSNormalizationBounds(
                calculateMinimumCost(components),
                calculateMaximumCost(components),
                calculateMinimumEnergy(nodes),
                calculateMaximumEnergy(components.size(), nodes),
                calculateMinimumLatency(nodes),
                calculateMaximumLatency(nodes),
                calculateMinimumBandwidth(nodes),
                calculateMaximumBandwidth(nodes));
    }

    private static Set<ComputingAppliance> collectResourceNodes() {
        Set<ComputingAppliance> nodes = new LinkedHashSet<>();

        for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
            agent.capacities.values().forEach(capacity -> nodes.add(capacity.node));
        }

        return nodes;
    }

    private static double calculateMinimumCost(Collection<Component> components) {
        double totalMinimumCost = 0.0;

        for (Component component : components) {
            double componentMinimumCost = Double.POSITIVE_INFINITY;

            for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
                double minimumDynamicCost = calculateComponentCost(agent, component) * 0.85; // TODO: remove hard-coded factor
                componentMinimumCost = Math.min(componentMinimumCost, minimumDynamicCost);
            }

            totalMinimumCost += componentMinimumCost;
        }

        return totalMinimumCost;
    }

    private static double calculateMaximumCost(Collection<Component> components) {
        double totalMaximumCost = 0.0;

        for (Component component : components) {
            double componentMaximumCost = Double.NEGATIVE_INFINITY;

            for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
                double maximumDynamicCost = calculateComponentCost(agent, component) * 1.15; // TODO: remove hard-coded factor
                componentMaximumCost = Math.max(componentMaximumCost, maximumDynamicCost);
            }

            totalMaximumCost += componentMaximumCost;
        }

        return totalMaximumCost;
    }

    private static double calculateComponentCost(ResourceAgent agent, Component component) {
        double demandShare = agent.calculateDemandShare(
                MappingStrategy.requiredCpu(component),
                MappingStrategy.requiredMemory(component),
                MappingStrategy.requiredStorage(component));

        return agent.baseHourlyPrice * demandShare;
    }

    private static double calculateMinimumEnergy(Set<ComputingAppliance> nodes) {
        return nodes.stream()
                .map(node -> node.iaas.machines.get(0))
                .mapToDouble(machine -> machine.getCurrentPowerBehavior().getMinConsumption())
                .min()
                .orElseThrow();
    }

    private static double calculateMaximumEnergy(
            int componentCount,
            Set<ComputingAppliance> nodes) {

        List<Double> maximumNodePowers = new ArrayList<>();

        for (ComputingAppliance node : nodes) {
            PhysicalMachine machine = node.iaas.machines.get(0);

            double maximumPower = machine.getCurrentPowerBehavior().getMinConsumption()
                    + machine.getCurrentPowerBehavior().getConsumptionRange();

            maximumNodePowers.add(maximumPower);
        }

        maximumNodePowers.sort(Comparator.reverseOrder());

        int maximumUsedNodeCount = Math.min(componentCount, maximumNodePowers.size());

        return maximumNodePowers.stream()
                .limit(maximumUsedNodeCount)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private static double calculateMinimumLatency(Set<ComputingAppliance> nodes) {
        return nodes.stream()
                .map(QoSNormalizationBounds::getRepository)
                .mapToDouble(QoSNormalizationBounds::getInputLatency)
                .min()
                .orElseThrow();
    }

    private static double calculateMaximumLatency(Set<ComputingAppliance> nodes) {
        return nodes.stream()
                .map(QoSNormalizationBounds::getRepository)
                .mapToDouble(QoSNormalizationBounds::getInputLatency)
                .max()
                .orElseThrow();
    }

    private static double calculateMinimumBandwidth(Set<ComputingAppliance> nodes) {
        return nodes.stream()
                .map(QoSNormalizationBounds::getRepository)
                .mapToDouble(QoSNormalizationBounds::getAverageBandwidth)
                .min()
                .orElseThrow();
    }

    private static double calculateMaximumBandwidth(Set<ComputingAppliance> nodes) {
        return nodes.stream()
                .map(QoSNormalizationBounds::getRepository)
                .mapToDouble(QoSNormalizationBounds::getAverageBandwidth)
                .max()
                .orElseThrow();
    }

    private static Repository getRepository(ComputingAppliance node) {
        return node.iaas.repositories.get(0);
    }

    private static double getInputLatency(Repository repository) {
        Integer latency = repository.getLatencies().get(repository.getName());

        if (latency == null) {
            throw new IllegalStateException(
                    "No input latency is configured for repository: " + repository.getName());
        }

        return latency;
    }

    private static double getAverageBandwidth(Repository repository) {
        return (repository.getInputbw() + repository.getOutputbw()) / 2.0;
    }

    @Override
    public String toString() {
        return "cost=[" + minimumCost + ", " + maximumCost + "]"
                + ", energy_W=[" + minimumEnergy + ", " + maximumEnergy + "]"
                + ", latency_ms=[" + minimumLatency + ", " + maximumLatency + "]"
                + ", bandwidth_byte_per_ms=[" + minimumBandwidth + ", " + maximumBandwidth + "]";
    }
}