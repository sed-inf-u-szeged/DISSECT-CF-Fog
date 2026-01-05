package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.forecast.ForecasterManager;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.NoiseAppCsvExporter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.lang3.SystemUtils;

public class SwarmAgent extends Timed {

    public ArrayList<Object> components;
    
    public ArrayList<NoiseSensor> noiseSensorsWithClassifier;
    
    private int currentIndex;
    
    public static ArrayList<SwarmAgent> allSwarmAgents = new ArrayList<>();
    
    private final Deque<CpuTempSample> cpuTempSamples;
    
    public AgentApplication app;
    
    private int triggerPrediction;

    public final Map<String, Deque<Double>> windows = new HashMap<>();


    public SwarmAgent(AgentApplication app) {
        this.app = app;
        this.components = new ArrayList<>();
        cpuTempSamples = new ArrayDeque<>();
        this.noiseSensorsWithClassifier = new ArrayList<>();
        allSwarmAgents.add(this);
        subscribe(this.app.configuration.get("samplingFreq").longValue());
        NoiseAppCsvExporter.getInstance();
    }
    
    public void registerComponent(Object component) {
        this.components.add(component);
        if (component instanceof NoiseSensor) {
            NoiseSensor ns = (NoiseSensor) component;
            windows.putIfAbsent(app.getComponentName(ns.util.resource.name), new ArrayDeque<>());
        }
    }

    @Override
    public void tick(long fires) {
        double avgCpuLoad = avgCpu();
        cpuTempSamples.addLast(new CpuTempSample(Timed.getFireCount(), avgCpuLoad));
        if (!cpuTempSamples.isEmpty()) {
            cpuTempSamples.removeFirst();
        }
        this.scale(avgCpuLoad);
        if (triggerPrediction % 6 == 0) {
            // TODO: calculate avg values, not just the last one
            NoiseAppCsvExporter.log();
        }

        if (triggerPrediction % (64 * 6) == 0 && windows.values().stream().anyMatch(w -> w.size() == 128)) {
            Map<String, String> files = null;
            try {
                files = writeWindowToCsv(ScenarioBase.resultDirectory);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Map<String, String> filesWithPredictions = callPredictorScript(files);
            Map<String, List<Double>> predictionValues = loadPredictions(filesWithPredictions);

            deleteFiles(files);
            deleteFiles(filesWithPredictions);

            for (Map.Entry<String, List<Double>> entry : predictionValues.entrySet()) {
                String deviceId = entry.getKey();
                List<Double> values = entry.getValue();
                // TODO: scaling logic
            }
            //System.exit(0);

        }
        triggerPrediction++;
    }
    
    private void deleteFiles(Map<String, String> files) {
        for (String path : files.values()) {
            File f = new File(path);
            if (f.exists()) {
                boolean deleted = f.delete();
                if (!deleted) {
                    System.err.println("Warning: could not delete file " + path);
                }
            }
        }
    }

    public void addValue(String deviceId, double value) {
        Deque<Double> window = windows.get(deviceId);

        if (window.size() == 128) { // TODO: remove this hardcoded value
            window.removeFirst();
        }

        window.addLast(value);
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
            throw new UnsupportedOperationException("Unsupported operating system");
        }

        Map<String, String> result = new ConcurrentHashMap<>();

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
                    String predictionName = fileName.replace("predicting.csv", "predicted.csv");
                    Path outputFile = inputFile.resolveSibling(predictionName);
                    String outputPath = outputFile.toString();

                    ForecasterManager.getInstance().predict(inputPath, outputPath);

                    result.put(deviceId, outputPath);
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

        return result;
    }


    private double getCpuLoadAvgLastMin() {
        if (cpuTempSamples.isEmpty()) {
            return Double.MAX_VALUE;
        }

        long elapsed = Timed.getFireCount() - cpuTempSamples.peekFirst().timestamp;
        if (elapsed < this.app.configuration.get("cpuTimeWindow").longValue()) {
            return Double.MAX_VALUE;
        }

        double sum = 0.0;
        for (CpuTempSample s : cpuTempSamples) {
            sum += s.cpuLoad;
        }
        
        return (cpuTempSamples.isEmpty()) ? 0.0 : (sum / cpuTempSamples.size());
    }
    
    private double avgCpu() {
        int classifierCount = 0;
        double avgLoad = 0.0;
        for (Object o : this.components) {
            if (o.getClass().equals(NoiseSensor.class)) {
                NoiseSensor ns = (NoiseSensor) o;
                if (ns.noOfprocessedFiles > 0) {
                    double load = 1.0 + 99.0
                            * (ns.noOfprocessedFiles * this.app.configuration.get("lengthOfProcessing").doubleValue() / 10_000);
                    load = Math.min(load, 100.0);
                    avgLoad += load;
                    classifierCount++;
                }
            }
        }
        return classifierCount == 0 ? 0 : avgLoad / classifierCount;
    }
    
