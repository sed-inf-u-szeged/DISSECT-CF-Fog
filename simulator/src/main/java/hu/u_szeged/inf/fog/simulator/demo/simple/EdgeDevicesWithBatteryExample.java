package hu.u_szeged.inf.fog.simulator.demo.simple;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.application.strategy.RuntimeAndTypeAwareApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.application.strategy.RuntimeAwareApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.iot.*;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DistanceAndTypeBasedDeviceStrategy;
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

        //minden mehetne for ciklusba majdnem, de egyelőre marad így
        ComputingAppliance cloud1 = new ComputingAppliance(cloudfile, "cloud1", new GeoLocation(47.53, 21.64), 100); //DEBRECEN
        ComputingAppliance fog1 = new ComputingAppliance(cloudfile, "fog1", new GeoLocation(46.845, 16.847), 50); //ZALAEGERSZEG
        ComputingAppliance fog2 = new ComputingAppliance(cloudfile, "fog2", new GeoLocation(47.4979, 19.04), 50); //BUDAPEST

        ComputingAppliance cloud2 = new ComputingAppliance(cloudfile, "cloud2", new GeoLocation(47.683, 17.635), 100); //GYŐR
        ComputingAppliance fog3 = new ComputingAppliance(cloudfile, "fog3", new GeoLocation(47.9554, 21.7167), 70); //NYÍREGYHÁZA
        ComputingAppliance fog4 = new ComputingAppliance(cloudfile, "fog4", new GeoLocation(46.215, 20.475), 70); //MAKÓ

        ComputingAppliance cloud3 = new ComputingAppliance(cloudfile, "cloud3", new GeoLocation(46.255, 20.145), 100); //SZEGED
        ComputingAppliance fog5 = new ComputingAppliance(cloudfile, "fog5", new GeoLocation(47.4979, 19.04), 80); //BUDAPEST
        ComputingAppliance fog6 = new ComputingAppliance(cloudfile, "fog6", new GeoLocation(47.53, 21.64), 80); // DEBRECEN



        new EnergyDataCollector("cloud1", cloud1.iaas, false, true);
        new EnergyDataCollector("fog1", fog1.iaas, false, true);
        new EnergyDataCollector("fog2", fog2.iaas, false, true);
        new EnergyDataCollector("cloud2", cloud2.iaas, false, true);
        new EnergyDataCollector("fog3", fog3.iaas, false, true);
        new EnergyDataCollector("fog4", fog4.iaas, false, true);
        new EnergyDataCollector("cloud3", cloud3.iaas, false, true);
        new EnergyDataCollector("fog5", fog5.iaas, false, true);
        new EnergyDataCollector("fog6", fog6.iaas, false, true);

        fog1.setParent(cloud1, 77);
        fog2.setParent(cloud1, 80);
        fog1.addNeighbor(fog2, 33);

        fog3.setParent(cloud2, 75);
        fog4.setParent(cloud2, 70);
        fog3.addNeighbor(fog4, 55);

        fog5.setParent(cloud3, 78);
        fog6.setParent(cloud3, 82);
        fog5.addNeighbor(fog6, 90);
        

        Instance instance1 = new Instance("instance1", va, arc, 0.0255 / 60 / 60 / 1000);
        Instance instance2 = new Instance("instance2", va, arc, 0.051 / 60 / 60 / 1000);
        Instance instance3 = new Instance("instance3", va, arc, 0.102 / 60 / 60 / 1000);
        Instance instance4 = new Instance("instance4", va, arc, 0.0255 / 60 / 60 / 1000);
        Instance instance5 = new Instance("instance5", va, arc, 0.051 / 60 / 60 / 1000);
        Instance instance6 = new Instance("instance6", va, arc, 0.102 / 60 / 60 / 1000);
        Instance instance7 = new Instance("instance7", va, arc, 0.0255 / 60 / 60 / 1000);
        Instance instance8 = new Instance("instance8", va, arc, 0.051 / 60 / 60 / 1000);
        Instance instance9 = new Instance("instance9", va, arc, 0.102 / 60 / 60 / 1000);

        Application application1 = new Application("App-1-M", 60 * 1000, 300000, 3_000_000, true,
                new RuntimeAndTypeAwareApplicationStrategy(0.9, 2.0), instance3, TaskType.MEDICAL);
        Application application2 = new Application("App-2-M", 60 * 1000, 300000, 3_000_000, true,
                new RuntimeAndTypeAwareApplicationStrategy(0.9, 2.0), instance2, TaskType.MEDICAL);
        Application application3 = new Application("App-3-M", 60 * 1000, 300000, 3_000_000, true,
                new RuntimeAndTypeAwareApplicationStrategy(0.9, 2.0), instance1, TaskType.MEDICAL);
        Application application4 = new Application("App-4-T", 60 * 1000, 300000, 3_000_000, false,
                new RuntimeAndTypeAwareApplicationStrategy(0.9, 2.0), instance6, TaskType.TRAFFIC);
        Application application5 = new Application("App-5-T", 60 * 1000, 300000, 3_000_000, true,
                new RuntimeAndTypeAwareApplicationStrategy(0.9, 2.0), instance5, TaskType.TRAFFIC);
        Application application6 = new Application("App-6-T", 60 * 1000, 300000, 3_000_000, true,
                new RuntimeAndTypeAwareApplicationStrategy(0.9, 2.0), instance4, TaskType.TRAFFIC);
        Application application7 = new Application("App-7-W", 60 * 1000, 300000, 3_000_000, false,
                new RuntimeAndTypeAwareApplicationStrategy(0.9, 2.0), instance9, TaskType.WEATHER);
        Application application8 = new Application("App-8-W", 60 * 1000, 300000, 3_000_000, true,
                new RuntimeAndTypeAwareApplicationStrategy(0.9, 2.0), instance8, TaskType.WEATHER);
        Application application9 = new Application("App-9-W", 60 * 1000, 300000, 3_000_000, true,
                new RuntimeAndTypeAwareApplicationStrategy(0.9, 2.0), instance7, TaskType.WEATHER);

        cloud1.addApplication(application1);
        fog1.addApplication(application2);
        fog2.addApplication(application3);

        cloud2.addApplication(application4);
        fog3.addApplication(application5);
        fog4.addApplication(application6);

        cloud3.addApplication(application7);
        fog5.addApplication(application8);
        fog6.addApplication(application9);


        ArrayList<Device> deviceList = new ArrayList<Device>();
        for (int i = 0; i < 100; i++) {
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

//        ScenarioBase.calculateIoTCost();
//        ScenarioBase.logBatchProcessing(stoptime - starttime);
//        TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
//        MapVisualiser.mapGenerator(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, deviceList);
        EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);

    /*
        for (Device device : deviceList) {
            device.battery.writeToFileConsumption(ScenarioBase.resultDirectory);
            //device.battery.writeToFilePercentage(ScenarioBase.resultDirectory);
        }


        AgentVisualiser.visualise("batteryGraph",
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(i -> Path.of(ScenarioBase.resultDirectory + File.separator + "battery" + i + ".csv"))
                        .toArray(Path[]::new)
        );
    */
    }
}