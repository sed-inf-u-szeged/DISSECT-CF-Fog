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
            if (resource.size == null) { // compute type

                int instances = resource.instances == null ? 1 : resource.instances;

                List<Capacity> reservedCapacity = new ArrayList<>();
                for (int i = 0; i < instances; i++) {
                    for (Capacity capacity : agent.capacities) {
                        if ((resource.provider == null || resource.provider.equals(capacity.node.provider))
                                && (resource.location == null || resource.location.equals(capacity.node.location))
                                && resource.cpu <= capacity.cpu && resource.memory <= capacity.memory) {

                            capacity.reserveCapacity(resource);
                            reservedCapacity.add(capacity);
                            break;
                        }
                    }
                }

                if (instances != reservedCapacity.size() && reservedCapacity.size() > 0) {
                    for (Capacity capacity : reservedCapacity) {
                        capacity.releaseCapacity(resource);
                    }
                } else if (instances == reservedCapacity.size()) {
                    agentResourcePairs.add(Pair.of(agent, resource));
                }

            } else { // storage type
                for (Capacity capacity : agent.capacities) {
                    if ((resource.provider == null || resource.provider.equals(capacity.node.provider))
                            && (resource.location == null || resource.location.equals(capacity.node.location))
                            && resource.size <= capacity.storage) {
                        capacity.reserveCapacity(resource);
                        agentResourcePairs.add(Pair.of(agent, resource));
                    }
                }
            }
        }

        return agentResourcePairs;
    }

    public List<Resource> sortingResourcesByCpuThenSize(List<Resource> originalResources) {
        List<Resource> sortedResources = new ArrayList<>(originalResources);

        sortedResources.sort((r1, r2) -> {
            if (r1.cpu != null && r2.cpu != null) {
                if (r1.instances != null) {
                    r1.cpu *= r1.instances;
                }
                if (r2.instances != null) {
                    r2.cpu *= r2.instances;
                }
                return descending ? Double.compare(r2.cpu, r1.cpu) : Double.compare(r1.cpu, r2.cpu);
            } else if (r1.cpu == null && r2.cpu == null) {
                return descending ? Double.compare(r2.size, r1.size) : Double.compare(r1.size, r2.size);
            }
            return (r1.cpu == null) ? 1 : -1;
        });

        return sortedResources;
    }
}