    private NoiseSensor findSensorByCpuTemp(boolean minSearch) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        NoiseSensor best = null;
        
        for (Object o : this.components) {
            if (o.getClass().equals(NoiseSensor.class)) {
                NoiseSensor ns = (NoiseSensor) o;
                
                if (minSearch) {
                    if (ns.cpuTemp < min && !ns.isClassificationRunning 
                            && ns.cpuTemp < this.app.configuration.get("cpuTempTreshold").doubleValue()) {
                        best = ns;
                        min = ns.cpuTemp;
                    }     
                } else {
                    if (ns.cpuTemp > max && ns.isClassificationRunning) {
                        best = ns;
                        max = ns.cpuTemp;
                    }
                }
            }
        }
        return best;
    }
    
    private void scale(double avgCpuLoad) {      
        //System.out.println(getCpuLoadAvgLastMin());
        if (noiseSensorsWithClassifier.size() < this.app.configuration.get("minContainerCount").intValue()) {
            NoiseSensor ns = findSensorByCpuTemp(true);
            if (ns != null) {
                SimLogger.logRun(this.app.getComponentName(ns.util.resource.name) + "'classifier was started at: " 
                        + Timed.getFireCount() / 1000.0 / 60.0 + " min. due to minimum requirement");
                noiseSensorsWithClassifier.add(ns);
                ns.isClassificationRunning = true;
            } 
        } else if (avgCpuLoad > this.app.configuration.get("cpuLoadScaleUp").doubleValue()) {
            NoiseSensor ns = findSensorByCpuTemp(true);
            if (ns != null) {
                SimLogger.logRun(this.app.getComponentName(ns.util.resource.name) + "'classifier was started at: " 
                    + Timed.getFireCount() / 1000 / 60 + " min. due to large load");
                noiseSensorsWithClassifier.add(ns);
                ns.isClassificationRunning = true;
            }
        } else if (getCpuLoadAvgLastMin() < this.app.configuration.get("cpuLoadScaleDown").doubleValue()) {
            NoiseSensor ns = findSensorByCpuTemp(false);
            if (ns != null && noiseSensorsWithClassifier.size() > this.app.configuration.get("minContainerCount").intValue()) {
                SimLogger.logRun(this.app.getComponentName(ns.util.resource.name) + "'classifier was turned off at: "
                    + Timed.getFireCount() / 1000.0 / 60.0  + " min. due to small load");
                noiseSensorsWithClassifier.remove(ns);
                ns.isClassificationRunning = false;    
            }
        }
    }
    
    public NoiseSensor getNextNoiseSensorToOffload() {
        if (noiseSensorsWithClassifier.isEmpty()) {
            return null;
        }
        if (currentIndex >= noiseSensorsWithClassifier.size()) {
            currentIndex = 0;
        }
        
        NoiseSensor item = noiseSensorsWithClassifier.get(currentIndex);
        currentIndex = (currentIndex + 1) % noiseSensorsWithClassifier.size();
        return item;
    }
    
    public void startNecesseryServices(int num) {
        int count = 0;
        for (Object component : components) {
            if (component instanceof NoiseSensor) {
                NoiseSensor ns = (NoiseSensor) component;
                noiseSensorsWithClassifier.add(ns);
                ns.isClassificationRunning = true;
                
                SimLogger.logRun(this.app.getComponentName(ns.util.resource.name) + "'classifier was started first at: "
                    + Timed.getFireCount() / 1000.0 / 60.0 + " min.");
                count++;
                if (count >= num) {
                    break; 
                }
            }
        }
    }

    String printWindow() {
        StringBuilder sb = new StringBuilder();
        sb.append("SlidingWindowManager {\n");

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

    private static class CpuTempSample {
        long timestamp;
        double cpuLoad;
        
        CpuTempSample(long ts, double load) {
            this.timestamp = ts;
            this.cpuLoad = load;
        }
    }

    public Map<String, String> writeWindowToCsv(String outputDir) throws IOException {
        LocalDateTime startTime = LocalDateTime.of(2023, 10, 1, 0, 0);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:00");

        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, Deque<Double>> entry : windows.entrySet()) {
            String deviceId = entry.getKey();
            Deque<Double> window = entry.getValue();

            if (window.size() != 128) {
                System.out.println("Skipping " + deviceId + " (not full)");
                continue;
            }

            String filePath = outputDir + "/" + deviceId + "-predicting.csv";

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
            }
            result.put(deviceId, filePath);

        }
        return result;
    }
}