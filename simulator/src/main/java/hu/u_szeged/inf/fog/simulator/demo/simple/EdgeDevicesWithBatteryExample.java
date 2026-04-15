package hu.u_szeged.inf.fog.simulator.demo.simple;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.application.strategy.RuntimeAwareApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.iot.Battery;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.EdgeDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.*;
import hu.u_szeged.inf.fog.simulator.common.util.EnergyDataCollector;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class EdgeDevicesWithBatteryExample {

    public static void main(String[] args) throws Exception {
        SimLogger.setLogging(1, true);

        String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";

        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1_073_741_824L);
        AlterableResourceConstraints arc = new AlterableResourceConstraints(2, 0.001, 4_294_967_296L);

        ComputingAppliance cloud1 = new ComputingAppliance(cloudfile, "cloud1", new GeoLocation(47.45, 21.3), 100);
        ComputingAppliance fog1 = new ComputingAppliance(cloudfile, "fog1", new GeoLocation(47.6, 17.9), 50);
        ComputingAppliance fog2 = new ComputingAppliance(cloudfile, "fog2", new GeoLocation(46.0, 18.2), 50);

        new EnergyDataCollector("cloud1", cloud1.iaas, false, true);
        new EnergyDataCollector("fog1", fog1.iaas, false, true);
        new EnergyDataCollector("fog2", fog2.iaas, false, true);

        fog1.setParent(cloud1, 77);
        fog2.setParent(cloud1, 80);

        fog1.addNeighbor(fog2, 33);

        Instance instance1 = new Instance("instance1", va, arc, 0.0255 / 60 / 60 / 1000);
        Instance instance2 = new Instance("instance2", va, arc, 0.051 / 60 / 60 / 1000);
        Instance instance3 = new Instance("instance3", va, arc, 0.102 / 60 / 60 / 1000);

        Application application1 = new Application("App-1", 60 * 1000, 250, 2_500, false,
                new RuntimeAwareApplicationStrategy(0.9, 2.0), instance3);
        Application application2 = new Application("App-2", 60 * 1000, 250, 2_500, true,
                new RuntimeAwareApplicationStrategy(0.9, 2.0), instance2);
        Application application3 = new Application("App-3", 60 * 1000, 250, 2_500, true,
                new RuntimeAwareApplicationStrategy(0.9, 2.0), instance1);

        cloud1.addApplication(application1);
        fog1.addApplication(application2);
        fog2.addApplication(application3);

        ArrayList<Device> deviceList = new ArrayList<Device>();
        for (int i = 0; i < 6; i++) {
            Device device;
            Battery battery;
            double step = SeedSyncer.centralRnd.nextDouble();

            battery = new Battery("battery"+i, Battery.BatteryType.PHONE_BATTERY);

            device = new EdgeDevice(2, 0.001, 2_174_483_648L, 0, 0,
                    0.02,  0.25, 2.2, 12, 3,
                    0, 100 * 60 * 60 * 1000, 100 , 60 * 1000,
                    new RandomWalkMobilityStrategy(new GeoLocation(47 + step, 19 - step), 0.0027, 0.0055, 10_000),
                    new RandomDeviceStrategy(), 0.1, 50, battery, true);
            device.setBattery(battery);
            deviceList.add(device);
        }

        long starttime = System.nanoTime();
        Timed.simulateUntilLastEvent();
        long stoptime = System.nanoTime();

//        ScenarioBase.calculateIoTCost();
//        ScenarioBase.logBatchProcessing(stoptime - starttime);
//        TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
//        MapVisualiser.mapGenerator(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, deviceList);
        EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);

        for (Device device : deviceList) {
            device.battery.writeToFileConsumption(ScenarioBase.resultDirectory);
            //device.battery.writeToFilePercentage(ScenarioBase.resultDirectory);
        }

        AgentVisualiser.visualise("batteryGraph",
                java.util.stream.IntStream.range(0, 6)
                        .mapToObj(i -> Path.of(ScenarioBase.resultDirectory + File.separator + "battery" + i + ".csv"))
                        .toArray(Path[]::new)
        );
    }
}