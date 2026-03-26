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

public class ScalableGreedySwarmAgent extends GreedyNoiseSwarmAgent {

    protected final Map<String, List<Double>> lastPredictions = new HashMap<>();

    public final ArrayList<Long> forecastingTimes = new ArrayList<>();

    protected long lastScalingActionMinute = -1;

    public ScalableGreedySwarmAgent(AgentApplication app) {
        super(app);
    }

    @Override
    public void tick(long fires) {
        Pair<Map<NoiseSensor, Double>, Double> noiseSensorCpuLoads = updateCpuMetricsForLastMinute(null);
        cpuTemperatureSamples.addLast(noiseSensorCpuLoads.getRight());

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

        boolean scaledToMinimum = false;
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

        if (lastScalingActionMinute >= 0 && nowMinute - lastScalingActionMinute < cooldown) {
            return;
        }

        // load-based scaling
        if (avgCpuLoad > cpuLoadScaleUp) {
            int startCount = getExtraScaleUpCountFromQueue();

            for (int i = 0; i < startCount; i++) {
                NoiseSensor ns = findSensorByCpuTemperature(true);
                if (ns == null) {
                    return;
                }

                noiseSensorsWithClassifier.add(ns);
                lastScalingActionMinute = nowMinute;
                decisionType.compute("scale-up-load", (k, v) -> v + 1);
                SimLogger.logRun(ns.util.component.id + "'classifier was started at: "
                        + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS
                        + " min. due to large load (" + (i + 1) + ")");
            }
        } else if (noiseSensorsWithClassifier.size() > minContainerCount
                && getAverageClassifierCpuLoadOverWindow() < cpuLoadScaleDown) {

            NoiseSensor ns =findSensorByCpuTemperature(false);
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
}
