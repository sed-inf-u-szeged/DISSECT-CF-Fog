package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.NoiseAppCsvExporter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SystemUtils;

public class SwarmAgent extends Timed {

    public ArrayList<Object> components;
    
    public ArrayList<NoiseSensor> noiseSensorsWithClassifier;
    
    private int currentIndex;
    
    public static ArrayList<SwarmAgent> allSwarmAgents = new ArrayList<>();

    public static String predictorScriptDir; 
    
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
        if (triggerPrediction % 6 == 0 ) {
            NoiseAppCsvExporter.log();
        }
       
        if (triggerPrediction % (64 * 6) == 0 &&  windows.values().stream().anyMatch(w -> w.size() == 128)) {
            // TODO: call it only if there was enough data
        	//callPredictorScript();
            try {
                writeWindowToCsv(ScenarioBase.resultDirectory);
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.exit(0);
        }
        triggerPrediction++;
    }
    
    public void addValue(String deviceId, double value) {
        Deque<Double> window = windows.get(deviceId);

        if (window.size() == 128) { // TODO: remove this hardcoded value
            window.removeFirst(); 
        }
        
        window.addLast(value); 
    }

    
    private void callPredictorScript() {
        try {
            String command;
            ProcessBuilder processBuilder;

            if (SystemUtils.IS_OS_LINUX) {
            	String modelPath = predictorScriptDir + "/checkpoints/simulator1__UNC-1-Noise-Sensor-3_1min_pl128";
            	String inputPath = predictorScriptDir + "/data/simulator1/UNC-1-Noise-Sensor-3_1min_test.csv";
            	String outputPath = predictorScriptDir + "/predictions/simulator1/UNC-1-Noise-Sensor-3_1min_predictions-" + Timed.getFireCount() + ".csv";

            	command = String.join(" ",
            	    "cd", predictorScriptDir,
            	    "&&",
            	    "uv run", "Time-Series-Library/predict.py",
            	    "--model_path", modelPath,
            	    "--input_path", inputPath,
            	    "--output_path", outputPath
            	);
            } else {
                throw new UnsupportedOperationException("Unsupported operating system");
            }

            processBuilder = new ProcessBuilder("bash", "-c", command);
            //System.out.println(processBuilder.command());
            //System.exit(0);
        	processBuilder.redirectErrorStream(true);

        	Process process = processBuilder.start();

        	try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        	    String line;
        	    while ((line = reader.readLine()) != null) {
        	        System.out.println(line);
        	    }
        	}
        	process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.getStackTrace();
        }
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
    
    public void writeWindowToCsv(String outputDir) throws IOException {
        LocalDateTime startTime = LocalDateTime.of(2023, 10, 1, 0, 0);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:00");

        for (Map.Entry<String, Deque<Double>> entry : windows.entrySet()) {
            String deviceId = entry.getKey();
            Deque<Double> window = entry.getValue();

            if (window.size() != 128) {
                System.out.println("Skipping " + deviceId + " (not full)");
                continue;
            }

            String filePath = outputDir + "/" + deviceId + "-for-prediction.csv";

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

        }
    }
}