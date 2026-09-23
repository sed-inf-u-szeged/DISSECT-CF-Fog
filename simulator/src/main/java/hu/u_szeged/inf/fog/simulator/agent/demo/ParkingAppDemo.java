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
import hu.u_szeged.inf.fog.simulator.agent.management.parking.ParkingTimeBasedSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.util.ParkingAppCsvExporter;
import hu.u_szeged.inf.fog.simulator.common.util.CsvVisualiser;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class ParkingAppDemo {

    public static void main(String[] args) throws NetworkException {

        SimLogger.setLogging(1, true);

        SeedSyncer.setSeed(1234567890);

        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator.generateTransitions(0, 0, 0, 0, 0);

        // platform service
        Map<String, Integer> platformLatencyMap = new HashMap<>();

        long platformBandwidth = (long) Config.PARKING_CONFIGURATION.get("platformBandwidthBytesPerMs");

        Repository platformRepo = new Repository(1_099_511_627_776L, "platformRepo", platformBandwidth, platformBandwidth, platformBandwidth, platformLatencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
        platformRepo.setState(NetworkNode.State.RUNNING);
        PlatformService platformService = new PlatformService(platformRepo);

        // parking sensors
        ParkingSensor.ParkingZone[] zones = ParkingSensor.ParkingZone.values();
        for (int i = 0; i < (int) Config.PARKING_CONFIGURATION.get("sensorCount"); i++) {
            Map<String, Integer> nbiotLatencyMap = new HashMap<>();
            nbiotLatencyMap.put("platformRepo",  (int) Config.PARKING_CONFIGURATION.get("nbiotSensorToPlatformLatencyMs"));

            Map<String, Integer> bleLatencyMap = new HashMap<>();

            String id = "parking-sensor-" + i;
            long nbiotBandwidth = (long) Config.PARKING_CONFIGURATION.get("nbiotSensorToPlatformBandwidthBytesPerMs");
            Repository nbiotRepo = new Repository(8_388_608, id + "-nbiotRepo", nbiotBandwidth, nbiotBandwidth, nbiotBandwidth, nbiotLatencyMap,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            long bleBandwidth = (long) Config.PARKING_CONFIGURATION.get("bleSensorToGatewayBandwidthBytesPerMs");
            Repository bleRepo = new Repository(8_388_608, id + "-bleRepo", bleBandwidth, bleBandwidth, bleBandwidth, bleLatencyMap,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
            nbiotRepo.setState(NetworkNode.State.RUNNING);
            bleRepo.setState(NetworkNode.State.RUNNING);

            ParkingSensor parkingSensor = new ParkingSensor(id, platformService, nbiotRepo, bleRepo, (int) Config.PARKING_CONFIGURATION.get("batteryCapacity"),
                    (ParkingSensor.ParkingMode) Config.PARKING_CONFIGURATION.get("initialParkingMode"), zones[i % zones.length]);
        }

        // gateways
        int sensorsPerGateway = (int) Config.PARKING_CONFIGURATION.get("sensorsPerGateway");

        int reqGateways = (int) Math.ceil( ParkingSensor.allParkingSensors.size() / (double) sensorsPerGateway);

        for (int i = 0; i < reqGateways; i++) {
            Map<String, Integer> gatewayLatencyMap = new HashMap<>();
            gatewayLatencyMap.put("platformRepo",  (int) Config.PARKING_CONFIGURATION.get("gatewayToPlatformLatencyMs"));

            long bleBandwidth = (long) Config.PARKING_CONFIGURATION.get("bleSensorToGatewayBandwidthBytesPerMs");
            long gatewayToPlatformBandwidth = (long) Config.PARKING_CONFIGURATION.get("gatewayToPlatformBandwidthBytesPerMs");

            Repository gatewayRepo = new Repository(8_388_608,"gatewayRepo-" + i,bleBandwidth,gatewayToPlatformBandwidth,gatewayToPlatformBandwidth,
                    gatewayLatencyMap,transitions.get(PowerTransitionGenerator.PowerStateKind.storage),transitions.get(PowerTransitionGenerator.PowerStateKind.network));
            gatewayRepo.setState(NetworkNode.State.RUNNING);

            ParkingGateway gateway = new ParkingGateway("parking-gateway-" + i, gatewayRepo, platformService, new ArrayList<>());

            for (int j = 0; j < sensorsPerGateway && i * sensorsPerGateway + j < ParkingSensor.allParkingSensors.size();j++) {
                ParkingSensor sensor = ParkingSensor.allParkingSensors.get(i * sensorsPerGateway + j);

                sensor.bleRepository.getLatencies().put(gatewayRepo.getName(), (int) Config.PARKING_CONFIGURATION.get("bleSensorToGatewayLatencyMs"));

                gateway.addSensor(sensor);
            }
        }

        // swarm agent
        AgentApplication app = new AgentApplication();
        app.name = "parking-app";
        ParkingTimeBasedSwarmAgent sa = new ParkingTimeBasedSwarmAgent(app);
        sa.observedAppComponents.addAll(ParkingSensor.allParkingSensors);
        sa.start((long) Config.PARKING_CONFIGURATION.get("cooldownFreq"));

        final long starttime = System.nanoTime();
        Timed.simulateUntil((long) Config.PARKING_CONFIGURATION.get("simLength"));
        final long stoptime = System.nanoTime();

        // CSV visualisation
        for (ParkingAppCsvExporter parkingAppCsvExporter : ParkingAppCsvExporter.allParkingAppCsvExporters.values()){
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
        for (ParkingGateway parkingGateway : ParkingGateway.allParkingGateways) {
            SimLogger.logRes("Gateway ID: " + parkingGateway.id);
            for (ParkingSensor sensor : parkingGateway.observedSensors) {
                SimLogger.logRes("\tSensor ID: " + sensor.id + ", Battery Level: " + sensor.batteryLevel + ", Event Count: " + sensor.eventCount + ", " + sensor.zone.toString() );
                totalRemainingBattery += sensor.batteryLevel;
            }
        }
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
            int sensorCount = sensorCountByZone.get(zone);

            double averageConsumed = sensorCount > 0 ? totalConsumed / (double) sensorCount : 0.0;

            SimLogger.logRes("\t" + zone + ": total consumed = " + totalConsumed + ", sensors = " + sensorCount + ", average per sensor = " + averageConsumed);
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
        SimLogger.logRes("\tOrchestration enabled: " + Config.PARKING_CONFIGURATION.get("orchestrationEnabled"));
        SimLogger.logRes("\tInitial parking mode: " + Config.PARKING_CONFIGURATION.get("initialParkingMode"));
        SimLogger.logRes("\tNumber of sensors: " + Config.PARKING_CONFIGURATION.get("sensorCount"));
        SimLogger.logRes("\tNumber of sensors per gateway: " + Config.PARKING_CONFIGURATION.get("sensorsPerGateway"));
        // savingPercent = (baselineConsumed - timeBasedConsumed) / baselineConsumed * 100
    }
}
