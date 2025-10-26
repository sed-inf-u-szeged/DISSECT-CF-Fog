package hu.u_szeged.inf.fog.simulator.agent.strategy;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import org.apache.commons.lang3.tuple.Pair;
import java.util.*;

public class SimulatedAnnealing extends AgentStrategy {
    public enum CoolingSchedule {
        EXPONENTIAL, // fastest cooling (for more complex fits not good)
        LINEAR, // always lowered by a constant number
        LOGARITHMIC // first cools down relative normal then slows down drastically
    }


    // TODO: change random to deterministic random (SeedSyncer.centralRnd)
    private final Random random = new Random();
    private static final double EPSILON = 1e-9;
    private static final double INITIAL_TEMP = 1000.0;
    private static final int MAX_ITERATIONS = 250;
    private static final double INITIAL_EXPONENTIAL_DECREASE = 1.1;
    private final CoolingSchedule coolingSchedule;

    private double exponential_decrease;

    public SimulatedAnnealing(CoolingSchedule coolingSchedule) {
        this.coolingSchedule = coolingSchedule;
    }

    public SimulatedAnnealing() {
        this.coolingSchedule = CoolingSchedule.LOGARITHMIC;
    }

    @Override
    public List<Pair<ResourceAgent, Resource>> canFulfill(ResourceAgent agent, List<Resource> resources) {
        System.out.println("\n=== SA START for " + agent.name + " ===");

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

        System.out.println("Total requested - CPU: " + totalRequestedCpu +
                ", Memory: " + (totalRequestedMemory / 1_073_741_824) + "GB" +
                ", Storage: " + (totalRequestedStorage / 1_073_741_824) + "GB");

        // Step 1: Generate initial solution (random order)
        List<Resource> currentOrder = new ArrayList<>(resources);
        Collections.shuffle(currentOrder, random);

        Solution currentSolution = tryAllocate(agent, currentOrder);
        System.out.println("Initial solution: " + currentSolution);

        Solution bestSolution = currentSolution.copy();

        // Step 2: SA loop
        double temp = INITIAL_TEMP;
        exponential_decrease = INITIAL_EXPONENTIAL_DECREASE;

        for (int iter = 1; iter <= MAX_ITERATIONS && temp > 1; iter++) {
            List<Resource> neighborOrder = new ArrayList<>(currentOrder);
            int moveType = random.nextInt(3);
            //System.out.println("iter " + iter);
            if (moveType == 0) {                // Swap two elements
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
            } else {                // Rotate: move one element to a random position
                int from = random.nextInt(neighborOrder.size());
                int to = random.nextInt(neighborOrder.size());
                Resource elem = neighborOrder.remove(from);
                neighborOrder.add(to, elem);
            }
            Solution neighborSolution = tryAllocate(agent, neighborOrder);

            double neighborScore = neighborSolution.getScore(resources.size(), totalRequestedCpu, totalRequestedMemory, totalRequestedStorage);
            double currentScore = currentSolution.getScore(resources.size(), totalRequestedCpu, totalRequestedMemory, totalRequestedStorage);

            //System.out.println(neighborScore + " vs " + currentScore + " of " + resources.size());
            if (neighborScore >= currentScore || neighborSolution.isBetter(currentSolution)) { // Better solution - always accept
                bestSolution = neighborSolution.copy();
                currentSolution = neighborSolution.copy();
                bestSolution = neighborSolution.copy();
               // System.out.println("BETTER");
            } else {                // Worse solution - accept with probability based on temperature
                double delta = neighborScore - currentScore;
                double acceptanceProbability = Math.pow(EPSILON, -(delta / temp));
               // System.out.println("acceptProb: " + acceptanceProbability);
               // System.out.println("delta: " + delta);
               // System.out.println("temp: " + temp);

                if (random.nextDouble() < acceptanceProbability) {
                    bestSolution = neighborSolution.copy();
                    currentSolution = neighborSolution.copy();
                    currentOrder = new ArrayList<>(neighborOrder);
                  /*  System.out.println("Iter " + iter + ": WORSE but accepted (score: " +
                            String.format("%.4f", neighborScore) + " < " +
                            String.format("%.4f", currentScore) +
                            ", prob=" + String.format("%.4f", acceptanceProbability) +
                            ", temp=" + String.format("%.1f", temp) + ")");

                   */
                }
            }

            temp = updateTemperature(temp, iter);
            // System.out.println("updatedTemp: " + temp);
        }

        System.out.println("\n=== SA COMPLETE FOR " + agent.name + "===");
        System.out.println("Best: " + bestSolution);

        // Step 3: Actually allocate and reserve the best solution
        return reserveResources(agent, bestSolution);
    }

    // Try to fit resources in given order WITHOUT reserving
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
            boolean isComputeOnly = (resource.size == null);
            boolean isStorageOnly = resource.size != null && ((resource.cpu == null || resource.cpu == 0) && (resource.memory == null || resource.memory == 0));
            boolean isHybrid = (resource.size != null && resource.size > 0 && (resource.cpu != null && resource.cpu > 0) || (resource.memory != null && resource.memory > 0));

