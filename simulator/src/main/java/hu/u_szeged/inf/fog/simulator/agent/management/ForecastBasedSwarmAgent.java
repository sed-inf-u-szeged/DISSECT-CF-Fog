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

    private final Map<String, List<Double>> lastPredictions = new HashMap<>();

    public final ArrayList<Long> forecastingTimes = new ArrayList<>();

    private long lastScalingActionMinute = -1;

    public ForecastBasedSwarmAgent(AgentApplication app) {
        super(app);
        decisionType.put("scale-up-predictive", 0);
    }

    @Override
    public void tick(long fires) {
        Pair<Map<NoiseSensor, Double>, Double> noiseSensorCpuLoads = updateCpuMetricsForLastMinute(windows);
        if (!cpuTemperatureSamples.isEmpty()) {
            cpuTemperatureSamples.removeFirst();
        }
        cpuTemperatureSamples.addLast(new CpuTemperatureSample(fires, noiseSensorCpuLoads.getRight()));

        // TODO: remove hardcoded values
        if (triggerPrediction % 30 == 0 && windows.values().stream().allMatch(w -> w.size() == 128)) {
            forecastingTimes.add(fires / ScenarioBase.MINUTE_IN_MILLISECONDS);
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

        if (!lastPredictions.isEmpty()) {
            for (List<Double> entry : lastPredictions.values()) {
                entry.remove(0);
            }
        }

        shutdown(fires);
    }

    public void scale(double avgCpuLoad) {
        long nowMinute = (long) (Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS);
        long cooldown = (long) Config.NOISE_CLASS_CONFIGURATION.get("cpuTimeWindow") / ScenarioBase.MINUTE_IN_MILLISECONDS;

        int minContainerCount = (int) Config.NOISE_CLASS_CONFIGURATION.get("minContainerCount");
        double cpuLoadScaleUp = (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuLoadScaleUp");
        double cpuLoadScaleDown = (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuLoadScaleDown");

        final Map<String, Double> tmaxCache = !lastPredictions.isEmpty() ? buildTmaxCache() : null;

        // minimum requirement
        while (noiseSensorsWithClassifier.size() < minContainerCount) {
            NoiseSensor ns = selectSensorToStartClassifier(tmaxCache);
            if (ns == null) {
                break;
            }
            noiseSensorsWithClassifier.add(ns);
            decisionType.compute("scale-up-min", (k, v) -> v + 1);
            SimLogger.logRun(ns.util.component.id + "'classifier was started at: "
                    + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                    + " min. due to minimum requirement");
        }

        if (noiseSensorsWithClassifier.size() < minContainerCount) {
            return;
        }

        if (lastScalingActionMinute >= 0 && nowMinute - lastScalingActionMinute < cooldown) {
            return;
        }

        // predictive scaling
        if (!lastPredictions.isEmpty() && hasHotRunningClassifier(tmaxCache)) {
            NoiseSensor ns = selectSensorToStartClassifier(tmaxCache);
            if (ns != null) {
                noiseSensorsWithClassifier.add(ns);
                lastScalingActionMinute = nowMinute;
                decisionType.compute("scale-up-predictive", (k, v) -> v + 1);
                SimLogger.logRun(ns.util.component.id + "'classifier was started at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                        + " min. due to predicted thermal risk");
            }
            return;
        }

        // load-based scale-up trigger
        if (avgCpuLoad > cpuLoadScaleUp) {
            NoiseSensor ns = selectSensorToStartClassifier(tmaxCache);
            if (ns != null) {
                noiseSensorsWithClassifier.add(ns);
                lastScalingActionMinute = nowMinute;
                decisionType.compute("scale-up-load", (k, v) -> v + 1);
                SimLogger.logRun(ns.util.component.id + "'classifier was started at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                        + " min. due to large load");
            }
            return;
        }

        // downscale
        if (noiseSensorsWithClassifier.size() > minContainerCount
                && getAverageClassifierCpuLoadOverWindow() < cpuLoadScaleDown) {

            NoiseSensor ns = selectSensorToStopClassifier(tmaxCache);
            if (ns != null) {
                noiseSensorsWithClassifier.remove(ns);
                lastScalingActionMinute = nowMinute;
                decisionType.compute("scale-down-load", (k, v) -> v + 1);
                SimLogger.logRun(ns.util.component.id + "'classifier was turned off at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                        + " min. due to small load");
            }
        }
    }

    private NoiseSensor selectSensorToStartClassifier(Map<String, Double> tmaxCache) {
        NoiseSensor bestSafe = null;
        double bestSafeCurrent = Double.MAX_VALUE;
        double bestSafeTmax = Double.MAX_VALUE;

        NoiseSensor coldest = null;
        double coldestCurrent = Double.MAX_VALUE;

        double cpuTempThreshold = (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold");
        double safeTmax = 78.0;

        for (Object object : observedAppComponents) {
            if (!(object instanceof NoiseSensor sensor)) continue;
            if (noiseSensorsWithClassifier.contains(sensor)) continue;

            double current = sensor.cpuTemperature;

            if (current >= cpuTempThreshold) {
                continue;
            }

            if (current < coldestCurrent) {
                coldestCurrent = current;
                coldest = sensor;
            }

            if (tmaxCache != null) {
                double tmax = tmaxCache.get(sensor.util.component.id);

                if (tmax <= safeTmax) {
                    if (current < bestSafeCurrent || (current == bestSafeCurrent && tmax < bestSafeTmax)) {
                        bestSafeCurrent = current;
                        bestSafeTmax = tmax;
                        bestSafe = sensor;
                    }
                }
            }
        }

        return (bestSafe != null) ? bestSafe : coldest;
    }

    private NoiseSensor selectSensorToStopClassifier(Map<String, Double> tmaxCache) {
        if (lastPredictions.isEmpty()) {
            return findSensorByCpuTemperature(false);
        }

        NoiseSensor worst = null;
        double worstTmax = Double.MIN_VALUE;

        for (NoiseSensor sensor : noiseSensorsWithClassifier) {
            Double tmax = tmaxCache.get(sensor.util.component.id);
            if (tmax > worstTmax) {
                worstTmax = tmax;
                worst = sensor;
            }
        }

        return worst;
    }

    private boolean hasHotRunningClassifier(Map<String, Double> tmaxCache) {
        if(!lastPredictions.isEmpty()){
            for (NoiseSensor sensor : noiseSensorsWithClassifier) {
                double maxTempNextHour = tmaxCache.get(sensor.util.component.id);
                if (maxTempNextHour >= 78.0) { // TODO: remove hardcoded value
                    return true;
                }
            }
        }
        return false;
    }

    private double getMaxPredictedCpuTempNextHour(String deviceId) {
        List<Double> prediction = lastPredictions.get(deviceId);

        double max = Double.MIN_VALUE;
        for (int i = 0; i < 60; i++) {
            Double value = prediction.get(i);
            if (value != null && value > max) {
                max = value;
            }
        }

        return max;
    }

    private Map<String, Double> buildTmaxCache() {
        Map<String, Double> tmax = new HashMap<>();

        for (Object o : observedAppComponents) {
            if (o instanceof NoiseSensor s) {
                tmax.put(s.util.component.id, getMaxPredictedCpuTempNextHour(s.util.component.id));
            }
        }

        return tmax;
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
