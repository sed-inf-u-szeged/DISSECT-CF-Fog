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
    //0.7 makes the quality more important than the distance => better quality >> better distance
    public static double lambda;

    static double getScore(StandardResourceAgent node1, StandardResourceAgent node2, AgentApplication application) {
        //Normalize
        double node1BW = ResourceAgent.normalize((double)ResourceAgent.getAvgBW(node1), (double)ResourceAgent.minBW, (double)ResourceAgent.maxBW);
        double node2BW = ResourceAgent.normalize((double)ResourceAgent.getAvgBW(node2), (double)ResourceAgent.minBW, (double)ResourceAgent.maxBW);

        double node1Lat = ResourceAgent.normalize(ResourceAgent.getAvgLatency(node1).doubleValue(), ResourceAgent.minLatency.doubleValue(), ResourceAgent.maxLatency.doubleValue());
        double node2Lat = ResourceAgent.normalize(ResourceAgent.getAvgLatency(node2).doubleValue(), ResourceAgent.minLatency.doubleValue(), ResourceAgent.maxLatency.doubleValue());

        //Price
        double node1Price = ResourceAgent.normalize(node1.getPrice(), ResourceAgent.minPrice, ResourceAgent.maxPrice);
        double node2Price = ResourceAgent.normalize(node2.getPrice(), ResourceAgent.minPrice, ResourceAgent.maxPrice);

        //Energy
        //getMin... => returns idle
        //getRange... => returns max-idle
        double node1Energy = ResourceAgent.normalize(ResourceAgent.getAvgEnergy(node1), ResourceAgent.minEnergy, ResourceAgent.maxEnergy);
        double node2Energy = ResourceAgent.normalize(ResourceAgent.getAvgEnergy(node2), ResourceAgent.minEnergy, ResourceAgent.maxEnergy);


        //Quality (latency, price, energy is lower = better)
        double qBW = (node1BW + node2BW) / 2.0;
        double qLat = (1.0 - (node1Lat + node2Lat) / 2.0);
        double qPrice = (1.0 - (node1Price + node2Price) / 2.0);
        double qEnergy = (1.0 - (node1Energy + node2Energy) / 2.0);

        double quality =
                qBW * application.bandwidth +
                qLat * application.latency +
                qPrice * application.price +
                qEnergy * application.energy;


        //Logical distance (penalty: the higher the difference, the higher the penalty, squared ensures high penalty + positive result)
        double distance =
                application.bandwidth * Math.pow(node1BW - node2BW, 2) +
                application.latency * Math.pow(node1Lat - node2Lat, 2) +
                application.price * Math.pow(node1Price - node2Price, 2) +
                application.energy * Math.pow(node1Energy - node2Energy, 2);

        //(weighted) square distance sum thing; squared + divided by num of distance parts (inverse generational distance)
        double IGD = Math.sqrt(distance)/4;

        //Score
        //Base penalty, quality corrects it. The more it corrects => the lower the final score => the better, as we need to return lower=better
        //return distance - lambda * quality;
        return IGD - lambda * quality;
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
