package hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.sa;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.MappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalMetricsCalculator;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.LocalMetrics;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalOffer.ComponentPlacement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SimulatedAnnealingStrategy extends MappingStrategy {

    private static final double EPSILON = 1e-9;

    private final Random random = SeedSyncer.centralRnd;

    private final LocalMetricsCalculator metricsCalculator = new LocalMetricsCalculator();

    @Override
    public List<LocalOffer> generateLocalOffers(ResourceAgent agent, AgentApplication application) {
        int maxIterations = (int) Config.APP_TYPE.get("saMaxIterations");
        int maxNeighborAttempts = (int) Config.APP_TYPE.get("saNeighborAttempts");
        double initialTemperature = (double) Config.APP_TYPE.get("saInitialTemperature");
        double minimumTemperature = (double) Config.APP_TYPE.get("saMinimumTemperature");
        double coolingRate = (double) Config.APP_TYPE.get("saCoolingRate");

        LocalMappingState bestState = optimize(agent, application, maxIterations, maxNeighborAttempts,
                        initialTemperature, minimumTemperature, coolingRate);

        if (bestState.isEmpty()) {
            return List.of();
        }

        List<ComponentPlacement> placements = bestState.toPlacements();
        LocalMetrics metrics = metricsCalculator.calculate(agent, placements);
        LocalOffer localOffer = new LocalOffer(agent, placements, metrics);

        return List.of(localOffer);
    }

    private LocalMappingState optimize(
            ResourceAgent agent,
            AgentApplication application,
            int maxIterations,
            int maxNeighborAttempts,
            double initialTemperature,
            double minimumTemperature,
            double coolingRate) {

        LocalMappingState currentState = createInitialState(agent, application);
        LocalMappingState bestState = currentState;

        double currentEnergy = calculateEnergy(agent, application, currentState);
        double bestEnergy = currentEnergy;
        double temperature = initialTemperature;

        for (int iteration = 0; iteration < maxIterations && temperature > minimumTemperature; iteration++) {
            LocalMappingState neighborState = generateNeighbor(agent, currentState, maxNeighborAttempts);
            double neighborEnergy = calculateEnergy(agent, application, neighborState);
            double energyDifference = neighborEnergy - currentEnergy;

            boolean acceptNeighbor = energyDifference <= 0.0
                    || random.nextDouble() < Math.exp(-energyDifference / temperature);

            if (acceptNeighbor) {
                currentState = neighborState;
                currentEnergy = neighborEnergy;
            }

            if (currentEnergy < bestEnergy) {
                bestState = currentState;
                bestEnergy = currentEnergy;
            }

            temperature *= coolingRate;
        }

        return bestState;
    }

    private double calculateEnergy(ResourceAgent agent, AgentApplication application, LocalMappingState state) {
        application.localCandidateEvaluationCount++;

        double coveragePenalty = calculateCoveragePenalty(state);

        if (state.isEmpty()) {
            return coveragePenalty * 2.0;
        }

        LocalMetrics metrics = metricsCalculator.calculate(agent, state.toPlacements());
        double qosScore = calculateQosScore(application, metrics);
        double resourceScore = calculateResourceScore(metrics);

        double boundedQosScore = qosScore / (1.0 + qosScore);
        double boundedResourceScore = Math.max(0.0, Math.min(1.0, resourceScore));
        double secondaryScore = (boundedQosScore + boundedResourceScore) / 2.0;

        return coveragePenalty * 2.0 + secondaryScore;
    }

    private LocalMappingState createInitialState(ResourceAgent agent, AgentApplication application) {

        Map<Capacity, AvailableCapacity> availableCapacities = new LinkedHashMap<>();

        for (Capacity capacity : agent.capacities.values()) {
            availableCapacities.put(capacity, new AvailableCapacity(capacity));
        }

        List<Component> shuffledComponents = new ArrayList<>(application.components);

        Collections.shuffle(shuffledComponents, random);

        Map<Component, Capacity> assignments = new LinkedHashMap<>();

        for (Component component : shuffledComponents) {
            List<Capacity> shuffledCapacities = new ArrayList<>( agent.capacities.values());

            Collections.shuffle(shuffledCapacities, random);

            for (Capacity capacity : shuffledCapacities) {
                AvailableCapacity availableCapacity = availableCapacities.get(capacity);

                if (!isMatchingPreferences(component, capacity)) {
                    continue;
                }

                if (!availableCapacity.canHost(component)) {
                    continue;
                }

                assignments.put(component, capacity);
                availableCapacity.consume(component);

                break;
            }
        }

        return new LocalMappingState(application.components, assignments);
    }

    private LocalMappingState generateMoveNeighbor(ResourceAgent agent, LocalMappingState currentState) {
        if (currentState.assignments.isEmpty() || agent.capacities.size() < 2) {
            return currentState;
        }

        List<Component> placedComponents = new ArrayList<>(currentState.assignments.keySet());
        Collections.shuffle(placedComponents, random);

        for (Component component : placedComponents) {
            Capacity currentCapacity = currentState.assignments.get(component);
            List<Capacity> candidateCapacities = new ArrayList<>(agent.capacities.values());
            Collections.shuffle(candidateCapacities, random);

            for (Capacity candidateCapacity : candidateCapacities) {
                if (candidateCapacity == currentCapacity) {
                    continue;
                }

                Map<Component, Capacity> candidateAssignments = new LinkedHashMap<>(currentState.assignments);
                candidateAssignments.put(component, candidateCapacity);

                LocalMappingState candidateState =
                        new LocalMappingState(currentState.applicationComponents, candidateAssignments);

                if (isValidState(agent, candidateState)) {
                    return candidateState;
                }
            }
        }

        return currentState;
    }

    private boolean isValidState(ResourceAgent agent, LocalMappingState state) {
        Map<Capacity, AvailableCapacity> availableCapacities = new LinkedHashMap<>();

        for (Capacity capacity : agent.capacities.values()) {
            availableCapacities.put(capacity, new AvailableCapacity(capacity));
        }

        for (Map.Entry<Component, Capacity> entry : state.assignments.entrySet()) {
            Component component = entry.getKey();
            Capacity capacity = entry.getValue();
            AvailableCapacity availableCapacity = availableCapacities.get(capacity);

            if (availableCapacity == null) {
                return false;
            }

            if (!isMatchingPreferences(component, capacity)) {
                return false;
            }

            if (!availableCapacity.canHost(component)) {
                return false;
            }

            availableCapacity.consume(component);
        }

        return true;
    }

    private LocalMappingState generateAddNeighbor(ResourceAgent agent, LocalMappingState currentState) {
        List<Component> missingComponents = new ArrayList<>();

        for (Component component : currentState.applicationComponents) {
            if (!currentState.assignments.containsKey(component)) {
                missingComponents.add(component);
            }
        }

        if (missingComponents.isEmpty() || agent.capacities.isEmpty()) {
            return currentState;
        }

        Collections.shuffle(missingComponents, random);

        for (Component component : missingComponents) {
            List<Capacity> candidateCapacities = new ArrayList<>(agent.capacities.values());
            Collections.shuffle(candidateCapacities, random);

            for (Capacity candidateCapacity : candidateCapacities) {
                Map<Component, Capacity> candidateAssignments = new LinkedHashMap<>(currentState.assignments);
                candidateAssignments.put(component, candidateCapacity);

                LocalMappingState candidateState =
                        new LocalMappingState(currentState.applicationComponents, candidateAssignments);

                if (isValidState(agent, candidateState)) {
                    return candidateState;
                }
            }
        }

        return currentState;
    }

    private LocalMappingState generateRemoveNeighbor(LocalMappingState currentState) {
        if (currentState.assignments.isEmpty()) {
            return currentState;
        }

        List<Component> placedComponents = new ArrayList<>(currentState.assignments.keySet());
        Component componentToRemove = placedComponents.get(random.nextInt(placedComponents.size()));

        Map<Component, Capacity> candidateAssignments = new LinkedHashMap<>(currentState.assignments);
        candidateAssignments.remove(componentToRemove);

        return new LocalMappingState(currentState.applicationComponents, candidateAssignments);
    }

    private LocalMappingState generateSwapNeighbor(ResourceAgent agent, LocalMappingState currentState) {
        if (currentState.assignments.size() < 2) {
            return currentState;
        }

        List<Component> placedComponents = new ArrayList<>(currentState.assignments.keySet());
        Collections.shuffle(placedComponents, random);

        for (int firstIndex = 0; firstIndex < placedComponents.size(); firstIndex++) {
            Component firstComponent = placedComponents.get(firstIndex);
            Capacity firstCapacity = currentState.assignments.get(firstComponent);

            for (int secondIndex = firstIndex + 1; secondIndex < placedComponents.size(); secondIndex++) {
                Component secondComponent = placedComponents.get(secondIndex);
                Capacity secondCapacity = currentState.assignments.get(secondComponent);

                if (firstCapacity == secondCapacity) {
                    continue;
                }

                Map<Component, Capacity> candidateAssignments = new LinkedHashMap<>(currentState.assignments);
                candidateAssignments.put(firstComponent, secondCapacity);
                candidateAssignments.put(secondComponent, firstCapacity);

                LocalMappingState candidateState =
                        new LocalMappingState(currentState.applicationComponents, candidateAssignments);

                if (isValidState(agent, candidateState)) {
                    return candidateState;
                }
            }
        }

        return currentState;
    }

    private LocalMappingState generateNeighbor(ResourceAgent agent, LocalMappingState currentState, int maxNeighborAttempts) {

        if (maxNeighborAttempts <= 0) {
            throw new IllegalArgumentException("The maximum number of neighbor attempts must be positive.");
        }

        for (int attempt = 0; attempt < maxNeighborAttempts; attempt++) {
            int moveType = random.nextInt(4);

            LocalMappingState neighborState = switch (moveType) {
                case 0 -> generateAddNeighbor(agent, currentState);
                case 1 -> generateRemoveNeighbor(currentState);
                case 2 -> generateMoveNeighbor(agent, currentState);
                default -> generateSwapNeighbor(agent, currentState);
            };

            if (!haveSameAssignments(currentState, neighborState)) {
                return neighborState;
            }
        }

        return currentState;
    }

    private double calculateQosScore(AgentApplication application, LocalMetrics metrics) {
        double totalWeight = application.price + application.energy + application.latency + application.bandwidth;

        if (totalWeight <= EPSILON) {
            return 0.0;
        }

        double weightedScore = 0.0;

        if (application.price > EPSILON) {
            weightedScore += application.price * normalizeMinimizedMetric(metrics.cost, application.maxCost, "maxCost");
        }

        if (application.energy > EPSILON) {
            weightedScore += application.energy
                    * normalizeMinimizedMetric(metrics.energy, application.maxEnergyConsumption, "maxEnergyConsumption");
        }

        if (application.latency > EPSILON) {
            weightedScore += application.latency * normalizeMinimizedMetric(metrics.latency, application.maxLatency, "maxLatency");
        }

        if (application.bandwidth > EPSILON) {
            weightedScore += application.bandwidth * normalizeMaximizedMetric(metrics.bandwidth, application.minBandwidth, "minBandwidth");
        }

        return weightedScore / totalWeight;
    }

    private double normalizeMinimizedMetric(double actualValue, Double referenceValue, String requirementName) {
        if (referenceValue == null || referenceValue <= EPSILON) {
            throw new IllegalArgumentException(requirementName + " must be positive when its QoS weight is positive.");
        }

        return actualValue / referenceValue;
    }

    private double normalizeMaximizedMetric(double actualValue, Double referenceValue, String requirementName) {
        if (referenceValue == null || referenceValue <= EPSILON) {
            throw new IllegalArgumentException(requirementName + " must be positive when its QoS weight is positive.");
        }

        return referenceValue / Math.max(actualValue, EPSILON);
    }

    private boolean haveSameAssignments(LocalMappingState firstState, LocalMappingState secondState) {
        return firstState.assignments.equals(secondState.assignments);
    }

    private double calculateCoveragePenalty(LocalMappingState state) {
        return state.getMissingComponentCount();
    }

    private double calculateResourceScore(LocalMetrics metrics) {
        double balancePenalty = 1.0 - metrics.balance;
        double utilisationPenalty = 1.0 - metrics.utilisation;
        double fragmentationPenalty = metrics.fragmentation;
        double compactnessPenalty = 1.0 - metrics.compactness;

        return (balancePenalty + utilisationPenalty + fragmentationPenalty + compactnessPenalty) / 4.0;
    }
}