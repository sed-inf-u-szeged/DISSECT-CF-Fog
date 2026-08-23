package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.LocalMetrics;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.MappingStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LocalMetricsCalculator {

    public LocalMetrics calculate(ResourceAgent agent, List<ComponentPlacement> placements) {
        if (placements.isEmpty()) {
            throw new IllegalArgumentException("LocalOffer metrics cannot be calculated for empty placements.");
        }

        double utilisation = calculateUtilisation(agent, placements);
        double balance = calculateBalance(agent, placements);
        double fragmentation = calculateFragmentation(agent, placements);
        double compactness = calculateCompactness(agent, placements);
        double cost = calculateCost(agent, placements);
        double energy = calculateProjectedPower(placements);
        double latency = calculateLatency(placements);
        double bandwidth = calculateBandwidth(placements);

        return new LocalMetrics(balance, utilisation,fragmentation,compactness, cost, energy, latency, bandwidth);
    }

    private double calculateBandwidth(List<ComponentPlacement> placements) {
        double totalBandwidth = 0.0;

        for (ComponentPlacement placement : placements) {
            Repository repository =placement.capacity.node.iaas.repositories.get(0);

            double inputBandwidth = repository.getInputbw();
            double outputBandwidth =repository.getOutputbw();
            double averageBandwidth = (inputBandwidth + outputBandwidth) / 2.0;
            totalBandwidth += averageBandwidth;
        }

        return totalBandwidth / placements.size();
    }

    private double calculateLatency(List<ComponentPlacement> placements) {
        double totalLatency = 0.0;

        for (ComponentPlacement placement : placements) {
            Repository repository = placement.capacity.node.iaas.repositories.get(0);
            Integer inputLatency =repository.getLatencies().get(repository.getName());

            if (inputLatency == null) {
                throw new IllegalStateException("No input latency is configured for repository: " + repository.getName());
            }

            totalLatency += inputLatency;
        }

        return totalLatency / placements.size();
    }

    public double calculateProjectedPower(List<ComponentPlacement> placements) {
        if (placements.isEmpty()) {
            return 0.0;
        }

        Set<ComputingAppliance> affectedNodes = new LinkedHashSet<>();

        for (ComponentPlacement placement : placements) {
            affectedNodes.add(placement.capacity.node);
        }

        double totalProjectedPower = 0.0;

        for (ComputingAppliance node : affectedNodes) {
            PhysicalMachine machine = node.iaas.machines.get(0);

            double totalCpu = machine.getCapacities().getTotalProcessingPower();
            double currentlyFreeCpu = machine.freeCapacities.getTotalProcessingPower();
            double offerCpuDemand = 0.0;

            for (ComponentPlacement placement : placements) {
                if (placement.capacity.node == node) {
                    offerCpuDemand += MappingStrategy.requiredCpu(placement.component);
                }
            }

            double projectedCpuUtilisation = 1.0 - (currentlyFreeCpu - offerCpuDemand) / totalCpu;
            double projectedPower = machine.getCurrentPowerBehavior().getMinConsumption()
                    + machine.getCurrentPowerBehavior().getConsumptionRange() * projectedCpuUtilisation;

            totalProjectedPower += projectedPower;
        }

        return totalProjectedPower;
    }

    private double calculateCost(ResourceAgent agent, List<ComponentPlacement> placements) {
        double cost = 0.0;

        for (ComponentPlacement placement : placements) {
            Component component = placement.component;

            double demandShare = agent.calculateDemandShare(
                            MappingStrategy.requiredCpu(component),
                            MappingStrategy.requiredMemory(component),
                            MappingStrategy.requiredStorage(component));

            cost += agent.hourlyPrice * demandShare;
        }

        return cost;
    }

    private double calculateUtilisation(ResourceAgent agent, List<ComponentPlacement> placements) {
        double totalCpu = 0.0;
        long totalMemory = 0L;
        long totalStorage = 0L;

        double remainingCpu = 0.0;
        long remainingMemory = 0L;
        long remainingStorage = 0L;

        for (Capacity capacity : agent.capacities.values()) {
            totalCpu += capacity.totalCpu;
            totalMemory += capacity.totalMemory;
            totalStorage += capacity.totalStorage;

            remainingCpu += capacity.cpu;
            remainingMemory += capacity.memory;
            remainingStorage += capacity.storage;
        }

        for (ComponentPlacement placement : placements) {
            Component component = placement.component;

            remainingCpu -= MappingStrategy.requiredCpu(component);
            remainingMemory -= MappingStrategy.requiredMemory(component);
            remainingStorage -= MappingStrategy.requiredStorage(component);
        }

        double utilisationSum = 0.0;
        int dimensionCount = 0;

        if (totalCpu > 0) {
            utilisationSum += 1.0 - remainingCpu / totalCpu;
            dimensionCount++;
        }

        if (totalMemory > 0) {
            utilisationSum += 1.0 - (double) remainingMemory / totalMemory;
            dimensionCount++;
        }

        if (totalStorage > 0) {
            utilisationSum += 1.0 - (double) remainingStorage / totalStorage;
            dimensionCount++;
        }

        if (dimensionCount == 0) {
            return 0.0;
        }

        return utilisationSum / dimensionCount;
    }

    private double calculateBalance(ResourceAgent agent, List<ComponentPlacement> placements) {
        double balanceSum = 0.0;
        int evaluatedCapacityCount = 0;

        for (Capacity capacity : agent.capacities.values()) {
            List<Double> remainingRatios = calculateRemainingRatios(capacity, placements);

            if (remainingRatios.isEmpty()) {
                continue;
            }

            double minimumRemainingRatio =
                    remainingRatios.stream()
                            .mapToDouble(Double::doubleValue)
                            .min()
                            .orElse(0.0);

            double maximumRemainingRatio =
                    remainingRatios.stream()
                            .mapToDouble(Double::doubleValue)
                            .max()
                            .orElse(0.0);

            double capacityBalance = 1.0 - (maximumRemainingRatio - minimumRemainingRatio);
            balanceSum += capacityBalance;
            evaluatedCapacityCount++;
        }

        if (evaluatedCapacityCount == 0) {
            return 0.0;
        }

        return balanceSum / evaluatedCapacityCount;
    }

    private double calculateFragmentation(ResourceAgent agent, List<ComponentPlacement> placements) {

        if (agent.capacities.isEmpty()) {
            return 0.0;
        }

        long usedCapacityCount =
                placements.stream()
                        .map(placement -> placement.capacity)
                        .distinct()
                        .count();

        return (double) usedCapacityCount / agent.capacities.size();
    }

    private double calculateCompactness(ResourceAgent agent, List<ComponentPlacement> placements) {
        if (agent.capacities.isEmpty()) {
            return 0.0;
        }

        List<Double> remainingCpuValues = new ArrayList<>();
        List<Double> remainingMemoryValues = new ArrayList<>();
        List<Double> remainingStorageValues = new ArrayList<>();

        for (Capacity capacity : agent.capacities.values()) {
            remainingCpuValues.add(calculateRemainingCpu(capacity, placements));
            remainingMemoryValues.add(calculateRemainingMemory(capacity, placements));
            remainingStorageValues.add(calculateRemainingStorage(capacity, placements));
        }

        double cpuConcentration = calculateNormalizedConcentration(remainingCpuValues);
        double memoryConcentration = calculateNormalizedConcentration(remainingMemoryValues);
        double storageConcentration = calculateNormalizedConcentration(remainingStorageValues);

        return (cpuConcentration + memoryConcentration + storageConcentration) / 3.0;
    }

    private double calculateRemainingCpu(Capacity capacity, List<ComponentPlacement> placements) {
        double remainingCpu = capacity.cpu;

        for (ComponentPlacement placement : placements) {
            if (placement.capacity == capacity) {
                remainingCpu -= MappingStrategy.requiredCpu(placement.component);
            }
        }

        return Math.max(0.0, remainingCpu);
    }

    private double calculateRemainingMemory(Capacity capacity, List<ComponentPlacement> placements) {
        long remainingMemory = capacity.memory;

        for (ComponentPlacement placement : placements) {
            if (placement.capacity == capacity) {
                remainingMemory -= MappingStrategy.requiredMemory(placement.component);
            }
        }

        return Math.max(0L, remainingMemory);
    }

    private double calculateRemainingStorage(Capacity capacity, List<ComponentPlacement> placements) {
        long remainingStorage = capacity.storage;

        for (ComponentPlacement placement : placements) {
            if (placement.capacity == capacity) {
                remainingStorage -= MappingStrategy.requiredStorage(placement.component);
            }
        }

        return Math.max(0L, remainingStorage);
    }

    private double calculateNormalizedConcentration(List<Double> remainingValues) {
        double totalRemaining = remainingValues.stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        if (totalRemaining <= 0.0) {
            return 0.0;
        }

        if (remainingValues.size() == 1) {
            return 1.0;
        }

        double concentration = remainingValues.stream()
                .mapToDouble(value -> {
                    double share = value / totalRemaining;
                    return share * share;
                })
                .sum();

        double minimumConcentration = 1.0 / remainingValues.size();

        return (concentration - minimumConcentration) / (1.0 - minimumConcentration);
    }

    private List<Double> calculateRemainingRatios(Capacity capacity, List<ComponentPlacement> placements) {
        double remainingCpu = capacity.cpu;
        long remainingMemory = capacity.memory;
        long remainingStorage = capacity.storage;

        for (ComponentPlacement placement : placements) {
            if (placement.capacity == capacity) {
                Component component = placement.component;

                remainingCpu -=MappingStrategy.requiredCpu(component);
                remainingMemory -= MappingStrategy.requiredMemory(component);
                remainingStorage -= MappingStrategy.requiredStorage(component);
            }
        }

        List<Double> remainingRatios = new ArrayList<>();

        if (capacity.totalCpu > 0) {
            remainingRatios.add(remainingCpu / capacity.totalCpu);
        }

        if (capacity.totalMemory > 0) {
            remainingRatios.add((double) remainingMemory / capacity.totalMemory);
        }

        if (capacity.totalStorage > 0) {
            remainingRatios.add((double) remainingStorage / capacity.totalStorage);
        }

        return remainingRatios;
    }

    public double calculateCurrentUtilisation(ResourceAgent agent) {
        return calculateUtilisation(agent, List.of());
    }

    public double calculateCurrentBalance(ResourceAgent agent) {
        return calculateBalance(agent, List.of());
    }

    public double calculateCurrentCompactness(ResourceAgent agent) {
        return calculateCompactness(agent, List.of());
    }

    public double calculateCurrentCapacityFragmentation(ResourceAgent agent) {
        if (agent.capacities.isEmpty()) {
            return 0.0;
        }

        int partiallyUsedCapacityCount = 0;

        for (Capacity capacity : agent.capacities.values()) {
            List<Double> remainingRatios = calculateRemainingRatios(capacity, List.of());

            boolean alreadyUsed = remainingRatios.stream().anyMatch(ratio -> ratio < 1.0);
            boolean stillUsable = remainingRatios.stream().allMatch(ratio -> ratio > 0.0);

            if (alreadyUsed && stillUsable) {
                partiallyUsedCapacityCount++;
            }
        }

        return (double) partiallyUsedCapacityCount / agent.capacities.size();
    }
}