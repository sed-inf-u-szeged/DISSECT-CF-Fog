package hu.u_szeged.inf.fog.simulator.util;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.iot.Task;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class TaskStatistics {

    private static final Pattern TASK_NUMBER_PATTERN = Pattern.compile("\\d+");

    private static String runName = "default";
    private static String strategy = "unknown";
    private static int deviceCount = -1;

    public static final Map<Integer, String> taskTypes = new HashMap<>();
    public static final Map<Integer, Long> creationTimes = new HashMap<>();
    public static final Map<Integer, Long> completionTimes = new HashMap<>();
    public static final Map<Integer, Long> deadlines = new HashMap<>();

    private TaskStatistics() {}

    public static void configure(String runName, String strategy, int deviceCount) {
        TaskStatistics.runName = runName;
        TaskStatistics.strategy = strategy;
        TaskStatistics.deviceCount = deviceCount;
    }

    public static void registerTask(Task task) {
        int taskNumber = extractFirstTaskNumber(task.id);

        taskTypes.put(taskNumber, task.type.name());
        creationTimes.put(taskNumber, task.created);
        deadlines.put(taskNumber, task.deadline);
    }

    public static void registerCompletion(Task task) {
        long completionTime = Timed.getFireCount();

        for (Integer taskNumber : extractTaskNumbers(task.id)) {
            completionTimes.put(taskNumber, completionTime);
        }
    }

    public static List<Integer> extractTaskNumbers(String taskId) {
        List<Integer> result = new ArrayList<>();
        Matcher matcher = TASK_NUMBER_PATTERN.matcher(taskId);

        while (matcher.find()) {
            result.add(Integer.parseInt(matcher.group()));
        }

        return result;
    }

    private static int extractFirstTaskNumber(String taskId) {
        List<Integer> numbers = extractTaskNumbers(taskId);

        if (numbers.isEmpty()) {
            throw new IllegalArgumentException("Task id does not contain task number: " + taskId);
        }

        return numbers.get(0);
    }

    public static void saveToCsv(String path) {
        File file = new File(path, "task_statistics.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("runName,strategy,deviceCount,taskId,taskType,created,completed,deadline,e2eLatency,deadlineMet");

            Set<Integer> ids = new TreeSet<>(creationTimes.keySet());

            for (Integer taskId : ids) {
                Long created = creationTimes.get(taskId);
                Long completed = completionTimes.get(taskId);
                Long deadline = deadlines.get(taskId);

                Long latency = completed == null ? null : completed - created;
                Boolean deadlineMet = completed == null || deadline == null ? null : completed <= deadline;

                writer.println(
                        runName + "," +
                                strategy + "," +
                                deviceCount + "," +
                                taskId + "," +
                                value(taskTypes.get(taskId)) + "," +
                                value(created) + "," +
                                value(completed) + "," +
                                value(deadline) + "," +
                                value(latency) + "," +
                                value(deadlineMet)
                );
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save task statistics", e);
        }
    }

    private static String value(Object o) {
        return o == null ? "" : o.toString();
    }
}