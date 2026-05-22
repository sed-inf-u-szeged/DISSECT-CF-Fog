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
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.*;
import hu.u_szeged.inf.fog.simulator.agent.application.dummy.DummyServer;
import hu.u_szeged.inf.fog.simulator.agent.decision.AsyncCBBABasedDecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.decision.CBBABasedDecisionMaker;
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

public class CBBADemo {
    public static void main(String[] args) throws IOException {

        SimLogger.setLogging(1, true);

        SeedSyncer.modifySeed(3548); // => async (I think)

        Map<String, Integer> sharedLatencyMap = new HashMap<>();

        /* image service config */
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(1, 1, 1, 1, 1);
        Deployment.setImageRegistry(new Repository(Long.MAX_VALUE, "Image-service", 125_000, 125_000, 125_000, sharedLatencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network)));

        /* agents */
        VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L);
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);

        List<CBBAResourceAgent> agents = new ArrayList<>();

        // ===================== CLOUD NODES =====================
        // High-end cloud (jó)
        final ComputingAppliance node1 = new ComputingAppliance(
                Config.createNode("Node1",
                        128,
                        512 * ScenarioBase.GB_IN_BYTE,
                        4096 * ScenarioBase.GB_IN_BYTE,
                        40, 180, 3200,
                        180_000, 60,
                        sharedLatencyMap),
                new GeoLocation(50.1109, 8.6821),
                "EU",
                "AWS",
                false
        );

        CBBAResourceAgent ra1 =
                new CBBAResourceAgent("Agent1", 0.72,
                        new FirstFitMappingStrategy(true),
                        new FloodingMessagingStrategy());

        ra1.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node1,128,
                        512 * ScenarioBase.GB_IN_BYTE,
                        4096 * ScenarioBase.GB_IN_BYTE));

        agents.add(ra1);

        // Mid cloud (átlagos)
        final ComputingAppliance node2 = new ComputingAppliance(
                Config.createNode("Node2",
                        32,
                        128 * ScenarioBase.GB_IN_BYTE,
                        1024 * ScenarioBase.GB_IN_BYTE,
                        35,170,1800,
                        120_000,95,
                        sharedLatencyMap),
                new GeoLocation(37.7749,-122.4194),
                "US",
                "Azure",
                false
        );

        CBBAResourceAgent ra2 =
                new CBBAResourceAgent("Agent2",0.41,
                        new FirstFitMappingStrategy(true),
                        new FloodingMessagingStrategy());

        ra2.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node2,32,
                        128 * ScenarioBase.GB_IN_BYTE,
                        1024 * ScenarioBase.GB_IN_BYTE));

        agents.add(ra2);

        // ===================== EDGE NODES =====================
        // erős edge
        final ComputingAppliance node3 = new ComputingAppliance(
                Config.createNode("Node3",
                        16,
                        32 * ScenarioBase.GB_IN_BYTE,
                        512 * ScenarioBase.GB_IN_BYTE,
                        28,155,950,
                        140_000,55,
                        sharedLatencyMap),
                new GeoLocation(48.8566,2.3522),
                "EU",
                "EdgeX",
                false
        );

        CBBAResourceAgent ra3 = new CBBAResourceAgent(
                "Agent3",0.19,
                new FirstFitMappingStrategy(true),
                new FloodingMessagingStrategy());

        ra3.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node3,16,
                        32 * ScenarioBase.GB_IN_BYTE,
                        512 * ScenarioBase.GB_IN_BYTE));
        agents.add(ra3);

        // gyenge edge
        final ComputingAppliance node4 = new ComputingAppliance(
                Config.createNode("Node4",
                        4,
                        8 * ScenarioBase.GB_IN_BYTE,
                        64 * ScenarioBase.GB_IN_BYTE,
                        25,150,500,
                        40_000,180,
                        sharedLatencyMap),
                new GeoLocation(52.52,13.405),
                "Asia",
                "FogNode",
                false
        );

        CBBAResourceAgent ra4 = new CBBAResourceAgent(
                "Agent4",0.11,
                new FirstFitMappingStrategy(true),
                new FloodingMessagingStrategy());

        ra4.initResourceAgent(resourceAgentVa, resourceAgentArc,
                new Capacity(node4,4,
                        8 * ScenarioBase.GB_IN_BYTE,
                        64 * ScenarioBase.GB_IN_BYTE));
        agents.add(ra4);

        // átlagos edge
        final ComputingAppliance node5 = new ComputingAppliance(
                Config.createNode("Node5",
                        8,
                        16 * ScenarioBase.GB_IN_BYTE,
                        256 * ScenarioBase.GB_IN_BYTE,
                        30,165,750,
                        90_000,110,
                        sharedLatencyMap),
                new GeoLocation(35.6895,139.6917),
                "Asia",
                "HuaweiEdge",
                false
        );

        CBBAResourceAgent ra5 = new CBBAResourceAgent(
                "Agent5",0.17,
                new FirstFitMappingStrategy(true),
                new FloodingMessagingStrategy());

        ra5.initResourceAgent(resourceAgentVa,resourceAgentArc,
                new Capacity(node5,8,
                        16 * ScenarioBase.GB_IN_BYTE,
                        256 * ScenarioBase.GB_IN_BYTE));
        agents.add(ra5);

        // jobb edge
        final ComputingAppliance node6 = new ComputingAppliance(
                Config.createNode("Node6",
                        32,
                        32 * ScenarioBase.GB_IN_BYTE,
                        1024 * ScenarioBase.GB_IN_BYTE,
                        38,185,1300,
                        160_000,70,
                        sharedLatencyMap),
                new GeoLocation(1.3521,103.8198),
                "Asia",
                "AlibabaEdge",
                false
        );

        CBBAResourceAgent ra6 = new CBBAResourceAgent(
                "Agent6",0.31,
                new FirstFitMappingStrategy(true),
                new FloodingMessagingStrategy());

        ra6.initResourceAgent(resourceAgentVa,resourceAgentArc,
                new Capacity(node6,32,
                        32 * ScenarioBase.GB_IN_BYTE,
                        1024 * ScenarioBase.GB_IN_BYTE));
        agents.add(ra6);

        // rossz edge
        final ComputingAppliance node7 = new ComputingAppliance(
                Config.createNode("Node7",
                        2,
                        4 * ScenarioBase.GB_IN_BYTE,
                        16 * ScenarioBase.GB_IN_BYTE,
                        26,152,400,
                        30_000,195,
                        sharedLatencyMap),
                new GeoLocation(41.9028,12.4964),
                "EU",
                "MicroEdge",
                false
        );

        CBBAResourceAgent ra7 = new CBBAResourceAgent(
                "Agent7",0.08,
                new FirstFitMappingStrategy(true),
                new FloodingMessagingStrategy());

        ra7.initResourceAgent(resourceAgentVa,resourceAgentArc,
                new Capacity(node7,2,
                        4 * ScenarioBase.GB_IN_BYTE,
                        16 * ScenarioBase.GB_IN_BYTE));
        agents.add(ra7);

        // balanced edge
        final ComputingAppliance node8 = new ComputingAppliance(
                Config.createNode("Node8",
                        8,
                        16 * ScenarioBase.GB_IN_BYTE,
                        128 * ScenarioBase.GB_IN_BYTE,
                        31,162,680,
                        100_000,90,
                        sharedLatencyMap),
                new GeoLocation(40.7128,-74.0060),
                "US",
                "CiscoEdge",
                false
        );

        CBBAResourceAgent ra8 = new CBBAResourceAgent(
                "Agent8",0.23,
                new FirstFitMappingStrategy(true),
                new FloodingMessagingStrategy());

        ra8.initResourceAgent(resourceAgentVa,resourceAgentArc,
                new Capacity(node8,8,
                        16 * ScenarioBase.GB_IN_BYTE,
                        128 * ScenarioBase.GB_IN_BYTE));
        agents.add(ra8);

        // erős edge
        final ComputingAppliance node9 = new ComputingAppliance(
                Config.createNode("Node9",
                        16,
                        32 * ScenarioBase.GB_IN_BYTE,
                        512 * ScenarioBase.GB_IN_BYTE,
                        42,190,1500,
                        170_000,65,
                        sharedLatencyMap),
                new GeoLocation(34.0522,-118.2437),
                "US",
                "GoogleEdge",
                false
        );

        CBBAResourceAgent ra9 = new CBBAResourceAgent(
                "Agent9",0.34,
                new FirstFitMappingStrategy(true),
                new FloodingMessagingStrategy());

        ra9.initResourceAgent(resourceAgentVa,resourceAgentArc,
                new Capacity(node9,16,
                        32 * ScenarioBase.GB_IN_BYTE,
                        512 * ScenarioBase.GB_IN_BYTE));
        agents.add(ra9);

        // gyengébb edge
        final ComputingAppliance node10 = new ComputingAppliance(
                Config.createNode("Node10",
                        4,
                        8 * ScenarioBase.GB_IN_BYTE,
                        32 * ScenarioBase.GB_IN_BYTE,
                        25,154,520,
                        55_000,170,
                        sharedLatencyMap),
                new GeoLocation(55.7558,37.6173),
                "US",
                "LocalFog",
                false
        );

        CBBAResourceAgent ra10 = new CBBAResourceAgent(
                "Agent10",0.12,
                new FirstFitMappingStrategy(true),
                new FloodingMessagingStrategy());

        ra10.initResourceAgent(resourceAgentVa,resourceAgentArc,
                new Capacity(node10,4,
                        8 * ScenarioBase.GB_IN_BYTE,
                        32 * ScenarioBase.GB_IN_BYTE));
        agents.add(ra10);

        // átlagos edge
        final ComputingAppliance node11 = new ComputingAppliance(
                Config.createNode("Node11",
                        80,
                        80 * ScenarioBase.GB_IN_BYTE,
                        256 * ScenarioBase.GB_IN_BYTE,
                        33,168,800,
                        110_000,105,
                        sharedLatencyMap),
                new GeoLocation(19.0760,72.8777),
                "Asia",
                "EdgeIndia",
                false
        );

        CBBAResourceAgent ra11 = new CBBAResourceAgent(
                "Agent11",0.15,
                new FirstFitMappingStrategy(true),
                new FloodingMessagingStrategy());

        ra11.initResourceAgent(resourceAgentVa,resourceAgentArc,
                new Capacity(node11,8,
                        8 * ScenarioBase.GB_IN_BYTE,
                        256 * ScenarioBase.GB_IN_BYTE));
        agents.add(ra11);

        // nagyon jó edge
        final ComputingAppliance node12 = new ComputingAppliance(
                Config.createNode("Node12",
                        32,
                        32 * ScenarioBase.GB_IN_BYTE,
                        1024 * ScenarioBase.GB_IN_BYTE,
                        44,195,1750,
                        190_000,50,
                        sharedLatencyMap),
                new GeoLocation(59.3293,18.0686),
                "EU",
                "NokiaEdge",
                false
        );

        CBBAResourceAgent ra12 = new CBBAResourceAgent(
                "Agent12",0.37,
                new FirstFitMappingStrategy(true),
                new FloodingMessagingStrategy());

        ra12.initResourceAgent(resourceAgentVa,resourceAgentArc,
                new Capacity(node12,32,
                        32 * ScenarioBase.GB_IN_BYTE,
                        1024 * ScenarioBase.GB_IN_BYTE));
        agents.add(ra12);


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

        /* app submission */
        List<Path> appDescriptionFiles = Files.list((Path) Config.DUMMY_CONFIGURATION.get("inputDir"))
                .filter(f -> f.toString().endsWith(".json"))
                .toList();

        int i = 0;
        for (Path file : appDescriptionFiles) {
            List<Integer> delays = (List<Integer>) Config.DUMMY_CONFIGURATION.get("submissionDelay");
            new DeferredEvent(delays.get(i) * ScenarioBase.MINUTE_IN_MILLISECONDS) {

                @Override
                protected void eventAction() {
                    //new Submission(file, 2048, new CBBABasedDecisionMaker());
                    new Submission(file, 2048, new AsyncCBBABasedDecisionMaker());
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
                for (Capacity.Utilisation util : cap.utilisations) {
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
