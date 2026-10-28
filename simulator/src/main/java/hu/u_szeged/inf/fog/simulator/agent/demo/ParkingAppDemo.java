package hu.u_szeged.inf.fog.simulator.agent.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingGateway;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.PlatformService;
import hu.u_szeged.inf.fog.simulator.agent.dt.ParkingTraceData;
import hu.u_szeged.inf.fog.simulator.agent.management.parking.*;
import hu.u_szeged.inf.fog.simulator.agent.forecast.ForecasterManager;
import hu.u_szeged.inf.fog.simulator.agent.util.ParkingAppCsvExporter;
import hu.u_szeged.inf.fog.simulator.agent.util.ParkingTraceExporter;
import hu.u_szeged.inf.fog.simulator.common.util.CsvVisualiser;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ParkingAppDemo {

    public static void main(String[] args) throws NetworkException {
        SimLogger.setLogging(1, true);
        SeedSyncer.setSeed(Config.RANDOM_SEED);

        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator.generateTransitions(0, 0, 0, 0, 0);

        // platform service
        Map<String, Integer> platformLatencyMap = new HashMap<>();

        long platformBandwidth = (long) Config.PARKING_CONFIGURATION.get("platformBandwidthBytesPerMs");

        Repository platformRepo = new Repository(1_099_511_627_776L, "platformRepo", platformBandwidth, platformBandwidth, platformBandwidth, platformLatencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
        platformRepo.setState(NetworkNode.State.RUNNING);
        PlatformService platformService = new PlatformService(platformRepo);

        long simulationLength = (long) Config.PARKING_CONFIGURATION.get("simLength");
        int traceSensorCount = 0;
        int totalTraceEvents = 0;
        long traceLength = 0;

        String workloadSource = Config.PARKING_CONFIGURATION.get("workloadSource").toString();
        ParkingTraceExporter traceExporter = null;
        if ("synthetic".equalsIgnoreCase(workloadSource)) {
            // parking sensors
            Config.ParkingScenario parkingScenario = (Config.ParkingScenario) Config.PARKING_CONFIGURATION.get("parkingScenario");
            int sensorCount = (int) Config.PARKING_CONFIGURATION.get("sensorCount");

            boolean traceExportEnabled = (boolean) Config.PARKING_CONFIGURATION.get("syntheticTraceExportEnabled");
            traceExporter = null;

            if (traceExportEnabled) {
                traceExporter = new ParkingTraceExporter(OffsetDateTime.parse((String) Config.PARKING_CONFIGURATION.get("traceStartTime"))
                );
            }

            for (int i = 0; i < sensorCount; i++) {
                Map<String, Integer> nbiotLatencyMap = new HashMap<>();
                nbiotLatencyMap.put("platformRepo", (int) Config.PARKING_CONFIGURATION.get("nbiotSensorToPlatformLatencyMs"));
                Map<String, Integer> bleLatencyMap = new HashMap<>();
                String id = "parking-sensor-" + i;

                long nbiotBandwidth = (long) Config.PARKING_CONFIGURATION.get("nbiotSensorToPlatformBandwidthBytesPerMs");
                Repository nbiotRepo = new Repository(
                        8_388_608,
                        id + "-nbiotRepo",
                        nbiotBandwidth,
                        nbiotBandwidth,
                        nbiotBandwidth,
                        nbiotLatencyMap,
                        transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                        transitions.get(PowerTransitionGenerator.PowerStateKind.network)
                );

                long bleBandwidth = (long) Config.PARKING_CONFIGURATION.get("bleSensorToGatewayBandwidthBytesPerMs");

                Repository bleRepo = new Repository(
                        8_388_608,
                        id + "-bleRepo",
                        bleBandwidth,
                        bleBandwidth,
                        bleBandwidth,
                        bleLatencyMap,
                        transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                        transitions.get(PowerTransitionGenerator.PowerStateKind.network)
                );

                nbiotRepo.setState(NetworkNode.State.RUNNING);
                bleRepo.setState(NetworkNode.State.RUNNING);

                ParkingSensor.ParkingZone zone = getZoneForSensor(i, sensorCount, parkingScenario);

                ParkingSensor parkingSensor = new ParkingSensor(
                        id,
                        platformService,
                        nbiotRepo,
                        bleRepo,
                        (int) Config.PARKING_CONFIGURATION.get("batteryCapacity"),
                        (ParkingSensor.ParkingMode) Config.PARKING_CONFIGURATION.get(
                                "initialParkingMode"
                        ),
                        zone,
                        traceExporter
                );
                if (traceExporter != null) {
                    traceExporter.registerSensor(parkingSensor);
                }
            }

            // gateways
            int sensorsPerGateway = (int) Config.PARKING_CONFIGURATION.get("sensorsPerGateway");

            int reqGateways = (int) Math.ceil(ParkingSensor.allParkingSensors.size() / (double) sensorsPerGateway);

            for (int i = 0; i < reqGateways; i++) {
                Map<String, Integer> gatewayLatencyMap = new HashMap<>();
                gatewayLatencyMap.put("platformRepo", (int) Config.PARKING_CONFIGURATION.get("gatewayToPlatformLatencyMs"));

                long bleBandwidth = (long) Config.PARKING_CONFIGURATION.get("bleSensorToGatewayBandwidthBytesPerMs");
                long gatewayToPlatformBandwidth = (long) Config.PARKING_CONFIGURATION.get("gatewayToPlatformBandwidthBytesPerMs");

                Repository gatewayRepo = new Repository(8_388_608, "gatewayRepo-" + i, bleBandwidth, gatewayToPlatformBandwidth, gatewayToPlatformBandwidth,
                        gatewayLatencyMap, transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
                gatewayRepo.setState(NetworkNode.State.RUNNING);

                ParkingGateway gateway = new ParkingGateway("parking-gateway-" + i, gatewayRepo, platformService, new ArrayList<>());

                for (int j = 0; j < sensorsPerGateway && i * sensorsPerGateway + j < ParkingSensor.allParkingSensors.size(); j++) {
                    ParkingSensor sensor = ParkingSensor.allParkingSensors.get(i * sensorsPerGateway + j);

                    sensor.bleRepository.getLatencies().put(gatewayRepo.getName(), (int) Config.PARKING_CONFIGURATION.get("bleSensorToGatewayLatencyMs"));

                    gateway.addSensor(sensor);
                }
            }
        } else if ("trace".equalsIgnoreCase(workloadSource)) {
            try {
                ParkingTraceData traceData = ParkingTraceData.load((Path) Config.PARKING_CONFIGURATION.get("traceStatePath"),
                        (Path) Config.PARKING_CONFIGURATION.get("tracePath"),
                        OffsetDateTime.parse((String) Config.PARKING_CONFIGURATION.get("traceStartTime")),
                        (long) Config.PARKING_CONFIGURATION.get("forecastHistoryWindowMs"));
                List<ParkingTraceData.SensorState> sensorStates = new ArrayList<>(traceData.sensors.values());
                Random coordinateRandom = SeedSyncer.centralRnd;

                simulationLength = Math.min(simulationLength, traceData.maxSimulationTimeMs);
                traceSensorCount = sensorStates.size();
                totalTraceEvents = traceData.eventsBySensor.values().stream().mapToInt(Deque::size).sum();
                traceLength = traceData.maxSimulationTimeMs;

                List<ParkingTraceData.SensorState> locatedSensors = sensorStates.stream().filter(sensor -> !Double.isNaN(sensor.latitude()) && !Double.isNaN(sensor.longitude())).toList();
                for (int i = 0; i < sensorStates.size(); i++) {
                    ParkingTraceData.SensorState sensor = sensorStates.get(i);
                    if (Double.isNaN(sensor.latitude()) || Double.isNaN(sensor.longitude())) {
                        ParkingTraceData.SensorState anchor = locatedSensors.get(coordinateRandom.nextInt(locatedSensors.size()));
                        double distanceMeters = 5.0 + coordinateRandom.nextDouble() * 10.0;
                        double angle = coordinateRandom.nextDouble() * 2.0 * Math.PI;
                        double latitudeOffset = Math.cos(angle) * distanceMeters / 111320.0;
                        double longitudeOffset = Math.sin(angle) * distanceMeters / (111320.0 * Math.cos(Math.toRadians(anchor.latitude())));
                        sensorStates.set(i, new ParkingTraceData.SensorState(sensor.id(), sensor.type(), anchor.latitude() + latitudeOffset, anchor.longitude() + longitudeOffset, sensor.occupied(), sensor.failed()));
                    }
                }

                Map<String, ParkingSensor> parkingSensorsById = new HashMap<>();
                for (ParkingTraceData.SensorState sensorState : sensorStates) {
                    Map<String, Integer> nbiotLatencyMap = new HashMap<>();
                    nbiotLatencyMap.put("platformRepo", (int) Config.PARKING_CONFIGURATION.get("nbiotSensorToPlatformLatencyMs"));
                    Map<String, Integer> bleLatencyMap = new HashMap<>();
                    long nbiotBandwidth = (long) Config.PARKING_CONFIGURATION.get("nbiotSensorToPlatformBandwidthBytesPerMs");
                    Repository nbiotRepo = new Repository(8_388_608, sensorState.id() + "-nbiotRepo", nbiotBandwidth, nbiotBandwidth, nbiotBandwidth, nbiotLatencyMap, transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
                    long bleBandwidth = (long) Config.PARKING_CONFIGURATION.get("bleSensorToGatewayBandwidthBytesPerMs");
                    Repository bleRepo = new Repository(8_388_608, sensorState.id() + "-bleRepo", bleBandwidth, bleBandwidth, bleBandwidth, bleLatencyMap, transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
                    nbiotRepo.setState(NetworkNode.State.RUNNING);
                    bleRepo.setState(NetworkNode.State.RUNNING);

                    Object initialParkingModeConfig = Config.PARKING_CONFIGURATION.get("initialParkingMode");
                    ParkingSensor.ParkingMode initialParkingMode;
                    if ("random".equals(initialParkingModeConfig)) {
                        initialParkingMode = SeedSyncer.centralRnd.nextBoolean() ? ParkingSensor.ParkingMode.NBIOT_PUSH : ParkingSensor.ParkingMode.BLE_POLL;
                    } else {
                        initialParkingMode = (ParkingSensor.ParkingMode) initialParkingModeConfig;
                    }

                    ParkingSensor parkingSensor = new ParkingSensor(sensorState.id(), platformService, nbiotRepo, bleRepo, (int) Config.PARKING_CONFIGURATION.get("batteryCapacity"), initialParkingMode, ParkingSensor.ParkingZone.COMMERCIAL, traceData.eventsBySensor.get(sensorState.id()));
                    parkingSensor.isTaken = sensorState.occupied();

                    String orchestrationStrategy = (String) Config.PARKING_CONFIGURATION.get("orchestrationStrategy");
                    if ("forecast".equals(orchestrationStrategy) || "forecast-2h".equals(orchestrationStrategy) || "forecast-lookahead".equals(orchestrationStrategy)) {
                        for (ParkingSensor.ParkingObservation observation : traceData.warmupObservationsBySensor.get(sensorState.id())) {
                            parkingSensor.addObservation(observation.simulationTimeMs(), observation.occupied());
                        }
                    }
                    parkingSensorsById.put(sensorState.id(), parkingSensor);
                }

                List<ParkingTraceData.SensorState> unassignedSensors = new ArrayList<>(sensorStates);
                int sensorsPerGateway = (int) Config.PARKING_CONFIGURATION.get("sensorsPerGateway");
                int gatewayIndex = 0;

                while (!unassignedSensors.isEmpty()) {
                    ParkingTraceData.SensorState referenceSensor = unassignedSensors.remove(0);
                    unassignedSensors.sort(Comparator.comparingDouble(sensor -> haversineDistance(referenceSensor.latitude(), referenceSensor.longitude(), sensor.latitude(), sensor.longitude())));
                    List<ParkingTraceData.SensorState> gatewaySensors = new ArrayList<>();
                    gatewaySensors.add(referenceSensor);
                    int additionalSensors = Math.min(sensorsPerGateway - 1, unassignedSensors.size());
                    for (int i = 0; i < additionalSensors; i++) {
                        gatewaySensors.add(unassignedSensors.remove(0));
                    }
                    Map<String, Integer> gatewayLatencyMap = new HashMap<>();
                    gatewayLatencyMap.put("platformRepo", (int) Config.PARKING_CONFIGURATION.get("gatewayToPlatformLatencyMs"));
                    long bleBandwidth = (long) Config.PARKING_CONFIGURATION.get("bleSensorToGatewayBandwidthBytesPerMs");
                    long gatewayToPlatformBandwidth = (long) Config.PARKING_CONFIGURATION.get("gatewayToPlatformBandwidthBytesPerMs");
                    Repository gatewayRepo = new Repository(8_388_608, "gatewayRepo-" + gatewayIndex, bleBandwidth, gatewayToPlatformBandwidth, gatewayToPlatformBandwidth, gatewayLatencyMap, transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
                    gatewayRepo.setState(NetworkNode.State.RUNNING);
                    ParkingGateway gateway = new ParkingGateway("parking-gateway-" + gatewayIndex, gatewayRepo, platformService, new ArrayList<>());
                    for (ParkingTraceData.SensorState sensorState : gatewaySensors) {
                        ParkingSensor sensor = parkingSensorsById.get(sensorState.id());
                        sensor.bleRepository.getLatencies().put(gatewayRepo.getName(), (int) Config.PARKING_CONFIGURATION.get("bleSensorToGatewayLatencyMs"));
                        gateway.addSensor(sensor);
                    }
                    gatewayIndex++;
                }

            } catch (Exception e) {
                throw new RuntimeException("Failed to load parking trace", e);
            }
        } else {
            throw new IllegalArgumentException("Unknown workload source: " + workloadSource);
        }

        String orchestrationStrategy = (String) Config.PARKING_CONFIGURATION.get("orchestrationStrategy");
        if ("forecast".equals(orchestrationStrategy) || "forecast-2h".equals(orchestrationStrategy) || "forecast-lookahead".equals(orchestrationStrategy)) {
            ForecasterManager.getInstance((String) Config.PARKING_CONFIGURATION.get("predictorDir"), 1, 1337, (String) Config.PARKING_CONFIGURATION.get("predictorModelPath"));
        }

        // swarm agent
        AgentApplication app = new AgentApplication();
        app.name = "parking-app";
        ParkingSwarmAgent sa;
        switch (orchestrationStrategy) {
            case "reactive" -> sa = new ParkingReactiveSwarmAgent(app);
            case "group-level" -> sa = new ParkingGroupLevelSwarmAgent(app);
            case "time-based" -> sa = new ParkingTimeBasedSwarmAgent(app);
            case "forecast" -> sa = new ParkingForecastSwarmAgent(app);
            case "forecast-2h" -> sa = new ParkingForecast2hSwarmAgent(app);
            case "forecast-lookahead" -> sa = new ParkingForecastLookAheadSwarmAgent(app);
            case "oracle" -> sa = new ParkingOracleSwarmAgent(app);
            default -> throw new IllegalArgumentException("Unknown parking orchestration strategy: " + orchestrationStrategy);
        }
        sa.observedAppComponents.addAll(ParkingSensor.allParkingSensors);
        sa.start((long) Config.PARKING_CONFIGURATION.get("cooldownFreq"));

        final long starttime = System.nanoTime();
        Timed.simulateUntil(simulationLength);

        if ("forecast".equals(orchestrationStrategy) || "forecast-2h".equals(orchestrationStrategy) || "forecast-lookahead".equals(orchestrationStrategy)) {
            ForecasterManager.getInstance().shutdownAll();
        }
        if (traceExporter != null) {
            traceExporter.close();
        }

        final long stoptime = System.nanoTime();

        // CSV visualisation
        for (ParkingAppCsvExporter parkingAppCsvExporter : ParkingAppCsvExporter.allParkingAppCsvExporters.values()) {
            CsvVisualiser.visualise(
                    parkingAppCsvExporter.swarmAgent.app.name,
                    parkingAppCsvExporter.parkingSpotStatusPath,
                    parkingAppCsvExporter.batteryStatusPath,
                    parkingAppCsvExporter.zoneStatusPath
            ).write();
        }

        /* results */
        SimLogger.logEmptyLine();
        SimLogger.logRes("Simulated time (minutes): " + TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
        SimLogger.logRes("Simulator runtime (seconds): " + TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));

        SimLogger.logRes("Total data received by the platform: " + platformService.receivedDataSize);
        SimLogger.logRes("Total events received by the platform: " + platformService.receivedEventCount + " (generated by sensors: " + ParkingSensor.totalParkingEvents + ")");

        long totalRemainingBattery = 0;
        int totalModeSwitchCount = 0;
        for (ParkingGateway parkingGateway : ParkingGateway.allParkingGateways) {
            SimLogger.logRes("Gateway ID: " + parkingGateway.id);
            for (ParkingSensor sensor : parkingGateway.observedSensors) {
                SimLogger.logRes("\tSensor ID: " + sensor.id + ", Battery Level: " + sensor.batteryLevel + ", Event Count: " + sensor.eventCount +
                        ", Mode Switch Count: " + sensor.modeSwitchCount + ", " + sensor.zone.toString());
                totalRemainingBattery += sensor.batteryLevel;
                totalModeSwitchCount += sensor.modeSwitchCount;
            }
        }
        SimLogger.logRes("Total mode switch count: " + totalModeSwitchCount);

        int initialBattery = (int) Config.PARKING_CONFIGURATION.get("batteryCapacity");
        long initialTotalBattery = (long) ParkingSensor.allParkingSensors.size() * initialBattery;
        long totalBatteryConsumed = initialTotalBattery - totalRemainingBattery;

        SimLogger.logRes("Total battery consumed: " + totalBatteryConsumed + ", remaining: " + totalRemainingBattery + ", consumed (%): " + (totalBatteryConsumed * 100.0 / initialTotalBattery));

        Map<ParkingSensor.ParkingZone, Long> consumedByZone = new EnumMap<>(ParkingSensor.ParkingZone.class);
        Map<ParkingSensor.ParkingZone, Integer> sensorCountByZone = new EnumMap<>(ParkingSensor.ParkingZone.class);

        for (ParkingSensor.ParkingZone zone : ParkingSensor.ParkingZone.values()) {
            consumedByZone.put(zone, 0L);
            sensorCountByZone.put(zone, 0);
        }

        for (ParkingSensor sensor : ParkingSensor.allParkingSensors) {
            long consumed = initialBattery - sensor.batteryLevel;
            consumedByZone.put(sensor.zone, consumedByZone.get(sensor.zone) + consumed);
            sensorCountByZone.put(sensor.zone, sensorCountByZone.get(sensor.zone) + 1);
        }

        SimLogger.logRes("Battery consumption by zone:");

        for (ParkingSensor.ParkingZone zone : ParkingSensor.ParkingZone.values()) {
            long totalConsumed = consumedByZone.get(zone);
            int sensorCountPerZone = sensorCountByZone.get(zone);

            double averageConsumed = sensorCountPerZone > 0 ? totalConsumed / (double) sensorCountPerZone : 0.0;

            SimLogger.logRes("\t" + zone + ": total consumed = " + totalConsumed + ", sensors = " + sensorCountPerZone + ", average per sensor = " + averageConsumed);
        }

        Collections.sort(platformService.latencies);
        int n = platformService.latencies.size();
        double max = platformService.latencies.get(n - 1) / 1000.0;
        double p95 = platformService.latencies.get((int) Math.ceil(n * 0.95) - 1) / 1000.0;
        double p99 = platformService.latencies.get((int) Math.ceil(n * 0.99) - 1) / 1000.0;
        double median = platformService.latencies.get(n / 2) / 1000.0;
        SimLogger.logRes("latency-metrics", latency -> {
            latency.put("max-E2E_sec", max);
            latency.put("p95-E2E_sec", p95);
            latency.put("p99-E2E_sec", p99);
            latency.put("median-E2E_sec", median);
            latency.put("average-E2E-latency_sec", PlatformService.totalEndToEndLatency / platformService.receivedEventCount / 1000.0);
        });

        SimLogger.logEmptyLine();
        SimLogger.logRes("Configuration:");
        SimLogger.logRes("\tOrchestration strategy: " + Config.PARKING_CONFIGURATION.get("orchestrationStrategy"));
        SimLogger.logRes("\tCooldown/orchestration frequency (ms): " + Config.PARKING_CONFIGURATION.get("cooldownFreq"));
        SimLogger.logRes("\tWorkload source: " + workloadSource);
        SimLogger.logRes("\tOrchestration enabled: " + Config.PARKING_CONFIGURATION.get("orchestrationEnabled"));
        SimLogger.logRes("\tInitial parking mode: " + Config.PARKING_CONFIGURATION.get("initialParkingMode"));
        SimLogger.logRes("\tNumber of sensors per gateway: " + Config.PARKING_CONFIGURATION.get("sensorsPerGateway"));
        if ("synthetic".equalsIgnoreCase(workloadSource)) {
            SimLogger.logRes("\tParking scenario: " + Config.PARKING_CONFIGURATION.get("parkingScenario"));
            SimLogger.logRes("\tNumber of sensors: " + Config.PARKING_CONFIGURATION.get("sensorCount"));
        } else {
            SimLogger.logRes("\tNumber of sensors: " + traceSensorCount);
        }
        if ("trace".equalsIgnoreCase(workloadSource)) {
            SimLogger.logRes("Trace gateways: " + ParkingGateway.allParkingGateways.size());
            SimLogger.logRes("Trace length (minutes): " + TimeUnit.MINUTES.convert(traceLength, TimeUnit.MILLISECONDS));
            SimLogger.logRes("Effective simulation length (minutes): " + TimeUnit.MINUTES.convert(simulationLength, TimeUnit.MILLISECONDS));
            for (ParkingGateway gateway : ParkingGateway.allParkingGateways) {
                SimLogger.logRes("Gateway " + gateway.id + " sensors: " + gateway.observedSensors.size());
            }
        }
        if ("forecast".equals(orchestrationStrategy) || "forecast-2h".equals(orchestrationStrategy) || "forecast-lookahead".equals(orchestrationStrategy)) {
            SimLogger.logRes("\tForecast threshold factor: " + Config.PARKING_CONFIGURATION.get("forecastThresholdFactor"));
        }
        // savingPercent = (baselineConsumed - timeBasedConsumed) / baselineConsumed * 100
    }

    private static ParkingSensor.ParkingZone getZoneForSensor(int sensorIndex, int sensorCount, Config.ParkingScenario parkingScenario) {

        return switch (parkingScenario) {
            case COMMERCIAL_ONLY -> ParkingSensor.ParkingZone.COMMERCIAL;
            case LOADING_ONLY -> ParkingSensor.ParkingZone.LOADING;
            case RESIDENTIAL_ONLY -> ParkingSensor.ParkingZone.RESIDENTIAL;
            case BALANCED -> {
                int type = sensorIndex % 3;

                yield switch (type) {
                    case 0 -> ParkingSensor.ParkingZone.COMMERCIAL;
                    case 1 -> ParkingSensor.ParkingZone.LOADING;
                    default -> ParkingSensor.ParkingZone.RESIDENTIAL;
                };
            }
            case COMMERCIAL_HEAVY -> {
                double ratio = sensorIndex / (double) sensorCount;

                if (ratio < 0.60) {
                    yield ParkingSensor.ParkingZone.COMMERCIAL;
                } else if (ratio < 0.80) {
                    yield ParkingSensor.ParkingZone.LOADING;
                } else {
                    yield ParkingSensor.ParkingZone.RESIDENTIAL;
                }
            }
        };
    }

    private static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371000.0;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
}
