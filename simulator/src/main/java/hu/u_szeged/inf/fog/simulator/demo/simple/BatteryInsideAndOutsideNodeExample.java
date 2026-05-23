package hu.u_szeged.inf.fog.simulator.demo.simple;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.application.strategy.MCTCommApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.RuntimeAndTypeAwareApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.RuntimeAwareApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.iot.*;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DistanceAndTypeBasedDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.*;
import hu.u_szeged.inf.fog.simulator.common.util.EnergyDataCollector;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class BatteryInsideAndOutsideNodeExample {

    public static void main(String[] args) throws Exception {
        SimLogger.setLogging(1, true);

        String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";

        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1_073_741_824L);
        AlterableResourceConstraints arc = new AlterableResourceConstraints(2, 0.001, 4_294_967_296L);

        ComputingAppliance cloud1 = new ComputingAppliance(cloudfile, "cloud1", new GeoLocation(47.45, 21.3), 100);
        ComputingAppliance fog1 = new ComputingAppliance(cloudfile, "fog1", new GeoLocation(47.6, 17.9), 50);
        ComputingAppliance fog2 = new ComputingAppliance(cloudfile, "fog2", new GeoLocation(46.0, 18.2), 50);

        new EnergyDataCollector("cloud1", cloud1.iaas, true, true);
        new EnergyDataCollector("fog1", fog1.iaas, true, true);
        new EnergyDataCollector("fog2", fog2.iaas, true, true);

        fog1.setParent(cloud1, 77);
        fog2.setParent(cloud1, 80);

        fog1.addNeighbor(fog2, 33);

        Instance instance1 = new Instance("instance1", va, arc, 0.0255 / 60 / 60 / 1000);
        Instance instance2 = new Instance("instance2", va, arc, 0.051 / 60 / 60 / 1000);
        Instance instance3 = new Instance("instance3", va, arc, 0.102 / 60 / 60 / 1000);

        Application application1 = new Application("App-1-M", 60 * 1000, 5000, 50_000, true,
                new MCTCommApplicationStrategy(0.9, 2.0), instance3, TaskType.MEDICAL);
        Application application2 = new Application("App-2-M", 60 * 1000, 5000, 50_000, true,
                new MCTCommApplicationStrategy(0.9, 2.0), instance2, TaskType.TRAFFIC);
        Application application3 = new Application("App-3-M", 60 * 1000, 5000, 50_000, true,
                new MCTCommApplicationStrategy(0.9, 2.0), instance1, TaskType.WEATHER);

        cloud1.addApplication(application1);
        fog1.addApplication(application2);
        fog2.addApplication(application3);

        ArrayList<Device> deviceList = new ArrayList<Device>();
        for (int i = 0; i < 10; i++) {
            Device device;
            Battery battery = new Battery("battery"+i, Battery.BatteryType.PHONE_BATTERY);

            //Magyarország szélességi és hosszúsági koordinátái körülbelül
            double minLat = 45.74;
            double maxLat = 48.58;
            double minLon = 16.11;
            double maxLon = 22.90;

            double lat = minLat + (maxLat - minLat) * SeedSyncer.centralRnd.nextDouble();
            double lon = minLon + (maxLon - minLon) * SeedSyncer.centralRnd.nextDouble();

            if (i % 3 == 0) {
                device = new EdgeDevice(2, 0.001, 2_174_483_648L, 0, 0,
                        0.02,  0.25, 2.2, 12, 3,
                        0, 48 * 60 * 60 * 1000, 1000 , 60 * 1000,
                        new RandomWalkMobilityStrategy(new GeoLocation(lat, lon), 0.0027, 0.0055, 10_000),
                        new DistanceAndTypeBasedDeviceStrategy(), 0.1, 50, battery, TaskType.MEDICAL, true);
            } else if (i % 3 == 1) {
                device = new EdgeDevice(2, 0.001, 2_174_483_648L, 0, 0,
                        0.02,  0.25, 2.2, 12, 3,
                        0, 48 * 60 * 60 * 1000, 1000 , 60 * 1000,
                        new RandomWalkMobilityStrategy(new GeoLocation(lat, lon), 0.0027, 0.0055, 10_000),
                        new DistanceAndTypeBasedDeviceStrategy(), 0.1, 50, battery, TaskType.TRAFFIC, true);
            } else{
                device = new EdgeDevice(2, 0.001, 2_174_483_648L, 0, 0,
                        0.02,  0.25, 2.2, 12, 3,
                        0, 48 * 60 * 60 * 1000, 1000 , 60 * 1000,
                        new RandomWalkMobilityStrategy(new GeoLocation(lat, lon), 0.0027, 0.0055, 10_000),
                        new DistanceAndTypeBasedDeviceStrategy(), 0.1, 50, battery, TaskType.WEATHER, true);
            }
            deviceList.add(device);
        }

        long starttime = System.nanoTime();
        Timed.simulateUntilLastEvent();
        long stoptime = System.nanoTime();


        ScenarioBase.calculateIoTCost();
        ScenarioBase.logBatchProcessing(stoptime - starttime);


        TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
        MapVisualiser.mapGenerator(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, deviceList);
        EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory); //energy.csv

        for (Device device : deviceList) {
            device.battery.writeToFileConsumption(ScenarioBase.resultDirectory);
        }

        AgentVisualiser.visualise("batteryGraph",
                java.util.stream.IntStream.range(0, 10)
                        .mapToObj(i -> Path.of(ScenarioBase.resultDirectory + File.separator + "battery" + i + ".csv"))
                        .toArray(Path[]::new)
        );
    }
}