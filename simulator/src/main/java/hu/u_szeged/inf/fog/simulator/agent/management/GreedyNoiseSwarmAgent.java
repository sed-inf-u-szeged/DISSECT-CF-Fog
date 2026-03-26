package hu.u_szeged.inf.fog.simulator.agent.management;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.util.NoiseAppCsvExporter;
import hu.u_szeged.inf.fog.simulator.common.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class GreedyNoiseSwarmAgent extends SwarmAgent {

    public List<NoiseSensor> noiseSensorsWithClassifier = new ArrayList<>();

    public static int shutdownCounter;

    public long filesSentToDatabase;

    private int currentClassifierIndex;

    public HashMap<String, Integer> decisionType;

    /**
     * window with CPU loads for downscaling
     */
    final Deque<Double> cpuTemperatureSamples = new ArrayDeque<>();

    NoiseAppCsvExporter noiseAppCsvExporter;

    public GreedyNoiseSwarmAgent(AgentApplication app) {
        super(app);

        decisionType = new HashMap<>(){
            {
                put("scale-up-min", 0);
                put("scale-up-load", 0);
                put("scale-down-load", 0);
                put("scale-down-temperature", 0);
            }
        };

        subscribe((long) Config.NOISE_CLASS_CONFIGURATION.get("samplingFreq") * 6); // every 1 min.
        if ((boolean) Config.NOISE_CLASS_CONFIGURATION.get("csvLogging")) {
            this.noiseAppCsvExporter = new NoiseAppCsvExporter(this);
        }
    }

    public void shutdown(long fires) {
        if ((app.submissionTime + (long) Config.NOISE_CLASS_CONFIGURATION.get("simLength")) < fires) {
            if(totalGeneratedFiles == this.filesSentToDatabase){
                for(Object component : this.observedAppComponents){
                    if(component instanceof NoiseSensor ns){
                        ns.stop();
                    }
                }
                this.unsubscribe();
                app.terminationTime = fires;
                SimLogger.logRun(app.name + " application finished at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
                shutdownCounter++;
                // TODO: release capacities
            }

            if (shutdownCounter + ResourceAgent.failedDeployments == ((List<Integer>) Config.NOISE_CLASS_CONFIGURATION.get("submissionDelay")).size()) {
                for (EnergyDataCollector edc : EnergyDataCollector.allEnergyCollectors.values()) {
                    edc.stop();
                }
            }
        }
    }

    public void startNecesseryServices(int num) {
        List<NoiseSensor> sensors = new ArrayList<>();
        for (Object component : this.observedAppComponents) {
            if (component instanceof NoiseSensor ns) {
                sensors.add(ns);
            }
        }

        Collections.shuffle(sensors, SeedSyncer.centralRnd);
        int limit = Math.min(num, sensors.size());

        for (int i = 0; i < limit; i++) {
            NoiseSensor ns = sensors.get(i);
            noiseSensorsWithClassifier.add(ns);
            SimLogger.logRun(
                    ns.util.component.id + "'s classifier was started to meet the required service count at: "
                            + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min."
            );
        }
    }

    public NoiseSensor getNextClassifierForOffloading() {
        if (noiseSensorsWithClassifier.isEmpty()) {
            return null;
        }
        if (currentClassifierIndex >= noiseSensorsWithClassifier.size()) {
            currentClassifierIndex = 0;
        }

        NoiseSensor item = noiseSensorsWithClassifier.get(currentClassifierIndex);
        currentClassifierIndex = (currentClassifierIndex + 1) % noiseSensorsWithClassifier.size();
        return item;
    }

    /**
     * Calculates CPU load metrics for all noise sensors for the last minute.
     * Optionally maintains a sliding window of past CPU temperatures for each sensor.
     *
     * @param windowsForPrediction a map of sliding windows used to store CPU temperatures
     * @return a pair where the first value is a map of sensors and their current CPU load,
     *         and the second value is the average CPU load of sensors that processed data in the last minute
     */
    Pair<Map<NoiseSensor, Double>, Double> updateCpuMetricsForLastMinute(Map<String, Deque<Double>> windowsForPrediction) {
        Map<NoiseSensor, Double> loads = new LinkedHashMap<>();
        double sum = 0.0;
        int count = 0;

        for (Object o : this.observedAppComponents) {
            if (o instanceof NoiseSensor ns) {

                double load = 0.0;
                if (ns.processedFilesLastMinute > 0) {
                    double lengthOfProcessing =
                            (double) Config.NOISE_CLASS_CONFIGURATION.get("lengthOfProcessing");

                    load = Math.min(
                            100.0 * (ns.processedFilesLastMinute * lengthOfProcessing) / this.getFrequency(),
                            100.0);

                    sum += load;
                    count++;
                    ns.processedFilesLastMinute = 0;
                }
                loads.put(ns, load);

                // memory for CPU temperature prediction
                if (windowsForPrediction != null) {

                    Deque<Double> window = windowsForPrediction.get(ns.util.component.id);

                    if (window.size() == 128) { // TODO: remove this hardcoded value
                        window.removeFirst();
                    }

                    window.addLast(ns.cpuTemperature);
                }
            }
        }

        double avg = count == 0 ? 0.0 : sum / count;
        return Pair.of(loads, avg);
    }

    NoiseSensor findSensorByCpuTemperature(boolean minSearch) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        NoiseSensor best = null;

        for (Object o : this.observedAppComponents) {
            if (o instanceof NoiseSensor ns) {
                if (minSearch) {
                    if (ns.cpuTemperature < min && !this.noiseSensorsWithClassifier.contains(ns)
                            && ns.cpuTemperature < (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold")) {
                        best = ns;
                        min = ns.cpuTemperature;
                    }
                } else {
                    if (ns.cpuTemperature > max && this.noiseSensorsWithClassifier.contains(ns)) {
                        best = ns;
                        max = ns.cpuTemperature;
                    }
                }
            }
        }
        return best;
    }

    double getAverageClassifierCpuLoadOverWindow() {
        long entries = (long) Config.NOISE_CLASS_CONFIGURATION.get("cpuTimeWindow")
                / ScenarioBase.MINUTE_IN_MILLISECONDS;

        while (cpuTemperatureSamples.size() > entries + 1) {
            cpuTemperatureSamples.pollFirst();
        }

        double sum = 0.0;
        for (double value : cpuTemperatureSamples) {
            sum += value;
        }

        return cpuTemperatureSamples.isEmpty() ? 100.0 : (sum / cpuTemperatureSamples.size());
    }

    void scale(double avgCpuLoad) {
        boolean scaledToMinimum = false;
        int minContainerCount = (int) Config.NOISE_CLASS_CONFIGURATION.get("minContainerCount");
        while (noiseSensorsWithClassifier.size() < minContainerCount) {
            NoiseSensor ns = findSensorByCpuTemperature(true);
            if (ns == null) {
                return;
            }

            noiseSensorsWithClassifier.add(ns);
            decisionType.compute("scale-up-min", (k, v) -> v + 1);
            scaledToMinimum = true;

            SimLogger.logRun(ns.util.component.id + "'classifier was started at: "
                    + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                    + " min. due to minimum requirement");
        }

        if (scaledToMinimum) {
            return;
        }

        if (avgCpuLoad > (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuLoadScaleUp")) {
            NoiseSensor ns = findSensorByCpuTemperature(true);
            if (ns != null) {
                noiseSensorsWithClassifier.add(ns);
                decisionType.compute("scale-up-load", (k, v) -> v + 1);
                SimLogger.logRun(ns.util.component.id + "'classifier was started at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min. due to large load ");
            }
        } else if (getAverageClassifierCpuLoadOverWindow() < (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuLoadScaleDown")) {
            NoiseSensor ns = findSensorByCpuTemperature(false);
            if (ns != null && noiseSensorsWithClassifier.size() > (int) Config.NOISE_CLASS_CONFIGURATION.get("minContainerCount")) {
                noiseSensorsWithClassifier.remove(ns);
                decisionType.compute("scale-down-load", (k, v) -> v + 1);
                SimLogger.logRun(ns.util.component.id + "'classifier was turned off at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS  + " min. due to small load");
            }
        }
    }

    protected int getExtraScaleUpCountFromQueue() {
        int queueLength = 0;
        int availableSensors = 0;

        for (Object o : this.observedAppComponents) {
            if (o instanceof NoiseSensor ns) {
                queueLength += ns.filesToProcess.size();

                if (ns.cpuTemperature < (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold")
                        && !this.noiseSensorsWithClassifier.contains(ns)) {
                    availableSensors++;
                }
            }
        }

        int requiredClassifiers = (int) Math.ceil((double) queueLength / (int) Config.NOISE_CLASS_CONFIGURATION.get("noiseSensorCount") * 2);
        int extraNeeded = requiredClassifiers - this.noiseSensorsWithClassifier.size();

        if (extraNeeded < 1) {
            extraNeeded = 1;
        }

        return Math.min(extraNeeded, availableSensors);
    }

    @Override
    public void tick(long fires) {
        Pair<Map<NoiseSensor, Double>, Double> noiseSensorCpuLoads = updateCpuMetricsForLastMinute(null);
        cpuTemperatureSamples.addLast(noiseSensorCpuLoads.getRight());

        this.scale(noiseSensorCpuLoads.getRight());

        if (this.noiseAppCsvExporter != null){
            this.noiseAppCsvExporter.log(this, noiseSensorCpuLoads);
        }

        shutdown(fires);
    }
}
