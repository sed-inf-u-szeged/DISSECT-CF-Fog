package hu.u_szeged.inf.fog.simulator.agent.strategy;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import org.apache.commons.lang3.tuple.Pair;
import java.util.*;

public class SimulatedAnnealing extends AgentStrategy {

    private Random random = new Random();

    // SA parameters - keep it simple
    private static final double INITIAL_TEMP = 100.0;
    private static final double COOLING_RATE = 0.98;
    private static final int MAX_ITERATIONS = 50;

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

        for (int iter = 1; iter <= MAX_ITERATIONS && temp > 1; iter++) {
            List<Resource> neighborOrder = new ArrayList<>(currentOrder);
            int i = random.nextInt(neighborOrder.size());
            int j = random.nextInt(neighborOrder.size());
            Collections.swap(neighborOrder, i, j);
            Solution neighborSolution = tryAllocate(agent, neighborOrder);

            double neighborScore = neighborSolution.getScore(resources.size(), totalRequestedCpu, totalRequestedMemory, totalRequestedStorage);
            double currentScore = currentSolution.getScore(resources.size(), totalRequestedCpu, totalRequestedMemory, totalRequestedStorage);

            System.out.println(neighborSolution.resourcesAllocated + " vs " + currentSolution.resourcesAllocated + " of " + resources.size());
            if (neighborScore > currentScore) {
                // Better solution - always accept
                bestSolution = neighborSolution.copy();
                //System.out.println("Iter " + iter + ": BETTER (score: " +
                //      String.format("%.4f", neighborScore) + " > " +
                //    String.format("%.4f", currentScore) + ")");
            } else {
                // Worse solution - accept with probability based on temperature
                double delta = neighborScore - currentScore; // This will be negative
                double acceptanceProbability = Math.exp(delta / temp);
                //System.out.println("accptProb: " + acceptanceProbability);
                //System.out.println("delta: " + delta);

                if (random.nextDouble() < acceptanceProbability) {
                    bestSolution = neighborSolution.copy();
                    System.out.println("Iter " + iter + ": WORSE but accepted (score: " +
                            String.format("%.4f", neighborScore) + " < " +
                            String.format("%.4f", currentScore) +
                            ", prob=" + String.format("%.4f", acceptanceProbability) +
                            ", temp=" + String.format("%.1f", temp) + ")");
                }
            }

            temp *= COOLING_RATE;
        }

        System.out.println("\n=== SA COMPLETE ===");
        System.out.println("Best: " + bestSolution);

