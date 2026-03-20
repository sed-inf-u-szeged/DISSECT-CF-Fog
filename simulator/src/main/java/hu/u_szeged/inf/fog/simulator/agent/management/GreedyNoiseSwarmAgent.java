package hu.u_szeged.inf.fog.simulator.agent.management;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
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

    static class CpuTemperatureSample {
        long timestamp;
        double cpuLoad;

        CpuTemperatureSample(long ts, double load) {
            this.timestamp = ts;
            this.cpuLoad = load;
        }
    }

    public List<NoiseSensor> noiseSensorsWithClassifier = new ArrayList<>();

    public static int shutdownCounter;

    public long filesSentToDatabase;

    private int currentClassifierIndex;

    public HashMap<String, Integer> decisionType;

    /**
     * window with CPU loads for downscaling
     */
    final Deque<CpuTemperatureSample> cpuTemperatureSamples = new ArrayDeque<>();

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

            //System.out.println(SwarmAgent.totalGeneratedFiles == GreedyNoiseSwarmAgent.filesSentToDatabase);
            //System.out.println(shutdownCounter + " " + ((List<Integer>) Config.NOISE_CLASS_ONFIGURATION.get("submissionDelay")).size());
        }
    }

    public void startNecesseryServices(int num) {
        int count = 0;
        for (Object component : this.observedAppComponents) {
            if (component instanceof NoiseSensor ns) {
                noiseSensorsWithClassifier.add(ns);

                SimLogger.logRun(ns.util.component.id + "'s classifier was started to meet the required service count at: "
                                + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
                count++;
                if (count >= num) {
                    break;
                }
            }
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


    Pair<Map<NoiseSensor, Double>, Double> updateCpuMetricsForLastMinute(Map<String, Deque<Double>> windows) {
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

                if (windows != null) {
                    Deque<Double> window = windows.get(ns.util.component.id);

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
        if (cpuTemperatureSamples.isEmpty()) {
            return Double.MAX_VALUE;
        }

        long elapsed = Timed.getFireCount() - cpuTemperatureSamples.peekFirst().timestamp;
        if (elapsed < (long) Config.NOISE_CLASS_CONFIGURATION.get("cpuTimeWindow")) {
            return Double.MAX_VALUE;
        }

        double sum = 0.0;
        for (CpuTemperatureSample s : cpuTemperatureSamples) {
            sum += s.cpuLoad;
        }

        return (sum / cpuTemperatureSamples.size());
    }

    void scale(double avgCpuLoad) {
        boolean scaledToMinimum = false;
        while (noiseSensorsWithClassifier.size() < (int) Config.NOISE_CLASS_CONFIGURATION.get("minContainerCount")) {
            NoiseSensor ns = findSensorByCpuTemperature(true);
            if (ns == null) {
                break;
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
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min. due to large load");
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

    @Override
    public void tick(long fires) {
        Pair<Map<NoiseSensor, Double>, Double> noiseSensorCpuLoads = updateCpuMetricsForLastMinute(null);

        if (!cpuTemperatureSamples.isEmpty()) {
            cpuTemperatureSamples.removeFirst();
        }
        cpuTemperatureSamples.addLast(new CpuTemperatureSample(fires, noiseSensorCpuLoads.getRight()));

        this.scale(noiseSensorCpuLoads.getRight());

        if (this.noiseAppCsvExporter != null){
            this.noiseAppCsvExporter.log(this, noiseSensorCpuLoads);
        }

        shutdown(fires);
    }
}
