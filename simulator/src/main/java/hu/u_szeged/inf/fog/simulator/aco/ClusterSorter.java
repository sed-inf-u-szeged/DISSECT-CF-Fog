package hu.u_szeged.inf.fog.simulator.aco;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.StandardResourceAgent;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.AbstractMap;
import java.util.stream.Collectors;

public class ClusterSorter {
    double getScore(StandardResourceAgent nodeAgent, StandardResourceAgent centerAgent, AgentApplication application) {
        double nodeBW = ResourceAgent.normalize((double)ResourceAgent.getAvgBW(nodeAgent), (double)ResourceAgent.minBW, (double)ResourceAgent.maxBW);
        double centerBW = ResourceAgent.normalize((double)ResourceAgent.getAvgBW(centerAgent), (double)ResourceAgent.minBW, (double)ResourceAgent.maxBW);
        double avgBW = (nodeBW + centerBW)/2;

        double nodeLat = ResourceAgent.normalize(ResourceAgent.getAvgLatency(nodeAgent).doubleValue(), ResourceAgent.minLatency.doubleValue(), ResourceAgent.maxLatency.doubleValue());
        double centerLat = ResourceAgent.normalize(ResourceAgent.getAvgLatency(centerAgent).doubleValue(), ResourceAgent.minLatency.doubleValue(), ResourceAgent.maxLatency.doubleValue());
        double avgLat = (nodeLat + centerLat)/2;

        //Price
        double nodePrice = ResourceAgent.normalize(nodeAgent.hourlyPrice, ResourceAgent.minPrice, ResourceAgent.maxPrice);
        double centerPrice = ResourceAgent.normalize(centerAgent.hourlyPrice, ResourceAgent.minPrice, ResourceAgent.maxPrice);
        double avgPrice = (nodePrice + centerPrice)/2;

        //Energy
        //getMin... => returns idle
        //getRange... => returns max-idle
        double nodeEnergy = ResourceAgent.normalize(ResourceAgent.getAvgEnergy(nodeAgent), ResourceAgent.minEnergy, ResourceAgent.maxEnergy);
        double centerEnergy = ResourceAgent.normalize(ResourceAgent.getAvgEnergy(centerAgent), ResourceAgent.minEnergy, ResourceAgent.maxEnergy);
        double avgEnergy = (nodeEnergy + centerEnergy)/2;

        return  (1 - avgBW) * application.bandwidthPriority +
                avgLat * application.latencyPriority +
                avgEnergy * application.energyPriority +
                avgPrice * application.pricePriority;
    }

    public double calculateScore(ArrayList<StandardResourceAgent> list, AgentApplication application) {
        double totalDistance = 0.0;
        int totalPairs = 0;

        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                StandardResourceAgent agent1 = list.get(i);
                StandardResourceAgent agent2 = list.get(j);
                totalDistance += getScore(agent1, agent2, application);
                totalPairs++;
            }
        }

        return totalPairs == 0 ? 0.0 : totalDistance / totalPairs;
    }

    public List<ArrayList<StandardResourceAgent>> sortClustersByScore(
            HashMap<Integer, ArrayList<StandardResourceAgent>> clusters, AgentApplication application) {

        return clusters.entrySet().stream()
                .map(entry -> {
                    // ComputingAppliance key = entry.getKey();
                    ArrayList<StandardResourceAgent> value = entry.getValue();

                    ArrayList<StandardResourceAgent> mergedList = new ArrayList<>();
                    //  mergedList.add(key);
                    mergedList.addAll(value);

                    double avgDistance = calculateScore(mergedList, application);

                    return new AbstractMap.SimpleEntry<>(mergedList, avgDistance);
                })
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
