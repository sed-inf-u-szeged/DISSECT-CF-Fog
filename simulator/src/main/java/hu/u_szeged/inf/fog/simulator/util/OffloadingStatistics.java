package hu.u_szeged.inf.fog.simulator.util;

import hu.u_szeged.inf.fog.simulator.iot.Task;

import java.io.*;
import java.util.*;

public class OffloadingStatistics {

    private static String runName = "default";
    private static String strategy = "unknown";
    private static int deviceCount = -1;

    private static final List<String> rows = new ArrayList<>();

    private OffloadingStatistics() {}

    public static void configure(String runName, String strategy, int deviceCount) {
        OffloadingStatistics.runName = runName;
        OffloadingStatistics.strategy = strategy;
        OffloadingStatistics.deviceCount = deviceCount;
    }

    public static void registerDecision(Task task, String decision, String targetLayer, String targetName) {
        // decision: LOCAL vagy OFFLOAD
        // targetLayer: LOCAL, FOG, CLOUD
        rows.add(
                runName + "," +
                        strategy + "," +
                        deviceCount + "," +
                        task.id + "," +
                        task.type.name() + "," +
                        decision + "," +
                        targetLayer + "," +
                        targetName
        );
    }

    public static void saveToCsv(String path) {
        File file = new File(path, "offloading_statistics.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("runName,strategy,deviceCount,taskId,taskType,decision,targetLayer,targetName");

            for (String row : rows) {
                writer.println(row);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save offloading statistics", e);
        }
    }
}