package hu.u_szeged.inf.fog.simulator.agent.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.RemoteServer;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.Sun;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingGateway;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.PlatformService;
import hu.u_szeged.inf.fog.simulator.agent.dt.*;
import hu.u_szeged.inf.fog.simulator.agent.management.noise.GreedyNoiseSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.management.parking.ParkingTimeBasedSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.util.NoiseAppCsvExporter;
import hu.u_szeged.inf.fog.simulator.agent.util.ParkingAppCsvExporter;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.CsvVisualiser;
import hu.u_szeged.inf.fog.simulator.common.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
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
        JsonNode root;

        if (args.length != 3) {
            System.err.println("Only for debugging purposes!");
            System.err.println("Usage: DigitalTwinDemo <input-json> <data-csv> <seed>");

            root = mapper.readTree(new File(""));
            csvPath = Path.of("");
            SeedSyncer.setSeed(Integer.parseInt(args[2]));
        } else {
            root = mapper.readTree(Path.of(args[0]).toFile());
            csvPath = Path.of(args[1]);
            SeedSyncer.setSeed(Integer.parseInt(args[2]));
        }

        String applicationType = root.path("metadata").path("application_type").asText();

        switch (applicationType) {
            case "InnoRenew" -> runNoiseDigitalTwin(mapper.treeToValue(root, NoiseDigitalTwinRequest.class),csvPath);
            case "FuelicsParking" -> runParkingDigitalTwin(mapper.treeToValue(root, ParkingDigitalTwinRequest.class), csvPath);
            default -> throw new IllegalArgumentException("Unknown digital twin type: " + applicationType);
        }
    }

    private static void runParkingDigitalTwin(ParkingDigitalTwinRequest request, Path csvPath) throws Exception {
        ParkingCsvData parkingData = ParkingCsvData.load(csvPath);
        ParkingSimulationBuilder.build(request, parkingData);

        PlatformService platformService = PlatformService.allPlatformServices.get(0);

        ParkingTimeBasedSwarmAgent swarmAgent = (ParkingTimeBasedSwarmAgent) SwarmAgent.allSwarmAgents.iterator().next();

        Map<String, Map<String, String>> parkingAssignment = new LinkedHashMap<>();
        for (ParkingDigitalTwinRequest.Component component : request.application.components) {
            if (component.properties != null && "parking-sensor".equals(component.properties.componentType)) {
                ParkingSensor sensor = ParkingSensor.allParkingSensors.stream()
                        .filter(s -> s.id.equals(component.componentId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Parking sensor not found: " + component.componentId));

                parkingAssignment
                        .computeIfAbsent(component.assignedResource, key -> new LinkedHashMap<>())
                        .put(component.componentId, sensor.mode.toString());
            }
        }

        long starttime = System.nanoTime();
        Timed.simulateUntil(request.metadata.predictionHorizonMin * ScenarioBase.MINUTE_IN_MILLISECONDS);
        long stoptime = System.nanoTime();

        for (ParkingAppCsvExporter exporter : ParkingAppCsvExporter.allParkingAppCsvExporters.values()) {
            CsvVisualiser.visualise(
                    exporter.swarmAgent.app.name,
                    exporter.parkingSpotStatusPath,
                    exporter.batteryStatusPath,
                    exporter.zoneStatusPath
            ).write();
        }

        SimLogger.logEmptyLine();
        SimLogger.logRes("simulated-time_min", TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
        SimLogger.logRes("simulator-runtime_sec", TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));
        SimLogger.logRes("total-data-received-by-platform_bytes", platformService.receivedDataSize);
        SimLogger.logRes("events-received-by-platform", platformService.receivedEventCount);
        SimLogger.logRes("number-of-parking-events", ParkingSensor.totalParkingEvents);
        SimLogger.logRes("assignment", group -> {
            for (Map.Entry<String, Map<String, String>> entry : parkingAssignment.entrySet()) {
                group.put(entry.getKey(), entry.getValue());
            }
        });

        Map<String, Object> parkingSensorGroup = new LinkedHashMap<>();
        long totalRemainingBattery = 0;
        long initialTotalBattery = 0;
        Map<String, Integer> initialBatteryBySensor = new LinkedHashMap<>();

        for (ParkingDigitalTwinRequest.Component component : request.application.components) {
            if (component.properties != null && "parking-sensor".equals(component.properties.componentType)) {
                initialBatteryBySensor.put(component.componentId, component.properties.batteryLevel);
                initialTotalBattery += component.properties.batteryLevel;
            }
        }

        for (Object o : swarmAgent.observedAppComponents) {
            if (o instanceof ParkingSensor sensor) {
                parkingSensorGroup.put(
                        sensor.id,
                        "mode: " + sensor.mode
                                + ", battery: " + sensor.batteryLevel
                                + ", event-count: " + sensor.eventCount
                                //+ ", zone: " + sensor.zone
                );
                totalRemainingBattery += sensor.batteryLevel;
            }
        }
        SimLogger.logRes("parking-sensors", parkingSensorGroup);

        long totalBatteryConsumed = initialTotalBattery - totalRemainingBattery;
        SimLogger.logRes("total-battery-consumed", totalBatteryConsumed);
        SimLogger.logRes("total-battery-remaining", totalRemainingBattery);
        SimLogger.logRes("battery-consumed_percent",
                initialTotalBattery == 0 ? 0.0 : totalBatteryConsumed * 100.0 / initialTotalBattery);

        Map<ParkingSensor.ParkingZone, Long> consumedByZone =
                new EnumMap<>(ParkingSensor.ParkingZone.class);
        Map<ParkingSensor.ParkingZone, Integer> sensorCountByZone =
                new EnumMap<>(ParkingSensor.ParkingZone.class);

        for (ParkingSensor.ParkingZone zone : ParkingSensor.ParkingZone.values()) {
            consumedByZone.put(zone, 0L);
            sensorCountByZone.put(zone, 0);
        }

        for (ParkingSensor sensor : ParkingSensor.allParkingSensors) {
            int initialBattery = initialBatteryBySensor.getOrDefault(sensor.id, sensor.batteryLevel);
            long consumed = initialBattery - sensor.batteryLevel;
            consumedByZone.put(sensor.zone, consumedByZone.get(sensor.zone) + consumed);
            sensorCountByZone.put(sensor.zone, sensorCountByZone.get(sensor.zone) + 1);
        }

        /*
        SimLogger.logRes("battery-consumption-by-zone", group -> {
            for (ParkingSensor.ParkingZone zone : ParkingSensor.ParkingZone.values()) {
                long consumed = consumedByZone.get(zone);
                int count = sensorCountByZone.get(zone);
                Map<String, Object> zoneData = new LinkedHashMap<>();
                zoneData.put("total-consumed", consumed);
                zoneData.put("sensors", count);
                zoneData.put("average-per-sensor", count == 0 ? 0.0 : consumed / (double) count);
                group.put(zone.toString(), zoneData);
            }
        });
        */

        if (!platformService.latencies.isEmpty()) {
            Collections.sort(platformService.latencies);
            int n = platformService.latencies.size();
            double max = platformService.latencies.get(n - 1) / 1000.0;
            double p95 = platformService.latencies.get((int) Math.ceil(n * 0.95) - 1) / 1000.0;
            double p99 = platformService.latencies.get((int) Math.ceil(n * 0.99) - 1) / 1000.0;
            double median = platformService.latencies.get(n / 2) / 1000.0;
            double average = platformService.receivedEventCount == 0
                    ? 0.0
                    : PlatformService.totalEndToEndLatency
                    / (double) platformService.receivedEventCount / 1000.0;

            SimLogger.logRes("latency-metrics", latency -> {
                latency.put("max-E2E_sec", max);
                latency.put("p95-E2E_sec", p95);
                latency.put("p99-E2E_sec", p99);
                latency.put("median-E2E_sec", median);
                latency.put("average-E2E-latency_sec", average);
            });
        }

        SimLogger.logRes("config-parameters", param -> {
            param.put("orchestration-enabled", Config.PARKING_CONFIGURATION.get("orchestrationEnabled"));
            param.put("scaling-cooldown_ms", request.metadata.scalingCooldownMs);
            param.put("BLE-polling-interval_ms", request.metadata.blePollingIntervalMs);
            param.put("number-of-sensors", ParkingSensor.allParkingSensors.size());
            param.put("number-of-gateways", ParkingGateway.allParkingGateways.size());
            param.put("max-simulable-time-according-to-data_min",
                    parkingData.maxSimulationTimeMs / ScenarioBase.MINUTE_IN_MILLISECONDS);
            param.put("requested-prediction-horizon_min", request.metadata.predictionHorizonMin);
            param.put("seed", SeedSyncer.getSeed());
        });

        System.out.println(SimLogger.getResultsAsJson());
    }

    private static void runNoiseDigitalTwin(NoiseDigitalTwinRequest request, Path csvPath) throws Exception {
        NoiseCsvData noiseData = NoiseCsvData.load(csvPath);
        long startOffsetMs = noiseData.simulationStartTime.toLocalTime().toNanoOfDay() / 1_000_000L;
        Sun.init(6, 20, 13, 1.5, startOffsetMs);
        NoiseSimulationBuilder.build(request, noiseData);

        for (ComputingAppliance ca : ComputingAppliance.allComputingAppliances.values()) {
            new EnergyDataCollector(ca.name + "-energy", ca.iaas, true, true);
        }

        GreedyNoiseSwarmAgent swarmAgent = (GreedyNoiseSwarmAgent) SwarmAgent.allSwarmAgents.iterator().next();

        Map<String, Object> noiseAssignment = new LinkedHashMap<>();
        for (Object o : swarmAgent.observedAppComponents) {
            if (o instanceof NoiseSensor ns) {
                noiseAssignment.put(
                        ns.util.component.id,
                        swarmAgent.noiseSensorsWithClassifier.contains(ns)
                );
            }
        }

        long starttime = System.nanoTime();
        Timed.simulateUntil(request.metadata.predictionHorizonMin * ScenarioBase.MINUTE_IN_MILLISECONDS);
        long stoptime = System.nanoTime();

        Map<String, Object> noiseSensorGroup = new LinkedHashMap<>();
        for (Object o : swarmAgent.observedAppComponents) {
            if (o instanceof NoiseSensor ns) {
                noiseSensorGroup.put(
                        ns.util.component.id,
                        "inside: " + ns.inside
                                + ", sun-exposed: " + ns.sunExposed
                                + ", has-classifier: "
                                + swarmAgent.noiseSensorsWithClassifier.contains(ns)
                );
            }
        }

        Path energyValues = EnergyDataCollector.writeToFile(ScenarioBase.RESULT_DIRECTORY);

        for (NoiseAppCsvExporter exporter : NoiseAppCsvExporter.allNoiseAppCsvExporters.values()) {
            CsvVisualiser.visualise(
                    exporter.swarmAgent.app.name,
                    exporter.soundValuesPath,
                    exporter.noiseSensorTemperaturePath,
                    exporter.noiseSensorCpuLoadPath,
                    exporter.noiseSensorClassifierCountPath,
                    exporter.processedFilePath,
                    exporter.fileMigrationCountPath,
                    exporter.sunIntensityPath,
                    energyValues,
                    exportCdfToCsv(ScenarioBase.RESULT_DIRECTORY, exporter.swarmAgent)
            ).write();
        }

        SimLogger.logEmptyLine();
        SimLogger.logRes("assignment", noiseAssignment);
        SimLogger.logRes(swarmAgent.app.name, noiseSensorGroup);

        long soundFilesOnNoiseSensors = 0;
        long soundFilesOnRemoteServers = 0;
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

        if (resFile != null) {
            soundFilesOnRemoteServers =
                    resFile.size / (long) Config.NOISE_CLASS_CONFIGURATION.get("resFileSize");
        }

        if (remoteServer != null && !remoteServer.latencies.isEmpty()) {
            Collections.sort(remoteServer.latencies);
            int n = remoteServer.latencies.size();
            double max = remoteServer.latencies.get(n - 1) / 1000.0;
            double p95 = remoteServer.latencies.get((int) Math.ceil(n * 0.95) - 1) / 1000.0;
            double p99 = remoteServer.latencies.get((int) Math.ceil(n * 0.99) - 1) / 1000.0;
            double median = remoteServer.latencies.get(n / 2) / 1000.0;
            long count = remoteServer.latencies.stream().filter(l -> l <= 10_000).count();
            double percentage = count * 100.0 / remoteServer.latencies.size();
            double average = soundFilesOnRemoteServers == 0
                    ? 0.0
                    : RemoteServer.totalEndToEndLatency
                    / (double) soundFilesOnRemoteServers / 1000.0;

            SimLogger.logRes("latency-metrics", latency -> {
                latency.put("max-E2E_sec", max);
                latency.put("p95-E2E_sec", p95);
                latency.put("p99-E2E_sec", p99);
                latency.put("median-E2E_sec", median);
                latency.put("E2E-less-than-10s_percent", percentage);
                latency.put("average-E2E-latency_sec", average);
            });
        }

        SimLogger.logRes("scaling-decision-count", group -> group.putAll(swarmAgent.decisionType));

        double totalEnergy = 0;
        for (EnergyDataCollector ec : EnergyDataCollector.allEnergyCollectors.values()) {
            totalEnergy += ec.accumulatedEnergy / ScenarioBase.TO_KWH;
        }

        long totalGeneratedFiles = swarmAgent.totalGeneratedFiles;

        SimLogger.logEmptyLine();
        SimLogger.logRes("simulated-time_min",
                TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
        SimLogger.logRes("simulator-runtime_sec",
                TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));
        SimLogger.logRes("total-energy_kwh", totalEnergy);
        SimLogger.logRes("size-of-generated-files_mb",
                totalGeneratedFiles
                        * (long) Config.NOISE_CLASS_CONFIGURATION.get("soundFileSize")
                        / ScenarioBase.MB_IN_BYTE);
        SimLogger.logRes("number-of-sound-events", totalGeneratedFiles);
        SimLogger.logRes("number-of-offloaded-sound-events", NoiseSensor.totalOffloadedFiles);
        SimLogger.logRes("number-of-sound-events-requiring-processing",
                NoiseSensor.totalSoundEventsToProcess);
        SimLogger.logRes("number-of-processed-files", NoiseSensor.totalProcessedFiles);
        SimLogger.logRes("number-of-sound-files-on-noise-sensors", soundFilesOnNoiseSensors);
        SimLogger.logRes("number-of-sound-files-on-the-remote-servers", soundFilesOnRemoteServers);

        double avgTimeBelowThrottling = 0.0;
        for (NoiseAppCsvExporter exporter : NoiseAppCsvExporter.allNoiseAppCsvExporters.values()) {
            avgTimeBelowThrottling += calculateTimeBelowThrottling(
                    exporter.noiseSensorTemperaturePath,
                    (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold")
            );
        }

        SimLogger.logRes(
                "time-below-the-temperature-threshold_percent",
                NoiseAppCsvExporter.allNoiseAppCsvExporters.isEmpty()
                        ? 0.0
                        : avgTimeBelowThrottling / NoiseAppCsvExporter.allNoiseAppCsvExporters.size()
        );

        SimLogger.logRes("config-parameters", param -> {
            param.put("sound-level-threshold_db", Config.NOISE_CLASS_CONFIGURATION.get("soundThreshold"));
            param.put("min-CPU-temperature_celsius", Config.NOISE_CLASS_CONFIGURATION.get("minCpuTemp"));
            param.put("max-CPU-temperature_celsius", Config.NOISE_CLASS_CONFIGURATION.get("maxCpuTemp"));
            param.put("CPU-temperature-threshold_celsius", Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold"));
            param.put("min-container-count", Config.NOISE_CLASS_CONFIGURATION.get("minContainerCount"));
            param.put("scaling-cooldown_ms", Config.NOISE_CLASS_CONFIGURATION.get("cpuTimeWindow"));
            param.put("CPU-load-scale-up_percent", Config.NOISE_CLASS_CONFIGURATION.get("cpuLoadScaleUp"));
            param.put("CPU-load-scale-down_percent", Config.NOISE_CLASS_CONFIGURATION.get("cpuLoadScaleDown"));
            param.put("max-simulable-time-according-to-data_min",
                    noiseData.maxSimulationTimeMs / ScenarioBase.MINUTE_IN_MILLISECONDS);
            param.put("requested-prediction-horizon_min", request.metadata.predictionHorizonMin);
            param.put("seed", SeedSyncer.getSeed());
        });

        System.out.println(SimLogger.getResultsAsJson());
    }
}
