package hu.u_szeged.inf.fog.simulator.agent.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.RemoteServer;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.Sun;
import hu.u_szeged.inf.fog.simulator.agent.dt.*;
import hu.u_szeged.inf.fog.simulator.agent.management.ForecastBasedSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.management.GreedyNoiseSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.util.NoiseAppCsvExporter;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.CsvVisualiser;
import hu.u_szeged.inf.fog.simulator.common.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Deque;
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
                    new File("/Users/markusa/Documents/git-repos/digital-twin/examples/candidate-1_input.json"),
                    DigitalTwinRequest.class
            );
            csvPath = Path.of("/Users/markusa/Documents/git-repos/digital-twin/examples/noise-data.csv");
        } else {
            request = mapper.readValue(Path.of(args[0]).toFile(), DigitalTwinRequest.class);
            csvPath = Path.of(args[1]);
        }

        NoiseCsvData noiseData = NoiseCsvData.load(csvPath);

        InputValidator.validate(request, noiseData);

        switch (request.metadata.applicationType) {
            case "InnoRenew":
                long startOffsetMs =
                        noiseData.simulationStartTime.toLocalTime().toNanoOfDay() / 1_000_000L;
                Sun.init(6, 20, 13, 1.5, startOffsetMs);
                SimulationBuilder.build(request, noiseData);
                for (ComputingAppliance ca : ComputingAppliance.allComputingAppliances.values()) {
                    new EnergyDataCollector(ca.name + "-energy", ca.iaas, true, true);
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

        for (SwarmAgent sa : SwarmAgent.allSwarmAgents) {
            SimLogger.logRes(sa.app.name + ": ");

            StorageObject resFile = null;
            for (Object o : sa.observedAppComponents) {
                if (o instanceof NoiseSensor ns) {
                    SimLogger.logRes("\t" + ns.util.component.id + " is inside: " + ns.inside + ", exposed to sunlight: " + ns.sunExposed);
                    for (StorageObject so : ns.util.vm.getResourceAllocation().getHost().localDisk.contents()) {
                        if (so.id.contains("noise-sensor")) {
                            soundFilesOnNoiseSensors++;
                        }
                    }
                } else if (o instanceof RemoteServer rs) {
                    for (StorageObject so : rs.util.vm.getResourceAllocation().getHost().localDisk.contents()) {
                        if (so.id.equals(sa.app.name)) {
                            resFile = so;
                        }
                    }
                    Collections.sort(rs.latencies);
                    int n = rs.latencies.size();
                    double max = rs.latencies.get(n - 1) / 1000.0;
                    double p95 = rs.latencies.get((int) Math.ceil(n * 0.95) - 1) / 1000.0;
                    double p99 = rs.latencies.get((int) Math.ceil(n * 0.99) - 1) / 1000.0;
                    double median = rs.latencies.get(n / 2) / 1000.0;
                    long count = rs.latencies.stream()
                            .filter(l -> l <= 10_000)
                            .count();

                    double percentage = (count * 100.0) / rs.latencies.size();

                    SimLogger.logRes("End-to-end latency (s):");
                    SimLogger.logRes("\tMax: " + max);
                    SimLogger.logRes("\tp95: " + p95);
                    SimLogger.logRes("\tp99: " + p99);
                    SimLogger.logRes("\tMedian: " + median);
                    SimLogger.logRes("\t<=10s: " + percentage + " (%)");
                }
            }
            soundFilesOnRemoteServers += resFile.size / (long) Config.NOISE_CLASS_CONFIGURATION.get("resFileSize");
            totalGeneratedFiles += sa.totalGeneratedFiles;

            if (sa instanceof GreedyNoiseSwarmAgent gnsa){
                SimLogger.logRes("Scaling decision type - count:");
                gnsa.decisionType.forEach((key, value) ->
                        SimLogger.logRes("\t" + key + ": " + value)
                );
            }
            if (sa instanceof ForecastBasedSwarmAgent fbsa){
                SimLogger.logRes("\tNumber of forecasts: " + fbsa.forecastingTimes.size());
            }
        }

        double totalEnergy = 0;
        for (EnergyDataCollector ec : EnergyDataCollector.allEnergyCollectors.values()) {
            totalEnergy += ec.accumulatedEnergy / ScenarioBase.TO_KWH;
        }

        SimLogger.logEmptyLine();
        SimLogger.logRes("Simulated time (minutes): " + TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
        SimLogger.logRes("Simulator runtime (seconds): " + TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));

        SimLogger.logRes("Total energy (kWh): " + totalEnergy);

        SimLogger.logRes("Size of generated files (MB): " + totalGeneratedFiles * (long) Config.NOISE_CLASS_CONFIGURATION.get("soundFileSize") / ScenarioBase.MB_IN_BYTE);
        SimLogger.logRes("Number of sound events (pc.): " + totalGeneratedFiles);

        SimLogger.logRes("Number of offloaded sound events (pc.): " + NoiseSensor.totalOffloadedFiles);
        SimLogger.logRes("Number of sound events requiring processing (pc.): " + NoiseSensor.totalSoundEventsToProcess);
        SimLogger.logRes("Number of processed files (pc.): " + NoiseSensor.totalProcessedFiles);
        SimLogger.logRes("Number of sound files on noise sensors: " + soundFilesOnNoiseSensors);
        SimLogger.logRes("Number of sound files on the remote servers: " + soundFilesOnRemoteServers);

        SimLogger.logRes("Average end-to-end latency (sec.): " + RemoteServer.totalEndToEndLatency / soundFilesOnRemoteServers / 1000.0);
        double avgTimeBelowThrottling = 0.0;
        for (NoiseAppCsvExporter noiseAppCsvExporter : NoiseAppCsvExporter.allNoiseAppCsvExporters.values()){
            avgTimeBelowThrottling += calculateTimeBelowThrottling(
                    noiseAppCsvExporter.noiseSensorTemperaturePath, (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold"));
        }
        SimLogger.logRes("Time below the temperature threshold (%):" + avgTimeBelowThrottling / NoiseAppCsvExporter.allNoiseAppCsvExporters.size());

        SimLogger.logEmptyLine();

        SimLogger.logRes("Config parameters:");
        SimLogger.logRes("\tSound level threshold: " + Config.NOISE_CLASS_CONFIGURATION.get("soundThreshold") + " (dB)");
        SimLogger.logRes("\tMin. CPU temperature: " + Config.NOISE_CLASS_CONFIGURATION.get("minCpuTemp") + " (℃)");
        SimLogger.logRes("\tMax. CPU temperature: " + Config.NOISE_CLASS_CONFIGURATION.get("maxCpuTemp") + " (℃)");
        SimLogger.logRes("\tCPU temperature threshold: " + Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold") + " (℃)");
        SimLogger.logRes("\tMin. container count: " + Config.NOISE_CLASS_CONFIGURATION.get("minContainerCount") + " (pc.)");
        SimLogger.logRes("\tScaling cooldown: " + Config.NOISE_CLASS_CONFIGURATION.get("cpuTimeWindow") + " (ms.)");
        SimLogger.logRes("\tCPU load scale up: " + Config.NOISE_CLASS_CONFIGURATION.get("cpuLoadScaleUp") + " (%)");
        SimLogger.logRes("\tCPU load scale down: " + Config.NOISE_CLASS_CONFIGURATION.get("cpuLoadScaleDown") + " (%)");
        SimLogger.logRes("\tMax. simulation time according to data: " + noiseData.maxSimulationTimeMs / ScenarioBase.MINUTE_IN_MILLISECONDS + " (min.)");
        SimLogger.logRes("\tRequested prediction horizon: " + request.metadata.predictionHorizonMin  + " (min.)");
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