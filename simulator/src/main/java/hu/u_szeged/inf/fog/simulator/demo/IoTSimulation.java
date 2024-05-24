package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.application.strategy.PliantApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.SmartDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.NomadicMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.PliantDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.EnergyChartVisualiser;
import hu.u_szeged.inf.fog.simulator.util.MapVisualiser;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser;

import java.util.*;

public class IoTSimulation {

    public static void main(String[] args) throws Exception {
        SimLogger.setLogging(22, true);

        String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";

        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1073741824L);
        AlterableResourceConstraints arc = new AlterableResourceConstraints(2, 0.001, 4294967296L);

        Instance appInstance1 = new Instance("instance1", va, arc, 0.051 / 60 / 60 / 1000);
        Instance appInstance2 = new Instance("instance2", va, arc, 0.102 / 60 / 60 / 1000);
        Instance appInstance3 = new Instance("instance3", va, arc, 0.0255 / 60 / 60 / 1000);

        // ComputingAppliance cloud1 = new ComputingAppliance(cloudfile, "cloud1", new
        // GeoLocation(47, 19), 1);

        ComputingAppliance fog1 = new ComputingAppliance(cloudfile, "fog1", new GeoLocation(47, 20), 1);

        // fog1.setParent(cloud1, 77);
        ComputingAppliance fog2 = new ComputingAppliance(cloudfile, "fog2", new GeoLocation(47, 20.10), 1);

        ComputingAppliance fog3 = new ComputingAppliance(cloudfile, "fog3", new GeoLocation(47.10, 20.10), 1);

        // ComputingAppliance fog4 = new ComputingAppliance(cloudfile, "fog4", new
        // GeoLocation(48, 20), 1000);

        // fog1.addNeighbor(fog2, 33);
        // fog2.addNeighbor(fog3, 43);
        // fog3.addNeighbor(fog4, 53);
        // fog4.addNeighbor(fog1, 63);

        // fog2.setParent(cloud1, 67);
        // fog3.setParent(cloud1, 57);
        // fog4.setParent(cloud1, 47);

        Application application1 = new Application("App-1", 1 * 60 * 1000, 250, 2500, true,
                new PliantApplicationStrategy(0.9, 2.0), appInstance1);
        Application application2 = new Application("App-2", 1 * 60 * 1000, 250, 2500, true,
                new PliantApplicationStrategy(0.9, 2.0), appInstance2);
        Application application3 = new Application("App-3", 1 * 60 * 1000, 250, 2500, true,
                new PliantApplicationStrategy(0.9, 2.0), appInstance3);


        /*
         * Application application2 = new Application("App-2", 1 * 60 * 60 * 1000, 2500,
         * 2500, true, null, appInstance); Application application3 = new
         * Application("App-3", 1 * 60 * 60 * 1000, 2500, 2500, true, null,
         * appInstance); Application application4 = new Application("App-4", 1 * 60 * 60
         * * 1000, 2500, 2500, true, null, appInstance); Application application5 = new
         * Application("App-5", 1 * 60 * 60 * 1000, 2500, 2500, true, null,
         * appInstance);
         */

        fog1.addApplication(application1);
        fog2.addApplication(application2);
        fog3.addApplication(application3);

        /*
         * fog1.addApplication(application2); fog2.addApplication(application3);
         * fog3.addApplication(application4); fog4.addApplication(application5);
         */
        ArrayList<GeoLocation> geoList = new ArrayList<GeoLocation>();

        geoList.add(new GeoLocation(47, 20));
        geoList.add(new GeoLocation(47, 20.10));
        geoList.add(new GeoLocation(47.10, 20.10));

        Device smartDevice2 = null;
        for (int i = 0; i < 10; i++) {
            HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();
            EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = null;

            try {
                transitions = PowerTransitionGenerator.generateTransitions(1, 2, 3, 4, 5);
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Map<String, PowerState> cpuTransitions = transitions
                    .get(PowerTransitionGenerator.PowerStateKind.host);
            final Map<String, PowerState> stTransitions = transitions
                    .get(PowerTransitionGenerator.PowerStateKind.storage);
            final Map<String, PowerState> nwTransitions = transitions
                    .get(PowerTransitionGenerator.PowerStateKind.network);

            Repository repo = new Repository(4294967296L, "mc-repo", 3250, 3250, 3250, latencyMap, stTransitions,
                    nwTransitions); // 26 Mbit/s
            PhysicalMachine localMachine = new PhysicalMachine(8, 0.001, 4294967296L, repo, 0, 0, cpuTransitions);

            // SmartDevice smartDevice1 = new SmartDevice(0, 1 * 60 * 60 * 1000, 100, 1, 60
            // * 1000, new StaticMobilityStrategy(new GeoLocation(47.15, 18.95)),new
            // CostAwareDeviceStrategy(), localMachine, 50, true);

            // SmartDevice smartDevice2 = new SmartDevice(4 * 60 * 60 * 1000, 5 * 60 * 60 *
            // 1000, 100, 1, 60 * 1000, new StaticMobilityStrategy(new GeoLocation(47.15,
            // 18.95)),new RandomDeviceStrategy(), localMachine, 50, true);

            // smartDevice2 = new EdgeDevice(0, 1 * 60 * 60 * 1000, 100, 1, 60 * 1000, new
            // RandomWalkMobilityStrategy(new GeoLocation(47, 19), 0.0027, 0.0055, 10000),
            // 2, new RandomDeviceStrategy(), localMachine, 50, true);
            //smartDevice2 = new EdgeDevice(0, 10 * 60 * 60 * 1000, 100, 1, 60 * 1000,
            //        new NomadicMobilityStrategy(new GeoLocation(47, 20), 0.00035, geoList), 2,
            //        new PliantDeviceStrategy(), localMachine, 1, 50, true);

             smartDevice2 = new SmartDevice(0, 10 * 60 * 60 * 1000, 100, 60 * 1000, new
             NomadicMobilityStrategy(new GeoLocation (47, 20), 0.00035, geoList), new
            PliantDeviceStrategy(), localMachine, 50, true);

        }

        long starttime = System.nanoTime();
        Timed.simulateUntilLastEvent();
        long stoptime = System.nanoTime();

        ScenarioBase.calculateIoTCost();
        ScenarioBase.logBatchProcessing(stoptime - starttime);
        TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
        EnergyChartVisualiser.generateApplicationEnergyChart(ScenarioBase.resultDirectory);
        EnergyChartVisualiser.generateDeviceEnergyChart(ScenarioBase.resultDirectory);
        MapVisualiser.mapGenerator(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, smartDevice2);
    }

}
