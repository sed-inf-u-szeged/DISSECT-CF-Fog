package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.application.strategy.*;
import hu.u_szeged.inf.fog.simulator.iot.SmartDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.StaticMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.LoadBalancedDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.prediction.Feature;
import hu.u_szeged.inf.fog.simulator.prediction.FeatureManager;
import hu.u_szeged.inf.fog.simulator.prediction.OnlineModelLifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class PredictionSimulationDefinition implements PredictionConfigurator.SimulationDefinition {
    @Override
    public void simulation() throws Exception {

        /** SETTINGS **/
        int device_multiplier = 30;
        long tasksize = 5120;
        double instructions = 480; // 1 minute on a1.2xlarge
        String applicationStrategy = "Pliant"; // PushUp HoldDown Random RuntimeAware
        double activationRatio = 0.9;
        double transferDivider = 2.0;

        SimLogger.setLogging(1, true);

        for (ComputingAppliance node : CityTopology.create(
                java.nio.file.Path.of(ScenarioBase.resourcePath, "LPDS_original.xml"),
                java.nio.file.Path.of(ScenarioBase.resourcePath), 10000, tasksize, instructions,
                () -> generateAppStrategy(applicationStrategy, activationRatio, transferDivider))) {
            new EnergyDataCollector(node.name, node.iaas, true);
        }

        generateDevices(device_multiplier);

        for (ComputingAppliance computingAppliance: ComputingAppliance.getAllComputingAppliances()) {
            FeatureManager.getInstance().addFeature(new Feature(String.format("%s::%s", computingAppliance.name, "notYetProcessedData")) {
                @Override
                public double compute() {
                    double result = 0.0;
                    for (Application application: computingAppliance.applications) {
                        result += (application.receivedData - application.processedData);
                    }
                    return result;
                }
            });
        }

        long starttime = System.nanoTime();

        long lifecycleCheckInterval = 60 * 1000L;

        // Check data readiness from the beginning and monitor until events end.
        while (Timed.getNextFire() >= 0) {
            long nextCheckTime = Timed.getFireCount() + lifecycleCheckInterval;
            Timed.simulateUntil(nextCheckTime);

            // Do not launch another training job after the final simulation event.
            if (Timed.getNextFire() >= 0) {
                OnlineModelLifecycleManager.getInstance().checkLifecycle();
            }
        }
        long stoptime = System.nanoTime();

        ScenarioBase.calculateIoTCost();
        ScenarioBase.logBatchProcessing(stoptime - starttime);
        TimelineVisualiser.generateTimeline(ScenarioBase.resultDirectory);
        // MapVisualiser.mapGenerator(ScenarioBase.scriptPath, ScenarioBase.resultDirectory, Device.allDevices);
    }

    private static ApplicationStrategy generateAppStrategy(String strategy, double activationRatio, double transferDivider) {
        if(strategy.equals("Pliant")) {
            return new PliantApplicationStrategy(activationRatio, transferDivider);
        }else if(strategy.equals("PushUp")) {
            return new PushUpApplicationStrategy(activationRatio, transferDivider);
        }else if(strategy.equals("HoldDown")) {
            return new HoldDownApplicationStrategy(activationRatio, transferDivider);
        }else if(strategy.equals("Random")) {
            return new RandomApplicationStrategy(activationRatio, transferDivider);
        }else if(strategy.equals("RuntimeAware")) {
            return new RuntimeAwareApplicationStrategy(activationRatio, transferDivider);
        }
        return null;
    }

    private static double[] generatePosition() {
        // double maxLatitude = 48.3; diff: 2.5
        // double maxLongitude = 22.3; diff: 5.9
        double minLatitude = 45.8;
        double minLongitude = 16.4;

        double returnLat = minLatitude + ((double) SeedSyncer.centralRnd.nextInt(26) / 10);
        double returnLong = minLongitude +  ((double) SeedSyncer.centralRnd.nextInt(60) / 10);

        double[] pos = {returnLat, returnLong};
        return pos;
    }

    private static void generateDevices(int multiplier) {
        // 0-1 hours: 2
        for(int i = 0; i<2*multiplier; i++) {
            HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();
            EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = null;

            try {
                transitions = PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Map<String, PowerState> cpuTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
            final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
            final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);

            Repository repo = new Repository(4294967296L, "device-" + i, 3250, 3250, 3250, latencyMap, stTransitions, nwTransitions); // 26 Mbit/s
            PhysicalMachine localMachine = new PhysicalMachine(1, 0.001, 1073741824L, repo, 0, 0, cpuTransitions);

            double[] pos = generatePosition();

            new SmartDevice(0, 1 * 60 * 60 * 1000, 50, 1 * 60 * 1000, new StaticMobilityStrategy(new GeoLocation(pos[0], pos[1])),
                    new LoadBalancedDeviceStrategy(), localMachine, 50, false);

        }
        // 1-2 hours: 6
        for(int i = 0; i<6*multiplier; i++) {
            HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();
            EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = null;

            try {
                transitions = PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Map<String, PowerState> cpuTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
            final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
            final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);

            Repository repo = new Repository(4294967296L, "device-" + i, 3250, 3250, 3250, latencyMap, stTransitions, nwTransitions); // 26 Mbit/s
            PhysicalMachine localMachine = new PhysicalMachine(1, 0.001, 1073741824L, repo, 0, 0, cpuTransitions);

            double[] pos = generatePosition();

            new SmartDevice(1 * 60 * 60 *1000, 2 * 60 * 60 * 1000, 50, 1 * 60 * 1000, new StaticMobilityStrategy(new GeoLocation(pos[0], pos[1])),
                    new LoadBalancedDeviceStrategy(), localMachine, 50, false);

        }
        // 2-3 hours: 3
        for(int i = 0; i<3*multiplier; i++) {
            HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();
            EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = null;

            try {
                transitions = PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Map<String, PowerState> cpuTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
            final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
            final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);

            Repository repo = new Repository(4294967296L, "device-" + i, 3250, 3250, 3250, latencyMap, stTransitions, nwTransitions); // 26 Mbit/s
            PhysicalMachine localMachine = new PhysicalMachine(1, 0.001, 1073741824L, repo, 0, 0, cpuTransitions);

            double[] pos = generatePosition();

            new SmartDevice(2 * 60 * 60 *1000, 3 * 60 * 60 * 1000, 50, 1 * 60 * 1000, new StaticMobilityStrategy(new GeoLocation(pos[0], pos[1])),
                    new LoadBalancedDeviceStrategy(), localMachine, 50, false);

        }
        // 3-4 hours: 7
        for(int i = 0; i<7*multiplier; i++) {
            HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();
            EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = null;

            try {
                transitions = PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Map<String, PowerState> cpuTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
            final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
            final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);

            Repository repo = new Repository(4294967296L, "device-" + i, 3250, 3250, 3250, latencyMap, stTransitions, nwTransitions); // 26 Mbit/s
            PhysicalMachine localMachine = new PhysicalMachine(1, 0.001, 1073741824L, repo, 0, 0, cpuTransitions);

            double[] pos = generatePosition();

            new SmartDevice(3 * 60 * 60 *1000, 4 * 60 * 60 * 1000, 50, 1 * 60 * 1000, new StaticMobilityStrategy(new GeoLocation(pos[0], pos[1])),
                    new LoadBalancedDeviceStrategy(), localMachine, 50, false);

        }
        // 4-10 hours: 2
        for(int i = 0; i<2*multiplier; i++) {
            HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();
            EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = null;

            try {
                transitions = PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Map<String, PowerState> cpuTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
            final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
            final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);

            Repository repo = new Repository(4294967296L, "device-" + i, 3250, 3250, 3250, latencyMap, stTransitions, nwTransitions); // 26 Mbit/s
            PhysicalMachine localMachine = new PhysicalMachine(1, 0.001, 1073741824L, repo, 0, 0, cpuTransitions);

            double[] pos = generatePosition();

            new SmartDevice(4 * 60 * 60 * 1000, 10 * 60 * 60 * 1000, 50, 1 * 60 * 1000,
                    new StaticMobilityStrategy(new GeoLocation(pos[0], pos[1])),
                    new LoadBalancedDeviceStrategy(), localMachine, 50, false);
        }

        // 6-10 hours: 5 additional devices per multiplier for serving predictions
        for(int i = 0; i<5*multiplier; i++) {
            HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();
            EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = null;

            try {
                transitions = PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Map<String, PowerState> cpuTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
            final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
            final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);

            Repository repo = new Repository(4294967296L, "device-serving-" + i, 3250, 3250, 3250, latencyMap, stTransitions, nwTransitions); // 26 Mbit/s
            PhysicalMachine localMachine = new PhysicalMachine(1, 0.001, 1073741824L, repo, 0, 0, cpuTransitions);

            double[] pos = generatePosition();

            new SmartDevice(6 * 60 * 60 * 1000, 10 * 60 * 60 * 1000, 50, 1 * 60 * 1000,
                    new StaticMobilityStrategy(new GeoLocation(pos[0], pos[1])),
                    new LoadBalancedDeviceStrategy(), localMachine, 50, false);
        }
    }
}