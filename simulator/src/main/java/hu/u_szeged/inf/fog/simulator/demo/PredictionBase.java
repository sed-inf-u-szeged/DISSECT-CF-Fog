package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
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
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class PredictionBase implements PredictionConfigurator.ISimulation {
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

        String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";
        String fogfile1 = ScenarioBase.resourcePath + "/XML_examples/LPDS_32.xml";
        String fogfile2 = ScenarioBase.resourcePath + "/XML_examples/LPDS_16.xml";

        VirtualAppliance va = new VirtualAppliance("va", 100, 0, false, 1073741824L);

        AlterableResourceConstraints arc1 = new AlterableResourceConstraints(2, 0.001, 4294967296L);
        AlterableResourceConstraints arc2 = new AlterableResourceConstraints(4, 0.001, 8589934592L);
        AlterableResourceConstraints arc3 = new AlterableResourceConstraints(8, 0.001, 17179869184L);

        // https://aws.amazon.com/ec2/pricing/on-demand/
        Instance instance_type1 = new Instance("a1.large", va, arc1, 0.051 / 60 / 60 / 1000);
        Instance instance_type2 = new Instance("a1.xlarge", va, arc2, 0.102 / 60 / 60 / 1000);
        Instance instance_type3 = new Instance("a1.2xlarge", va, arc3, 0.204 / 60 / 60 / 1000);

        ComputingAppliance Budapest_cloud = new ComputingAppliance(cloudfile, "Budapest_cloud", new GeoLocation(47.5, 19.1), 10000);

        ComputingAppliance Sopron_fog2 = new ComputingAppliance(fogfile2, "Sopron_fog2", new GeoLocation(47.7, 16.6), 10000);
        ComputingAppliance Szombathely_fog2 = new ComputingAppliance(fogfile2, "Szombathely_fog2", new GeoLocation(47.2, 16.6), 10000);
        ComputingAppliance Gyor_fog1 = new ComputingAppliance(fogfile1, "Gyor_fog1", new GeoLocation(47.6, 17.6), 10000);

        ComputingAppliance Szeged_fog2 = new ComputingAppliance(fogfile2, "Szeged_fog2", new GeoLocation(46.2, 20.1), 10000);
        ComputingAppliance Pecs_fog2 = new ComputingAppliance(fogfile2, "Pecs_fog2", new GeoLocation(46.0, 18.2), 10000);
        ComputingAppliance Kecskemet_fog1 = new ComputingAppliance(fogfile1, "Kecskemet_fog1", new GeoLocation(46.8, 19.7), 10000);

        ComputingAppliance Miskolc_fog2 = new ComputingAppliance(fogfile2, "Miskolc_fog2", new GeoLocation(48.1, 20.7), 10000);
        ComputingAppliance Debrecen_fog2 = new ComputingAppliance(fogfile2, "Debrecen_fog2", new GeoLocation(47.5, 21.6), 10000);
        ComputingAppliance Gyongyos_fog1 = new ComputingAppliance(fogfile1, "Gyongyos_fog1", new GeoLocation(47.7, 19.9), 10000);

        Gyor_fog1.addNeighbor(Gyongyos_fog1, 33);
        Gyor_fog1.addNeighbor(Kecskemet_fog1, 35);
        Kecskemet_fog1.addNeighbor(Gyongyos_fog1, 34);

        Kecskemet_fog1.setParent(Budapest_cloud, 25);
        Gyongyos_fog1.setParent(Budapest_cloud, 25);
        Gyor_fog1.setParent(Budapest_cloud, 27);

        Sopron_fog2.addNeighbor(Szombathely_fog2, 45);
        Sopron_fog2.setParent(Gyor_fog1, 43);
        Szombathely_fog2.setParent(Gyor_fog1, 44);

        Szeged_fog2.addNeighbor(Pecs_fog2, 44);
        Szeged_fog2.setParent(Kecskemet_fog1, 49);
        Pecs_fog2.setParent(Kecskemet_fog1, 47);

        Miskolc_fog2.addNeighbor(Debrecen_fog2, 41);
        Miskolc_fog2.setParent(Gyongyos_fog1, 44);
        Debrecen_fog2.setParent(Gyongyos_fog1, 48);

        Application Budapest_cloud_app = new Application("Budapest_cloud_app", 1 * 60 * 1000, tasksize, instructions, false, generateAppStrategy(applicationStrategy, activationRatio, transferDivider), instance_type3);
        Application Sopron_fog2_app = new Application("Sopron_fog2_app", 1 * 60 * 1000, tasksize, instructions, true, generateAppStrategy(applicationStrategy, activationRatio, transferDivider), instance_type1);
        Application Szombathely_fog2_app = new Application("Szombathely_fog2_app", 1 * 60 * 1000, tasksize, instructions, true, generateAppStrategy(applicationStrategy, activationRatio, transferDivider), instance_type1);
        Application Gyor_fog1_app = new Application("Gyor_fog1_app", 1 * 60 * 1000, tasksize, instructions, true, generateAppStrategy(applicationStrategy, activationRatio, transferDivider), instance_type2);
        Application Szeged_fog2_app = new Application("Szeged_fog2_app", 1 * 60 * 1000, tasksize, instructions, true, generateAppStrategy(applicationStrategy, activationRatio, transferDivider), instance_type1);
        Application Pecs_fog2_app = new Application("Pecs_fog2_app", 1 * 60 * 1000, tasksize, instructions, true, generateAppStrategy(applicationStrategy, activationRatio, transferDivider), instance_type1);
        Application Kecskemet_fog1_app = new Application("Kecskemet_fog1_app", 1 * 60 * 1000, tasksize, instructions, true, generateAppStrategy(applicationStrategy, activationRatio, transferDivider), instance_type2);
        Application Miskolc_fog2_app = new Application("Miskolc_fog2_app", 1 * 60 * 1000, tasksize, instructions, true, generateAppStrategy(applicationStrategy, activationRatio, transferDivider), instance_type1);
        Application Debrecen_fog2_app = new Application("Debrecen_fog2_app", 1 * 60 * 1000, tasksize, instructions, true, generateAppStrategy(applicationStrategy, activationRatio, transferDivider), instance_type1);
        Application Gyongyos_fog1_app = new Application("Gyongyos_fog1_app", 1 * 60 * 1000, tasksize, instructions, true, generateAppStrategy(applicationStrategy, activationRatio, transferDivider), instance_type2);

        Budapest_cloud.addApplication(Budapest_cloud_app);
        Sopron_fog2.addApplication(Sopron_fog2_app);
        Szombathely_fog2.addApplication(Szombathely_fog2_app);
        Gyor_fog1.addApplication(Gyor_fog1_app);
        Szeged_fog2.addApplication(Szeged_fog2_app);
        Pecs_fog2.addApplication(Pecs_fog2_app);
        Kecskemet_fog1.addApplication(Kecskemet_fog1_app);
        Miskolc_fog2.addApplication(Miskolc_fog2_app);
        Debrecen_fog2.addApplication(Debrecen_fog2_app);
        Gyongyos_fog1.addApplication(Gyongyos_fog1_app);

        generateDevices(device_multiplier);

        for (ComputingAppliance computingAppliance: ComputingAppliance.getAllComputingAppliances()) {
            FeatureManager.getInstance().addFeature(new Feature(String.format("%s::%s", computingAppliance.name, "notYetProcessedData")) {
                @Override
                public double compute() {
                    int result = 0;
                    for (Application application: computingAppliance.applications) {
                        result += (application.receivedData - application.processedData);
                    }
                    return result;
                }
            });
        }

        long starttime = System.nanoTime();
        Timed.simulateUntilLastEvent();
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
        // 4-5 hours: 2
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

            new SmartDevice(4 * 60 * 60 * 1000, 5 * 60 * 60 * 1000, 50, 1 * 60 * 1000, 
                    new StaticMobilityStrategy(new GeoLocation(pos[0], pos[1])),
                    new LoadBalancedDeviceStrategy(), localMachine, 50, false);
        }
    }
}