            if (isComputeOnly) {
                int instances = resource.instances == null ? 1 : resource.instances;

                // 1 component with more instances can't be deployed to 2 different resources if im correct
                for (int capIdx = 0; capIdx < tempCapacities.size(); capIdx++) {
                    Capacity cap = tempCapacities.get(capIdx);
                    if (isMatchingPreferences(resource, cap)
                            && resource.cpu * instances <= cap.cpu
                            && resource.memory * instances <= cap.memory) {
                        cap.cpu -= resource.cpu * instances;
                        cap.memory -= resource.memory * instances;

                        totalCpu += resource.cpu * instances;
                        totalMemory += resource.memory * instances;

                        allocationMap.put(resource, capIdx);
                        allocated++;
                        break;
                    }
                }
            } else if (isStorageOnly) {
                for (int capIdx = 0; capIdx < tempCapacities.size(); capIdx++) {
                    Capacity cap = tempCapacities.get(capIdx);
                    if (isMatchingPreferences(resource, cap)
                            && resource.size <= cap.storage
                    ) {
                        cap.storage -= resource.size;
                        allocated++;
                        totalStorage += resource.size;
                        allocationMap.put(resource, capIdx);
                        break;
                    }
                }

            } else if (isHybrid) {
                // Hybrid resource: needs BOTH compute AND storage
                for (int capIdx = 0; capIdx < tempCapacities.size(); capIdx++) {
                    Capacity cap = tempCapacities.get(capIdx);
                    if (isMatchingPreferences(resource, cap)
                            && resource.cpu <= cap.cpu
                            && resource.memory <= cap.memory
                            && resource.size <= cap.storage) {

                        cap.cpu -= resource.cpu;
                        cap.memory -= resource.memory;
                        cap.storage -= resource.size;

                        allocated++;
                        totalCpu += resource.cpu;
                        totalMemory += resource.memory;
                        totalStorage += resource.size;
                        allocationMap.put(resource, capIdx);
                        break;
                    }
                }
            }
        }

        return new Solution(resourceOrder, allocated, totalCpu, totalMemory, totalStorage, allocationMap);
    }

    // Actually reserve
    private List<Pair<ResourceAgent, Resource>> reserveResources(ResourceAgent agent, Solution solution) {
        List<Pair<ResourceAgent, Resource>> result = new ArrayList<>();

        for (Resource resource : solution.resourceOrder) {
            if (!solution.allocationMap.containsKey(resource)) {
                continue; // Skip resources that couldn't be allocated
            }

            int targetCapIdx = solution.allocationMap.get(resource);

            Capacity targetCap = agent.capacities.get(targetCapIdx);
            boolean isComputeOnly = (resource.size == null);

            if (isComputeOnly) {
                int instances = resource.instances == null ? 1 : resource.instances;

                for (int i = 0; i < instances; i++) {
                    targetCap.reserveCapacity(resource);
                    result.add(Pair.of(agent, resource));
                }
            } else {
                targetCap.reserveCapacity(resource);
                result.add(Pair.of(agent, resource));
            }
        }

        return result;
    }

    private double updateTemperature(double currentTemp, int iteration) {
        switch (coolingSchedule) {
            case EXPONENTIAL:
                exponential_decrease = Math.pow(exponential_decrease, 1.075);
                return currentTemp - exponential_decrease;

            case LOGARITHMIC:
                return currentTemp - Math.log(iteration);

            default: // LINEAR
                return currentTemp - Math.max(1, (INITIAL_TEMP / MAX_ITERATIONS));
        }
    }

    boolean isMatchingPreferences(Resource resource, Capacity capacity) {
        boolean providerMatch = (resource.provider == null || resource.provider.equals(capacity.node.provider));
        boolean locationMatch = (resource.location == null || resource.location.equals(capacity.node.location));
        boolean edgeMatch = (resource.edge == null || resource.edge == capacity.node.edge);

        return providerMatch && locationMatch && edgeMatch;
    }

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
            double allocationScore = (double) resourcesAllocated / requestedResources;

            double cpuFulfillment = totalRequestedCpu > 0 ? (totalCpu / totalRequestedCpu) : 0;
            double memoryFulfillment = totalRequestedMemory > 0 ? ((double) totalMemory / totalRequestedMemory) : 0;
            double storageFulfillment = totalRequestedStorage > 0 ? ((double) totalStorage / totalRequestedStorage) : 0;

            //  System.out.println(allocationScore + " " + cpuFulfillment + " " + memoryFulfillment + " " + storageFulfillment);
            return allocationScore * 10 + cpuFulfillment + memoryFulfillment + storageFulfillment;
        }

        boolean isBetter(Solution solution) {
            return this.resourcesAllocated > solution.resourcesAllocated;
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