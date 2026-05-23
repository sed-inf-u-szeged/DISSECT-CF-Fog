package hu.u_szeged.inf.fog.simulator.util;

import java.io.*;

public class RunStatistics {

    private RunStatistics() {}

    public static void saveToCsv(String path, String runName, String strategy, int deviceCount, long runtimeMs) {
        File file = new File(path, "run_statistics.csv");

        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("runName,strategy,deviceCount,runtimeMs,usedMemoryMb");
            writer.println(runName + "," + strategy + "," + deviceCount + "," + runtimeMs + "," + usedMemoryMb);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save run statistics", e);
        }
    }
}