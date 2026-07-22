package hu.u_szeged.inf.fog.simulator.agent.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.PlatformService;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ParkingAppDemo {

    public static void main(String[] args) throws NetworkException {

        SimLogger.setLogging(1, true);

        Map<String, Integer> sharedLatencyMap = new HashMap<>();
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator.generateTransitions(0, 0, 0, 0, 0);

        // platform service
        Repository nbiotPlatformRepo = new Repository(1_099_511_627_776L, "nbiotPlatformRepo", 125_000L, 125_000L, 125_000L, sharedLatencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
        sharedLatencyMap.put("nbiotPlatformRepo", 3_000);
        nbiotPlatformRepo.setState(NetworkNode.State.RUNNING);
        PlatformService platformService = new PlatformService(nbiotPlatformRepo, null);

        // gateways

        // parking sensors
        for (int i = 0; i < 10; i++) {
            String id = "parking-sensor-" + i;
            Repository nbiotRepo = new Repository(8_388_608, id + "-nbiotRepo", 13, 13, 13, sharedLatencyMap,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
            Repository bleRepo = new Repository(8_388_608, id + "-blteRepo", 125, 125, 125, sharedLatencyMap,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage), transitions.get(PowerTransitionGenerator.PowerStateKind.network));
            nbiotRepo.setState(NetworkNode.State.RUNNING);
            bleRepo.setState(NetworkNode.State.RUNNING);

            ParkingSensor parkingSensor = new ParkingSensor(id, platformService, nbiotRepo, bleRepo, 600_000,
                    ParkingSensor.ParkingMode.NBIOT_PUSH,ParkingSensor.ParkingProfile.NORMAL);
        }

        final long starttime = System.nanoTime();
        Timed.simulateUntil((long) Config.DUMMY_CONFIGURATION.get("simLength"));
        final long stoptime = System.nanoTime();

        /* results */
        SimLogger.logEmptyLine();
        SimLogger.logRes("Simulated time (minutes): " + TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
        SimLogger.logRes("Simulator runtime (seconds): " + TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));

        SimLogger.logRes("Received data size on the platform: " + platformService.receivedDataSize);
        SimLogger.logRes("Received event count on the platform: " + platformService.receivedEventCount);
        for (ParkingSensor sensor : ParkingSensor.allParkingSensors) {
            SimLogger.logRes("\tSensor ID: " + sensor.id + ", Battery Level: " + sensor.batteryLevel);
        }

    }
}
