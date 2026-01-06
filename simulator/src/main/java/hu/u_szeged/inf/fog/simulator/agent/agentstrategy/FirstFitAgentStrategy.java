package hu.u_szeged.inf.fog.simulator.agent.agentstrategy;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Resource;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import org.apache.commons.lang3.tuple.Pair;
import java.util.ArrayList;
import java.util.List;

public class FirstFitAgentStrategy extends AgentStrategy {
    private final boolean descending;

    public FirstFitAgentStrategy(boolean descending) {
        this.descending = descending;
    }

    public List<Pair<ResourceAgent, Resource>> canFulfill(ResourceAgent agent, List<Resource> resources) {
        List<Resource> sortedResources = sortingResourcesByCpuThenSize(resources);
        List<Pair<ResourceAgent, Resource>> agentResourcePairs = new ArrayList<>();

        for (Resource resource : sortedResources) {
            int instances = resource.instances == null ? 1 : resource.instances;
            double requiredCpu = (resource.cpu != null && resource.cpu > 0) ? resource.cpu : 0;
            long requiredMemory = (resource.memory != null && resource.memory > 0) ? resource.memory : 0;
            long requiredStorage = (resource.size != null && resource.size > 0) ? resource.size : 0;

            List<Capacity> reservedCapacities = new ArrayList<>();

            for (int i = 0; i < instances; i++) {
                boolean reserved = false;

                for (Capacity capacity : agent.capacities) {
                    if (isMatchingPreferences(resource, capacity)
                            && requiredCpu <= capacity.cpu  //TODO: check if < is needed
                            && requiredMemory <= capacity.memory
                            && requiredStorage <= capacity.storage) {

                        capacity.reserveCapacity(resource);
                        reservedCapacities.add(capacity);
                        reserved = true;
                        break;
                    }
                }

                if (!reserved) {
                    break;
                }
            }

            if (reservedCapacities.size() == instances) {
                agentResourcePairs.add(Pair.of(agent, resource));
            } else if (!reservedCapacities.isEmpty()) {
                for (Capacity capacity : reservedCapacities) {
                    capacity.releaseCapacity(resource);
                }
            }
        }

        return agentResourcePairs;
    }

    public List<Resource> sortingResourcesByCpuThenSize(List<Resource> originalResources) {
        List<Resource> sortedResources = new ArrayList<>(originalResources);
        sortedResources.sort((r1, r2) -> {
            double r1EffectiveCpu = (r1.cpu != null) ? r1.cpu * (r1.instances != null ? r1.instances : 1) : -1;
            double r2EffectiveCpu = (r2.cpu != null) ? r2.cpu * (r2.instances != null ? r2.instances : 1) : -1;

            if (r1.cpu != null && r2.cpu != null) {
                double cpu1 = r1.cpu * (r1.instances != null ? r1.instances : 1);
                double cpu2 = r2.cpu * (r2.instances != null ? r2.instances : 1);
                return descending ? Double.compare(cpu2, cpu1) : Double.compare(cpu1, cpu2);
            } else if (r1.cpu == null && r2.cpu == null) {
                return descending ? Double.compare(r2.size, r1.size) : Double.compare(r1.size, r2.size);
            }
            return (r1.cpu == null) ? 1 : -1;
        });

        return sortedResources;
    }
}