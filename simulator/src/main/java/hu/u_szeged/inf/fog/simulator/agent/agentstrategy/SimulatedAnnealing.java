package hu.u_szeged.inf.fog.simulator.agent.agentstrategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import org.apache.commons.lang3.tuple.Pair;
import java.util.*;

/**
 * Resource allocation strategy based on the Simulated Annealing optimization algorithm.
 * Attempts to find an optimal allocation of resources to capacities by exploring different
 * resource-agent permutations and accepting worse solutions with decreasing probability over time
 * according to the temperature and score of the permutation.
 *
 * <p>The algorithm uses a temperature-based acceptance criterion to escape local optima
 * and converge towards a global optimum. Three cooling schedules are available:
 * exponential, linear, and logarithmic.</p>
 *
 * <p>The strategy evaluates solutions based on resource allocation count and fulfillment
 * of CPU, memory, and storage requirements.</p>
 */
public class SimulatedAnnealing extends AgentStrategy {
    public enum CoolingSchedule {
        EXPONENTIAL,
        LINEAR,
        LOGARITHMIC
    }

    private final Random random = SeedSyncer.centralRnd;
    private static final double INITIAL_TEMPERATURE = 100.0;
    private static final double INITIAL_TEMPERATURE_THRESHOLD =  0.0001;
    private double temperatureThreshold = INITIAL_TEMPERATURE_THRESHOLD;

    private static final int MAX_ITERATIONS = 1000;
    private CoolingSchedule coolingSchedule;


    public SimulatedAnnealing(final CoolingSchedule coolingSchedule) {
        this.coolingSchedule = coolingSchedule;
    }

    public SimulatedAnnealing() {
        this.coolingSchedule = CoolingSchedule.LINEAR;
    }

    @Override
    public List<Pair<ResourceAgent, Resource>> canFulfill(ResourceAgent agent, List<Resource> resources) {
        double totalRequestedCpu = 0;
        long totalRequestedMemory = 0;
        long totalRequestedStorage = 0;

        for (Resource resource : resources) {
            if (resource.cpu != null) {
                int instances = resource.instances == null ? 1 : resource.instances;
                totalRequestedCpu += resource.cpu * instances;
            }
            if (resource.memory != null) {
                int instances = resource.instances == null ? 1 : resource.instances;
                totalRequestedMemory += resource.memory * instances;
            }
            if (resource.size != null) {
                totalRequestedStorage += resource.size;
            }
        }

        // Step 1: Generate initial solution (random order)
        List<Resource> currentOrder = new ArrayList<>(resources);
        Collections.shuffle(currentOrder, random);

        Solution currentSolution = tryAllocate(agent, currentOrder);
        Solution bestSolution = currentSolution.copy();

        // Step 2: SA loop
        double temperature = INITIAL_TEMPERATURE;

        for (int iter = 1; iter <= MAX_ITERATIONS && temperature > temperatureThreshold; iter++) {
            List<Resource> neighborOrder = new ArrayList<>(currentOrder);

            int moveType = random.nextInt(3);
            if (moveType == 0) {
                int i = random.nextInt(neighborOrder.size());
                int j = random.nextInt(neighborOrder.size());
                Collections.swap(neighborOrder, i, j);
            } else if (moveType == 1) {                // Reverse a random subsequence
                int i = random.nextInt(neighborOrder.size());
                int j = random.nextInt(neighborOrder.size());
                if (i > j) {
                    int tempI = i;
                    i = j;
                    j = tempI;
                }
                Collections.reverse(neighborOrder.subList(i, j + 1));
            } else {                // Move one element to a random position
                int from = random.nextInt(neighborOrder.size());
                int to = random.nextInt(neighborOrder.size());
                Resource elem = neighborOrder.remove(from);
                neighborOrder.add(to, elem);
            }
            Solution neighborSolution = tryAllocate(agent, neighborOrder);
            double neighborScore = neighborSolution.getScore(resources.size(), totalRequestedCpu, totalRequestedMemory, totalRequestedStorage);
            double currentScore = currentSolution.getScore(resources.size(), totalRequestedCpu, totalRequestedMemory, totalRequestedStorage);

            if (neighborScore >= currentScore) { // Better solution - always accept
                currentOrder = new ArrayList<>(neighborOrder);
                currentSolution = neighborSolution.copy();
                bestSolution = neighborSolution.copy();
            } else {                // Worse solution - accept with probability based on temperature
                double delta = currentScore - neighborScore;
                double acceptanceProbability = Math.exp(-delta / temperature);

                if (random.nextDouble() < acceptanceProbability) {
                    bestSolution = neighborSolution.copy();
                    currentSolution = neighborSolution.copy();
                    currentOrder = new ArrayList<>(neighborOrder);
                }
            }
            temperature = updateTemperature(temperature, iter);
        }

        // Step 3: Actually allocate and reserve the best solution
        return reserveResources(agent, bestSolution);
    }

    public void switchCoolingTactic() {
        final int choice = random.nextInt(3);
        if(temperatureThreshold < 5){
            temperatureThreshold += 0.5;
        }

        switch (choice) {
            case 0:
                this.coolingSchedule = CoolingSchedule.LINEAR;
                break;
            case 1:
                this.coolingSchedule = CoolingSchedule.EXPONENTIAL;
                break;
            default:
                this.coolingSchedule = CoolingSchedule.LOGARITHMIC;
                break;
        }
    }

    public void resetValues(){
        temperatureThreshold = INITIAL_TEMPERATURE_THRESHOLD;
    }