        // Step 3: Actually allocate and reserve the best solution
        // Use the allocation map from the best solution
        return allocateAndReserve(agent, bestSolution);
    }

    // Try to allocate resources in given order WITHOUT reserving
    private Solution tryAllocate(ResourceAgent agent, List<Resource> resourceOrder) {
        int allocated = 0;
        double totalCpu = 0;
        long totalMemory = 0;
        long totalStorage = 0;

        // Track which capacity node each resource gets allocated to
        Map<Resource, Integer> allocationMap = new HashMap<>();

        // Create temporary copy of capacities to simulate allocation
        List<Capacity> tempCapacities = new ArrayList<>();
        for (Capacity cap : agent.capacities) {
            Capacity temp = new Capacity(cap.node, cap.cpu, cap.memory, cap.storage);
            tempCapacities.add(temp);
        }

        for (Resource resource : resourceOrder) {
            boolean isComputeOnly = (resource.size == null);
            boolean isStorageOnly = (resource.size != null && (resource.cpu == null || resource.cpu == 0));
            boolean isHybrid = (resource.size != null && resource.cpu != null && resource.cpu > 0);

            if (isComputeOnly) {
                // Pure compute resource
                int instances = resource.instances == null ? 1 : resource.instances;
                int found = 0;
                List<Integer> usedCapIndices = new ArrayList<>();

                for (int i = 0; i < instances; i++) {
                    for (int capIdx = 0; capIdx < tempCapacities.size(); capIdx++) {
                        Capacity cap = tempCapacities.get(capIdx);
                        if ((resource.provider == null || resource.provider.equals(cap.node.provider))
                                && (resource.location == null || resource.location.equals(cap.node.location))
                                && (resource.edge == null || resource.edge == cap.node.edge)
                                && resource.cpu <= cap.cpu
                                && resource.memory <= cap.memory) {

                            // Temporarily reduce capacity
                            cap.cpu -= resource.cpu;
                            cap.memory -= resource.memory;
                            usedCapIndices.add(capIdx);
                            found++;
                            break;
                        }
                    }
                }

                if (found == instances) {
                    allocated++;
                    totalCpu += resource.cpu * instances;
                    totalMemory += resource.memory * instances;
                    // Store the first capacity index used for this resource
                    if (!usedCapIndices.isEmpty()) {
                        allocationMap.put(resource, usedCapIndices.get(0));
                    }
                }

            } else if (isStorageOnly) {
                // Pure storage resource
                for (int capIdx = 0; capIdx < tempCapacities.size(); capIdx++) {
                    Capacity cap = tempCapacities.get(capIdx);
                    if ((resource.provider == null || resource.provider.equals(cap.node.provider))
                            && (resource.location == null || resource.location.equals(cap.node.location))
                            && (resource.edge == null || resource.edge == cap.node.edge)
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
                    System.out.println(resource.edge + " " + cap.node.edge);
                    if ((resource.provider == null || resource.provider.equals(cap.node.provider))
                            && (resource.location == null || resource.location.equals(cap.node.location))
                            && (resource.edge == null || resource.edge == cap.node.edge)
                            && resource.cpu <= cap.cpu
                            && resource.memory <= cap.memory
                            && resource.size <= cap.storage) {

                        // Reduce ALL required capacities
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

    // Actually allocate and reserve for real using the allocation map
    private List<Pair<ResourceAgent, Resource>> allocateAndReserve(ResourceAgent agent, Solution solution) {
        List<Pair<ResourceAgent, Resource>> result = new ArrayList<>();

        for (Resource resource : solution.resourceOrder) {
            if (!solution.allocationMap.containsKey(resource)) {
                continue; // Skip resources that couldn't be allocated
            }

            int targetCapIdx = solution.allocationMap.get(resource);

            if (targetCapIdx >= agent.capacities.size()) {
                continue;
            }

            Capacity targetCap = agent.capacities.get(targetCapIdx);
            boolean isComputeOnly = (resource.size == null);
            boolean isHybrid = (resource.size != null && resource.cpu != null && resource.cpu > 0);

            if (isComputeOnly) {
                // Pure compute resource
                int instances = resource.instances == null ? 1 : resource.instances;
                List<Capacity> reserved = new ArrayList<>();

                for (int i = 0; i < instances; i++) {
                    for (Capacity cap : agent.capacities) {
                        if ((resource.provider == null || resource.provider.equals(cap.node.provider))
                                && (resource.location == null || resource.location.equals(cap.node.location))
                                && resource.cpu <= cap.cpu
                                && resource.memory <= cap.memory
                                && resource.edge == cap.node.edge) {

                            cap.reserveCapacity(resource);
                            reserved.add(cap);
                            break;
                        }
                    }
                }

                if (reserved.size() == instances) {
                    result.add(Pair.of(agent, resource));
                } else if (reserved.size() > 0) {
                    for (Capacity cap : reserved) {
                        cap.releaseCapacity(resource);
                    }
                }

            } else {
                // Storage or hybrid resource
                // Verify the target capacity can still handle this resource
                boolean matchesProviderLocation =
                        (resource.provider == null || resource.provider.equals(targetCap.node.provider))
                                && (resource.location == null || resource.location.equals(targetCap.node.location));

                boolean hasStorage = resource.size <= targetCap.storage;

                boolean hasCompute = true;
                if (isHybrid) {
                    hasCompute = resource.cpu <= targetCap.cpu && resource.memory <= targetCap.memory;
                }

                if (matchesProviderLocation && hasStorage && hasCompute) {
                    targetCap.reserveCapacity(resource);
                    result.add(Pair.of(agent, resource));
                }
            }
        }

        return result;
    }

    private class Solution {
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
            return allocationScore + cpuFulfillment + memoryFulfillment + storageFulfillment;
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