package hu.u_szeged.inf.fog.simulator.agent.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.RemoteServer;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingGateway;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.PlatformService;
import hu.u_szeged.inf.fog.simulator.agent.management.parking.ParkingSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.util.ParkingAppCsvExporter;
import hu.u_szeged.inf.fog.simulator.common.util.CsvVisualiser;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class ParkingAppDemo {

    public static void main(String[] args) throws NetworkException {

        SimLogger.setLogging(1, true);

        SeedSyncer.setSeed(1234567890);

        Map<String, Integer> sharedLatencyMap = new HashMap<>();
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator.generateTransitions(0, 0, 0, 0, 0);

        // platform service
        Repository platformRepo = new Repository(1_099_511_627_776L, "platformRepo", 125_000L, 125_000L, 125_000L, sharedLatencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
        sharedLatencyMap.put("platformRepo", 3_000);
        platformRepo.setState(NetworkNode.State.RUNNING);
        PlatformService platformService = new PlatformService(platformRepo);

        // parking sensors
        for (int i = 0; i < 10; i++) {
            String id = "parking-sensor-" + i;
            Repository nbiotRepo = new Repository(8_388_608, id + "-nbiotRepo", 13, 13, 13, sharedLatencyMap,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
            Repository bleRepo = new Repository(8_388_608, id + "-bleRepo", 125, 125, 125, sharedLatencyMap,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
            nbiotRepo.setState(NetworkNode.State.RUNNING);
            bleRepo.setState(NetworkNode.State.RUNNING);

            ParkingSensor parkingSensor = new ParkingSensor(id, platformService, nbiotRepo, bleRepo, 600_000,
                    //ParkingSensor.ParkingMode.NBIOT_PUSH,ParkingSensor.ParkingProfile.NORMAL);
                    ParkingSensor.ParkingMode.BLE_POLL,ParkingSensor.ParkingProfile.NORMAL, ParkingSensor.ParkingZone.LOADING);
        }

        // gateways
        int reqGateways = (int) Math.ceil(ParkingSensor.allParkingSensors.size() / 10.0);
        for (int i = 0; i < reqGateways; i++) {
            Map<String, Integer> latencyMap = new HashMap<>();
            Repository gatewayRepo = new Repository(8_388_608, "gatewayRepo-" + i, 125, 1250, 1250, latencyMap,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            gatewayRepo.setState(NetworkNode.State.RUNNING);
            sharedLatencyMap.put("gatewayRepo-" + i, 1000);
            latencyMap.put("platformRepo", 50);

            ParkingGateway gateway = new ParkingGateway("parking-gateway-" + i, gatewayRepo, platformService, new ArrayList<>());

            for (int j = 0; j < 10 && i * 10 + j < ParkingSensor.allParkingSensors.size(); j++) {
                gateway.addSensor(ParkingSensor.allParkingSensors.get(i * 10 + j));
            }
        }

        // swarm agent
        AgentApplication app = new AgentApplication();
        app.name = "parking-app";
        ParkingSwarmAgent sa = new ParkingSwarmAgent(app);
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
                    parkingAppCsvExporter.batteryStatusPath
            ).write();
        }

        /* results */
        SimLogger.logEmptyLine();
        SimLogger.logRes("Simulated time (minutes): " + TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
        SimLogger.logRes("Simulator runtime (seconds): " + TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));

        SimLogger.logRes("Total data received by the platform: " + platformService.receivedDataSize);
        SimLogger.logRes("Total events received by the platform: " + platformService.receivedEventCount + " (generated by sensors: " + ParkingSensor.totalParkingEvents + ")");
        for (ParkingSensor sensor : ParkingSensor.allParkingSensors) {
            SimLogger.logRes("\tSensor ID: " + sensor.id + ", Battery Level: " + sensor.batteryLevel + ", Event Count: " + sensor.eventCount);
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
    }
}
