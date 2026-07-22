package hu.u_szeged.inf.fog.simulator.agent.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.RemoteServer;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.Sun;
import hu.u_szeged.inf.fog.simulator.agent.dt.*;
import hu.u_szeged.inf.fog.simulator.agent.management.noise.GreedyNoiseSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.util.NoiseAppCsvExporter;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.CsvVisualiser;
import hu.u_szeged.inf.fog.simulator.common.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static hu.u_szeged.inf.fog.simulator.agent.demo.NoiseClassDemo.calculateTimeBelowThrottling;
import static hu.u_szeged.inf.fog.simulator.agent.demo.NoiseClassDemo.exportCdfToCsv;

public class DigitalTwinDemo {

    public static void main(String[] args) throws Exception {

        SimLogger.setLogging(1, true);

        ObjectMapper mapper = new ObjectMapper();
        Path csvPath;
        DigitalTwinRequest request = null;
        if (args.length != 2) {
            System.err.println("Usage: DigitalTwinDemo <input-json> <noise-csv>");

            request = mapper.readValue(
                    //new File("D:\\Documents\\git-projects\\digital-twin\\examples\\candidate-1_input.json"),
                    new File("/Users/markusa/Documents/git-repos/digital-twin/examples/dt-request-1_input.json"),
                    DigitalTwinRequest.class
            );
            csvPath = Path.of("/Users/markusa/Documents/git-repos/digital-twin/examples/noise-data.csv");
        } else {
            request = mapper.readValue(Path.of(args[0]).toFile(), DigitalTwinRequest.class);
            csvPath = Path.of(args[1]);
        }

        NoiseCsvData noiseData = NoiseCsvData.load(csvPath);
        InputValidator.validate(request, noiseData);

        Map<String, Object> noiseSensorGroup = new LinkedHashMap<>();
        GreedyNoiseSwarmAgent swarmAgent = null;
        switch (request.metadata.applicationType) {
            case "InnoRenew":
                long startOffsetMs =
                        noiseData.simulationStartTime.toLocalTime().toNanoOfDay() / 1_000_000L;
                Sun.init(6, 20, 13, 1.5, startOffsetMs);
                SimulationBuilder.build(request, noiseData);
                for (ComputingAppliance ca : ComputingAppliance.allComputingAppliances.values()) {
                    new EnergyDataCollector(ca.name + "-energy", ca.iaas, true, true);
                }

                swarmAgent = (GreedyNoiseSwarmAgent) SwarmAgent.allSwarmAgents.iterator().next();

                for (Object o : swarmAgent.observedAppComponents) {
                    if (o instanceof NoiseSensor ns) {
                        noiseSensorGroup.put(
                                ns.util.component.id,
                                "inside: " + ns.inside +
                                        ", sun-exposed: " + ns.sunExposed +
                                        ", has-classifier: " + swarmAgent.noiseSensorsWithClassifier.contains(ns)
                        );
                    }
                }
                
                break;
            default:
                System.err.println("Unknown digital twin type: " + request.metadata.applicationType);
                System.exit(1);
                return;
        }

        long starttime = System.nanoTime();
        Timed.simulateUntil(noiseData.maxSimulationTimeMs);
        long stoptime = System.nanoTime();

        Path energyValues = EnergyDataCollector.writeToFile(ScenarioBase.RESULT_DIRECTORY);
        for (NoiseAppCsvExporter noiseAppCsvExporter : NoiseAppCsvExporter.allNoiseAppCsvExporters.values()){

            CsvVisualiser.visualise(
                    noiseAppCsvExporter.swarmAgent.app.name,
                    noiseAppCsvExporter.soundValuesPath,
                    noiseAppCsvExporter.noiseSensorTemperaturePath,
                    noiseAppCsvExporter.noiseSensorCpuLoadPath,
                    noiseAppCsvExporter.noiseSensorClassifierCountPath,
                    noiseAppCsvExporter.processedFilePath,
                    noiseAppCsvExporter.fileMigrationCountPath,
                    noiseAppCsvExporter.sunIntensityPath,
                    energyValues,
                    exportCdfToCsv(ScenarioBase.RESULT_DIRECTORY, noiseAppCsvExporter.swarmAgent)
            ).write();
        }

        SimLogger.logEmptyLine();

        long soundFilesOnNoiseSensors = 0;
        long soundFilesOnRemoteServers = 0;
        long totalGeneratedFiles = 0;

        SimLogger.logRes(swarmAgent.app.name, noiseSensorGroup);

            StorageObject resFile = null;
            RemoteServer remoteServer = null;
            for (Object o : swarmAgent.observedAppComponents) {
                if (o instanceof NoiseSensor ns) {
                    for (StorageObject so : ns.util.vm.getResourceAllocation().getHost().localDisk.contents()) {
                        if (so.id.contains("noise-sensor")) {
                            soundFilesOnNoiseSensors++;
                        }
                    }
                } else if (o instanceof RemoteServer rs) {
                    remoteServer = rs;
                    for (StorageObject so : rs.util.vm.getResourceAllocation().getHost().localDisk.contents()) {
                        if (so.id.equals(swarmAgent.app.name)) {
                            resFile = so;
                        }
                    }
                }
            }
            soundFilesOnRemoteServers += resFile.size / (long) Config.NOISE_CLASS_CONFIGURATION.get("resFileSize");
            totalGeneratedFiles += swarmAgent.totalGeneratedFiles;

        Collections.sort(remoteServer.latencies);
        int n = remoteServer.latencies.size();
        double max = remoteServer.latencies.get(n - 1) / 1000.0;
        double p95 = remoteServer.latencies.get((int) Math.ceil(n * 0.95) - 1) / 1000.0;
        double p99 = remoteServer.latencies.get((int) Math.ceil(n * 0.99) - 1) / 1000.0;
        double median = remoteServer.latencies.get(n / 2) / 1000.0;
        long count = remoteServer.latencies.stream()
                .filter(l -> l <= 10_000)
                .count();

        double percentage = (count * 100.0) / remoteServer.latencies.size();

        long finalSoundFilesOnRemoteServers = soundFilesOnRemoteServers;
        SimLogger.logRes("latency-metrics", latency -> {
            latency.put("max-E2E_sec", max);
            latency.put("p95-E2E_sec", p95);
            latency.put("p99-E2E_sec", p99);
            latency.put("median-E2E_sec", median);
            latency.put("E2E-less-than-10s_percent", percentage);
            latency.put("average-E2E-latency_sec", RemoteServer.totalEndToEndLatency / finalSoundFilesOnRemoteServers / 1000.0);
        });
        GreedyNoiseSwarmAgent finalSwarmAgent = swarmAgent;
        SimLogger.logRes("scalin-decision-count", group ->
                        group.putAll(finalSwarmAgent.decisionType)
        );

        double totalEnergy = 0;
        for (EnergyDataCollector ec : EnergyDataCollector.allEnergyCollectors.values()) {
            totalEnergy += ec.accumulatedEnergy / ScenarioBase.TO_KWH;
        }

        SimLogger.logEmptyLine();
        SimLogger.logRes("simulated-time_min", TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
        SimLogger.logRes("simulator-runtime_sec", TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));

        SimLogger.logRes("total-energy_kwh", totalEnergy);

        SimLogger.logRes("size-of-generated-files_mb", totalGeneratedFiles * (long) Config.NOISE_CLASS_CONFIGURATION.get("soundFileSize") / ScenarioBase.MB_IN_BYTE);
        SimLogger.logRes("number-of-sound-events", totalGeneratedFiles);

        SimLogger.logRes("number-of-offloaded-sound-events", NoiseSensor.totalOffloadedFiles);
        SimLogger.logRes("number-of-sound-events-requiring-processing", NoiseSensor.totalSoundEventsToProcess);
        SimLogger.logRes("number-of-processed-files", NoiseSensor.totalProcessedFiles);
        SimLogger.logRes("number-of-sound-files-on-noise-sensors", soundFilesOnNoiseSensors);
        SimLogger.logRes("number-of-sound-files-on-the-remote-servers", soundFilesOnRemoteServers);


        double avgTimeBelowThrottling = 0.0;
        for (NoiseAppCsvExporter noiseAppCsvExporter : NoiseAppCsvExporter.allNoiseAppCsvExporters.values()){
            avgTimeBelowThrottling += calculateTimeBelowThrottling(
                    noiseAppCsvExporter.noiseSensorTemperaturePath, (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold"));
        }
        SimLogger.logRes("time-below-the-temperature-threshold_percent", avgTimeBelowThrottling / NoiseAppCsvExporter.allNoiseAppCsvExporters.size());

        SimLogger.logEmptyLine();

        DigitalTwinRequest finalRequest = request;
        SimLogger.logRes("config-parameters", param -> {
            param.put("sound-level-threshold_db", Config.NOISE_CLASS_CONFIGURATION.get("soundThreshold"));
            param.put("min-CPU-temperature_celsius", Config.NOISE_CLASS_CONFIGURATION.get("minCpuTemp"));
            param.put("max-CPU-temperature_celsius", Config.NOISE_CLASS_CONFIGURATION.get("maxCpuTemp"));
            param.put("CPU-temperature-threshold_celsius", Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold"));
            param.put("min-container-count", Config.NOISE_CLASS_CONFIGURATION.get("minContainerCount"));
            param.put("scaling cooldown_ms", Config.NOISE_CLASS_CONFIGURATION.get("cpuTimeWindow"));
            param.put("CPU-load-scale-up_percent", Config.NOISE_CLASS_CONFIGURATION.get("cpuLoadScaleUp"));
            param.put("CPU-load-scale-down_percent", Config.NOISE_CLASS_CONFIGURATION.get("cpuLoadScaleDown"));
            param.put("max-simulable-time-according-to-data_min", noiseData.maxSimulationTimeMs / ScenarioBase.MINUTE_IN_MILLISECONDS);
            param.put("requested-prediction-horizon_min", finalRequest.metadata.predictionHorizonMin);
        });

        System.out.println(SimLogger.getResultsAsJson());

        /*
        System.out.println("Simulation start: "
                + noiseData.simulationStartTime);

        for (Map.Entry<String, Deque<SensorEvent>> entry
                : noiseData.eventsBySensor.entrySet()) {

            System.out.println("\n=== " + entry.getKey() + " ===");

            for (SensorEvent event : entry.getValue()) {
                System.out.println(
                        "t=" + event.simulationTimeMs()
                                + " ms -> "
                                + event.value()
                );
            }
        }
        */
    }
}