    // Try to fit resources in given order WITHOUT actually reserving
    private Solution tryAllocate(ResourceAgent agent, List<Resource> resourceOrder) {
        int allocated = 0;
        double totalCpu = 0;
        long totalMemory = 0;
        long totalStorage = 0;

        Map<Resource, Integer> allocationMap = new HashMap<>();

        List<Capacity> tempCapacities = new ArrayList<>();
        for (Capacity cap : agent.capacities) {
            Capacity temp = new Capacity(cap.node, cap.cpu, cap.memory, cap.storage);
            tempCapacities.add(temp);
        }

        for (Resource resource : resourceOrder) {
            for (int capIdx = 0; capIdx < tempCapacities.size(); capIdx++) {
                Capacity cap = tempCapacities.get(capIdx);

                int instances = resource.instances == null ? 1 : resource.instances;
                double requiredCpu = (resource.cpu != null && resource.cpu > 0) ? resource.cpu * instances : 0;
                long requiredMemory = (resource.memory != null && resource.memory > 0) ? resource.memory * instances : 0;
                long requiredStorage = (resource.size != null && resource.size > 0) ? resource.size : 0;

                if (isMatchingPreferences(resource, cap)
                        && requiredCpu <= cap.cpu
                        && requiredMemory <= cap.memory
                        && requiredStorage <= cap.storage) {

                    cap.cpu -= requiredCpu;
                    cap.memory -= requiredMemory;
                    cap.storage -= requiredStorage;

                    totalCpu += requiredCpu;
                    totalMemory += requiredMemory;
                    totalStorage += requiredStorage;

                    allocationMap.put(resource, capIdx);
                    allocated++;
                    break;
                }
            }
        }

        return new Solution(resourceOrder, allocated, totalCpu, totalMemory, totalStorage, allocationMap);
    }

    private List<Pair<ResourceAgent, Resource>> reserveResources(ResourceAgent agent, Solution solution) {
        List<Pair<ResourceAgent, Resource>> result = new ArrayList<>();

        for (Resource resource : solution.resourceOrder) {
            if (!solution.allocationMap.containsKey(resource)) {
                continue; // Skip resources that couldn't be allocated
            }

            int targetCapIdx = solution.allocationMap.get(resource);

            Capacity targetCap = agent.capacities.get(targetCapIdx);
            int instances = resource.instances == null ? 1 : resource.instances;

            for (int i = 0; i < instances; i++) {
                targetCap.reserveCapacity(resource);
                result.add(Pair.of(agent, resource));
            }
        }

        return result;
    }

    private double updateTemperature(double currentTemp, int iteration) {
        switch (coolingSchedule) {
            case EXPONENTIAL:
                return INITIAL_TEMPERATURE * Math.pow(0.98, iteration);

            case LOGARITHMIC:
                return INITIAL_TEMPERATURE / Math.pow(Math.log(iteration + Math.E), 3);

            default: // LINEAR
                return currentTemp - INITIAL_TEMPERATURE / MAX_ITERATIONS;
        }
    }

    /**
     * Solution class that represents a single permutation's different attributes. The number
     * of resources it can fulfill and the total CPU, memory and storage it can fulfill.
     */
    private static class Solution {
        List<Resource> resourceOrder;
        int resourcesAllocated;
        double totalCpu;
        long totalMemory;
        long totalStorage;
        Map<Resource, Integer> allocationMap; // Maps resource to capacity index

        Solution(List<Resource> order, int allocated, double cpu, long mem, long storage, Map<Resource, Integer> allocMap) {
            this.resourceOrder = new ArrayList<>(order);
            this.resourcesAllocated = allocated;
            this.totalCpu = cpu;
            this.totalMemory = mem;
            this.totalStorage = storage;
            this.allocationMap = new HashMap<>(allocMap);
        }

        Solution copy() {
            return new Solution(resourceOrder, resourcesAllocated, totalCpu, totalMemory, totalStorage, allocationMap);
        }

        double getScore(int requestedResources, double totalRequestedCpu, long totalRequestedMemory, long totalRequestedStorage) {
            double allocationRatio = (double) resourcesAllocated / requestedResources;

            double cpuFulfillment = totalRequestedCpu > 0 ? (totalCpu / totalRequestedCpu) : 1.0;
            double memoryFulfillment = totalRequestedMemory > 0 ? ((double) totalMemory / totalRequestedMemory) : 1.0;
            double storageFulfillment = totalRequestedStorage > 0 ? ((double) totalStorage / totalRequestedStorage) : 1.0;

            final double MIN_ACCEPTABLE_CPU = 0.5;
            final double MIN_ACCEPTABLE_MEMORY = 0.5;
            final double MIN_ACCEPTABLE_STORAGE = 0.5;

            double penalty = 1.0;
            if (totalRequestedCpu > 0 && cpuFulfillment < MIN_ACCEPTABLE_CPU) {
                penalty *= 0.5;
            }
            if (totalRequestedMemory > 0 && memoryFulfillment < MIN_ACCEPTABLE_MEMORY) {
                penalty *= 0.5;
            }
            if (totalRequestedStorage > 0 && storageFulfillment < MIN_ACCEPTABLE_STORAGE) {
                penalty *= 0.5;
            }

            return (allocationRatio * 2 + cpuFulfillment + memoryFulfillment + storageFulfillment) * penalty;
        }

        @Override
        public String toString() {
            return String.format("Resources=%d/%d, CPU=%.1f, MEM=%.1fGB, Storage=%.1fGB",
                    resourcesAllocated, resourceOrder.size(),
                    totalCpu,
                    totalMemory / 1_000_000_000.0,
                    totalStorage / 1_000_000_000.0);
        }
    }
}