package hu.u_szeged.inf.fog.simulator.agent.management;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.forecast.ForecasterManager;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class ForecastBasedSwarmAgent extends GreedyNoiseSwarmAgent {

    public final Map<String, Deque<Double>> windows = new HashMap<>();

    private int triggerPrediction;

    private Map<String, List<Double>> lastPredictions = new HashMap<>();

    private long lastScalingActionMinute = -1;

    public ForecastBasedSwarmAgent(AgentApplication app) {
        super(app);
    }

    @Override
    public void tick(long fires) {
        Pair<Map<NoiseSensor, Double>, Double> noiseSensorCpuLoads = updateCpuMetricsForLastMinute(windows);
        if (!cpuTemperatureSamples.isEmpty()) {
            cpuTemperatureSamples.removeFirst();
        }
        cpuTemperatureSamples.addLast(new CpuTemperatureSample(fires, noiseSensorCpuLoads.getRight()));

        // TODO: remove hardcoded values
        if (triggerPrediction % 30 == 0 && windows.values().stream().anyMatch(w -> w.size() == 128)) {
            Map<String, String> filesForPredictions = null;

            filesForPredictions = writeWindowToCsv(ScenarioBase.RESULT_DIRECTORY);
            Map<String, String> filesWithPredictions = callPredictorScript(filesForPredictions);
            Map<String, List<Double>> predictionValues = loadPredictions(filesWithPredictions);

            deleteFiles(filesForPredictions);
            deleteFiles(filesWithPredictions);

            lastPredictions.clear();
            lastPredictions.putAll(predictionValues);

            //for (Map.Entry<String, List<Double>> entry : predictionValues.entrySet()) {
            //    String deviceId = entry.getKey();
            //    List<Double> values = entry.getValue();
            //}
            //System.exit(0);

        }
        triggerPrediction++;

        scale(noiseSensorCpuLoads.getRight());

        if (this.noiseAppCsvExporter != null){
            this.noiseAppCsvExporter.log(this, noiseSensorCpuLoads);
        }

        shutdown(fires);
    }

    public void scale(double avgCpuLoad) {
        long nowMinute = (long) (Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS);
        if (lastScalingActionMinute >= 0 && nowMinute - lastScalingActionMinute < 3) { // TODO: remove hardcoded value
            return;
        }

        int minContainerCount = (int) Config.NOISE_CLASS_ONFIGURATION.get("minContainerCount");
        double cpuLoadScaleUp = (double) Config.NOISE_CLASS_ONFIGURATION.get("cpuLoadScaleUp");
        double cpuLoadScaleDown = (double) Config.NOISE_CLASS_ONFIGURATION.get("cpuLoadScaleDown");

        // A) Minimum requirement: always keep at least minContainerCount
        if (noiseSensorsWithClassifier.size() < minContainerCount) {
            NoiseSensor ns = selectSensorToStartClassifier();
            if (ns != null) {
                SimLogger.logRun(ns.util.component.id + "'classifier was started at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                        + " min. due to minimum requirement");
                noiseSensorsWithClassifier.add(ns);
            }
            return;
        }

        // B) Predictive thermal scaling: if any running classifier is HOT, start 1 more on a SAFE node
        if (hasUsablePredictions() && hasHotRunningClassifier()) {
            NoiseSensor ns = selectSensorToStartClassifier();
            if (ns != null) {
                SimLogger.logRun(ns.util.component.id + "'classifier was started at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                        + " min. due to predicted thermal risk (next 1 hour)");
                noiseSensorsWithClassifier.add(ns);
            }
            return;
        }

        // C) (Optional) Keep your old load-based scale-up trigger, but place on predicted SAFE node
        if (avgCpuLoad > cpuLoadScaleUp) {
            NoiseSensor ns = selectSensorToStartClassifier();
            if (ns != null) {
                SimLogger.logRun(ns.util.component.id + "'classifier was started at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                        + " min. due to large load");
                noiseSensorsWithClassifier.add(ns);
            }
            return;
        }

        // D) Downscale (conservative):
        // only if (1) above minimum, (2) low load over window, (3) no HOT running classifier
        if (noiseSensorsWithClassifier.size() > minContainerCount
                && getAverageClassifierCpuLoadOverWindow() < cpuLoadScaleDown
                && !hasHotRunningClassifier()) {

            NoiseSensor ns = selectSensorToStopClassifier();
            if (ns != null) {
                SimLogger.logRun(ns.util.component.id + "'classifier was turned off at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                        + " min. due to small load (and no predicted thermal risk)");
                noiseSensorsWithClassifier.remove(ns);
            }
        }
    }

    private boolean hasUsablePredictions() {
        return !lastPredictions.isEmpty()
                && lastPredictions.values().stream().anyMatch(v -> v != null && v.size() >= 60);
    }

    private NoiseSensor selectSensorToStartClassifier() {
        NoiseSensor best = null;
        double bestTmax = Double.POSITIVE_INFINITY;

        for (Object object : observedAppComponents) {
            if (object instanceof NoiseSensor sensor){
                if (noiseSensorsWithClassifier.contains(sensor)) continue;

                if (!isSafeForScaleOut(sensor)) continue;

                String deviceId = sensor.util.component.id;
                Double tmax = getMaxPredictedCpuTempNextHour(deviceId);

                if (tmax < bestTmax) {
                    bestTmax = tmax;
                    best = sensor;
                }
            }
        }

        if (best == null) {
            best = findSensorByCpuTemperature(true);
        }

        return best;
    }

    private NoiseSensor selectSensorToStopClassifier() {
        NoiseSensor worst = null;
        double worstTmax = Double.NEGATIVE_INFINITY;

        for (NoiseSensor sensor : noiseSensorsWithClassifier) {
            Double tmax = getMaxPredictedCpuTempNextHour(sensor.util.component.id);
            if (tmax > worstTmax) {
                worstTmax = tmax;
                worst = sensor;
            }
        }

        if (worst == null) {
            worst = findSensorByCpuTemperature(false);
        }

        return worst;
    }

    private boolean isHot(NoiseSensor sensor) {
        Double maxTempNextHour = getMaxPredictedCpuTempNextHour(sensor.util.component.id);
        return maxTempNextHour >= 78.0; // TODO: remove hardcoded value
    }

    private boolean hasHotRunningClassifier() {
        for (NoiseSensor sensor : noiseSensorsWithClassifier) {
            if (isHot(sensor)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSafeForScaleOut(NoiseSensor sensor) {
        Double maxTempNextHour = getMaxPredictedCpuTempNextHour(sensor.util.component.id);
        return maxTempNextHour < 78.0;
    }

    private double getMaxPredictedCpuTempNextHour(String deviceId) {
        if (deviceId == null) {
            return Double.POSITIVE_INFINITY;
        }

        List<Double> prediction = lastPredictions.get(deviceId);
        if (prediction == null || prediction.size() < 60) {
            return Double.POSITIVE_INFINITY;
        }

        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 60; i++) {
            Double value = prediction.get(i);
            if (value != null && value > max) {
                max = value;
            }
        }

        if (max == Double.NEGATIVE_INFINITY) {
            return Double.POSITIVE_INFINITY;
        }

        return max;
    }

    public Map<String, List<Double>> loadPredictions(Map<String, String> filesWithPredictions) {
        Map<String, List<Double>> predictions = new HashMap<>();

        for (Map.Entry<String, String> entry : filesWithPredictions.entrySet()) {
            String deviceId = entry.getKey();
            String filePath = entry.getValue();

            List<Double> values = new ArrayList<>();

            try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                String line;
                br.readLine();

                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    String[] parts = line.split(",");
                    if (parts.length < 2) continue;

                    double val = Double.parseDouble(parts[1]);
                    values.add(val);
                }

                predictions.put(deviceId, values);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return predictions;
    }


    private Map<String, String> callPredictorScript(Map<String, String> files) {
        if (!SystemUtils.IS_OS_LINUX) {
            SimLogger.logError("Unsupported operating system");
        }

        Map<String, String> resultFiles = new ConcurrentHashMap<>();

        int threads = Math.min(files.size(), Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Future<?>> futures = new ArrayList<>();

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String deviceId = entry.getKey();
            String inputPath = entry.getValue();

            futures.add(executor.submit(() -> {
                try {
                    Path inputFile = Paths.get(inputPath);
                    String fileName = inputFile.getFileName().toString();
                    String predictionName = fileName.replace("window-for-prediction.csv", "predicted-values.csv");
                    Path outputFile = inputFile.resolveSibling(predictionName);
                    String outputPath = outputFile.toString();

                    ForecasterManager.getInstance().predict(inputPath, outputPath);

                    resultFiles.put(deviceId, outputPath);
                } catch (IOException | InterruptedException e) {
                    System.err.println("Prediction failed for " + deviceId + " (" + inputPath + ")");
                    e.printStackTrace();
                }
            }));
        }

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();

        return resultFiles;
    }

    private void deleteFiles(Map<String, String> files) {
        for (String path : files.values()) {
            File f = new File(path);
            if (f.exists()) {
                boolean deleted = f.delete();
                if (!deleted) {
                    SimLogger.logError("File " + path + " cannot be deleted");
                }
            }
        }
    }

    public Map<String, String> writeWindowToCsv(String outputDir) {
        LocalDateTime startTime = LocalDateTime.of(2023, 10, 1, 0, 0);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:00");

        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, Deque<Double>> entry : windows.entrySet()) {
            String sensorId = entry.getKey();
            Deque<Double> window = entry.getValue();

            String filePath = outputDir + File.separator + sensorId + "-" + Timed.getFireCount() + "-window-for-prediction.csv";

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {

                bw.write("date,noise-sensor-temperature,separated-cpu-load,sound-values,no-of-file-migrations,no-of-files-to-process");
                bw.newLine();

                int index = 0;
                for (double value : window) {
                    LocalDateTime timestamp = startTime.plusMinutes(index);
                    bw.write(
                            fmt.format(timestamp) + "," +
                                    value + "," +
                                    "0,0,0,0"
                    );
                    bw.newLine();

                    index++;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            result.put(sensorId, filePath);

        }
        return result;
    }

    String printWindow() {
        StringBuilder sb = new StringBuilder();
        sb.append("Sliding windows {\n");

        for (Map.Entry<String, Deque<Double>> entry : windows.entrySet()) {
            sb.append("  ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\n");
        }

        sb.append("}");
        return sb.toString();
    }
}
