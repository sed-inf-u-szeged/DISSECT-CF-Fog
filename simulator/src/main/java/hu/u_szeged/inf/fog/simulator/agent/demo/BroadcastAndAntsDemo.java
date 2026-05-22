package hu.u_szeged.inf.fog.simulator.agent.demo;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.agent.*;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.application.dummy.DummyServer;
import hu.u_szeged.inf.fog.simulator.agent.decision.*;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.FirstFitMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BroadcastAndAntsDemo {
    
    public static void main(String[] args) throws IOException {

        SimLogger.setLogging(1, true);
        
        Map<String, Integer> sharedLatencyMap = new HashMap<>();
        
        /* image service config */
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(1, 1, 1, 1, 1);
        Deployment.setImageRegistry(new Repository(Long.MAX_VALUE, "Image-service", 125_000, 125_000, 125_000, sharedLatencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network)));
        
        /* node config */
        final ComputingAppliance node1 = new ComputingAppliance(
                Config.createNode("Node1", 26, 26 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        35, 200, 535, 100_000, 70, sharedLatencyMap),
                new GeoLocation(51.5074, -0.1278), "EU", "Azure", false); // London

        final ComputingAppliance node2 = new ComputingAppliance(
                Config.createNode("Node2", 28, 28 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        30, 296, 493, 37_500, 30, sharedLatencyMap),
                new GeoLocation(48.8566, 2.3522), "EU", "AWS", false); // Paris

        final ComputingAppliance node3 = new ComputingAppliance(
                Config.createNode("Node3", 18, 18 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        40, 398, 533, 150_000, 60, sharedLatencyMap),
                new GeoLocation(52.5200, 13.4050), "Asia", "Azure", false); // Berlin

        final ComputingAppliance node4 = new ComputingAppliance(
                Config.createNode("Node4", 20, 20 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        30, 150, 535, 37_500, 70, sharedLatencyMap),
                new GeoLocation(41.8781, -87.6298), "US", "AWS", false); // Chicago

        final ComputingAppliance node5 = new ComputingAppliance(
                Config.createNode("Node5", 18, 18 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        40, 200, 506, 150_000, 60, sharedLatencyMap),
                new GeoLocation(29.7604, -95.3698), "Asia", "Azure", false); // Houston

        final ComputingAppliance node6 = new ComputingAppliance(
                Config.createNode("Node6", 12, 12 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        35, 250, 545, 37_500, 25, sharedLatencyMap),
                new GeoLocation(37.34, -121.89), "US", "AWS", false); // Los Angeles

        final ComputingAppliance node7 = new ComputingAppliance(
                Config.createNode("Node7", 18, 18 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        28, 180, 480, 200_000, 25, sharedLatencyMap),
                new GeoLocation(35.6895, 139.6917), "Asia", "Azure", false); // Tokyo

        final ComputingAppliance node8 = new ComputingAppliance(
                Config.createNode("Node8", 20, 20 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        30, 170, 470, 180_000, 20, sharedLatencyMap),
                new GeoLocation(1.3521, 103.8198), "US", "AWS", false); // Singapore

        final ComputingAppliance node9 = new ComputingAppliance(
                Config.createNode("Node9", 22, 22 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        38, 260, 520, 80_000, 55, sharedLatencyMap),
                new GeoLocation(-33.8688, 151.2093), "EU", "Azure", false); // Sydney

        final ComputingAppliance node10 = new ComputingAppliance(
                Config.createNode("Node10", 24, 24 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        34, 220, 510, 90_000, 45, sharedLatencyMap),
                new GeoLocation(19.0760, 72.8777), "EU", "AWS", false); // Mumbai

        final ComputingAppliance node11 = new ComputingAppliance(
                Config.createNode("Node11", 24, 24 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        45, 320, 600, 30_000, 90, sharedLatencyMap),
                new GeoLocation(-23.5505, -46.6333), "Asia", "Azure", false); // São Paulo

        final ComputingAppliance node12 = new ComputingAppliance(
                Config.createNode("Node12", 12, 12 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        50, 350, 650, 20_000, 110, sharedLatencyMap),
                new GeoLocation(30.0444, 31.2357), "US", "AWS", false); // Cairo

        new EnergyDataCollector("Node1-energy", node1.iaas,true, true);
        new EnergyDataCollector("Node2-energy", node2.iaas,true,true);
        new EnergyDataCollector("Node3-energy", node3.iaas,true,true);
        new EnergyDataCollector("Node4-energy", node4.iaas,true, true);
        new EnergyDataCollector("Node5-energy", node5.iaas,true,true);
        new EnergyDataCollector("Node6-energy", node1.iaas,true, true);
        new EnergyDataCollector("Node7-energy", node2.iaas,true,true);
        new EnergyDataCollector("Node8-energy", node3.iaas,true,true);
        new EnergyDataCollector("Node9-energy", node4.iaas,true, true);
        new EnergyDataCollector("Node10-energy", node5.iaas,true,true);
        new EnergyDataCollector("Node11-energy", node1.iaas,true, true);
        new EnergyDataCollector("Node12-energy", node2.iaas,true,true);
        
        /* agent config */
        VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L);
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);

        StandardResourceAgent ra1 =
                new StandardResourceAgent("Agent1", 0.00013889, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra1.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node1, 26, 26 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        StandardResourceAgent ra2 =
                new StandardResourceAgent("Agent2", 0.00277778, new FirstFitMappingStrategy(false), new FloodingMessagingStrategy());
        ra2.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node2, 28, 28 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        StandardResourceAgent ra3 =
                new StandardResourceAgent("Agent3", 0.00041667, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra3.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node3, 18, 18 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        StandardResourceAgent ra4 =
                new StandardResourceAgent("Agent4", 0.00000278, new FirstFitMappingStrategy(false), new FloodingMessagingStrategy());
        ra4.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node4, 20, 20 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        StandardResourceAgent ra5 =
                new StandardResourceAgent("Agent5", 0.00027166, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra5.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node5, 18, 18 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        StandardResourceAgent ra6 =
                new StandardResourceAgent("Agent6", 0.00041667, new FirstFitMappingStrategy(false), new FloodingMessagingStrategy());
        ra6.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node6, 12, 12 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        StandardResourceAgent ra7 =
                new StandardResourceAgent("Agent7", 0.00008889, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra7.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node7, 18, 18 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        StandardResourceAgent ra8 =
                new StandardResourceAgent("Agent8", 0.00347222, new FirstFitMappingStrategy(false), new FloodingMessagingStrategy());
        ra8.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node8, 20, 20 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        StandardResourceAgent ra9 =
                new StandardResourceAgent("Agent9", 0.00111111, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra9.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node9, 22, 22 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        StandardResourceAgent ra10 =
                new StandardResourceAgent("Agent10", 0.00166667, new FirstFitMappingStrategy(false), new FloodingMessagingStrategy());
        ra10.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node10, 24, 24 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        StandardResourceAgent ra11 =
                new StandardResourceAgent("Agent11", 0.00013889, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra11.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node11, 24, 24 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        StandardResourceAgent ra12 =
                new StandardResourceAgent("Agent12", 0.00006944, new FirstFitMappingStrategy(false), new FloodingMessagingStrategy());
        ra12.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node12, 12, 12 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));
    
        /* app submission */
        List<Path> appDescriptionFiles = Files.list((Path) Config.DUMMY_CONFIGURATION.get("inputDir"))
                .filter(f -> f.toString().endsWith(".json"))
                .toList();

        // Group agents
        ArrayList<StandardResourceAgent> agentsToBeClustered = new ArrayList<>();

        agentsToBeClustered.add(ra1);
        agentsToBeClustered.add(ra2);
        agentsToBeClustered.add(ra3);
        agentsToBeClustered.add(ra4);
        agentsToBeClustered.add(ra5);
        agentsToBeClustered.add(ra6);
        agentsToBeClustered.add(ra7);
        agentsToBeClustered.add(ra8);
        agentsToBeClustered.add(ra9);
        agentsToBeClustered.add(ra10);
        agentsToBeClustered.add(ra11);
        agentsToBeClustered.add(ra12);


        int i = 0;
        for (Path file : appDescriptionFiles) {
            List<Integer> delays = (List<Integer>) Config.DUMMY_CONFIGURATION.get("submissionDelay");
            new DeferredEvent(delays.get(i) * ScenarioBase.MINUTE_IN_MILLISECONDS) {

                @Override
                protected void eventAction() {
                    new Submission(file, 2048, new BroadcastBasedDecisionMaker());
                    //new Submission(file, 2048, new CentralizedAntBasedDecisionMaker(4, agentsToBeClustered, 50, 100, 0.5, 0.2, 0.15, 0.3, 0.7));
                    //new Submission(file, 2048, new DecentralizedAntBasedDecisionMaker(agentsToBeClustered, 50, 100, 0.75, 0.15, 1 * 60 * 1000, 0.7));
                }
            };
            i++;
        }

        final long starttime = System.nanoTime();       
        Timed.simulateUntil((long) Config.DUMMY_CONFIGURATION.get("simLength"));
        final long stoptime = System.nanoTime();
        EnergyDataCollector.writeToFile(ScenarioBase.RESULT_DIRECTORY);
    
        /* results */
        SimLogger.logEmptyLine();
        for (StorageObject so : Deployment.registryService.contents()){
            SimLogger.logRes("\t(Registry) " + so);
        }

        SimLogger.logEmptyLine();
        for (ComputingAppliance ca : ComputingAppliance.allComputingAppliances.values()) {
            for (VirtualMachine vm : ca.iaas.listVMs()) {
                SimLogger.logRes("\t(" + ca.name + ") " + vm);
            }
            for (StorageObject so : ca.iaas.machines.get(0).localDisk.contents()){
                SimLogger.logRes("\t\t (PM content) " + so);
            }
            for (StorageObject so : ca.iaas.repositories.get(0).contents()) {
                SimLogger.logRes("\t\t (Repo content) " + so);
            }
        }

        SimLogger.logEmptyLine();
        for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
            for (Capacity cap : agent.capacities.values()) {
                SimLogger.logRes("\t(" + agent.name + ") " + cap);
                for (Utilisation util : cap.utilisations) {
                    SimLogger.logRes("\t\t" + util);
                }
            }
        }

        SimLogger.logEmptyLine();
        SimLogger.logRes("Simulation time (hour): " + TimeUnit.HOURS.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
        SimLogger.logRes("Size of generated files (MB): " + DummyServer.totalGeneratedFileSize / 1_048_576);
        SimLogger.logRes("Simulator's runtime (sec.): " + TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));
    }
}