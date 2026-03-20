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
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.Deployment;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.Submission;
import hu.u_szeged.inf.fog.simulator.agent.application.dummy.DummyServer;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DummyAppDemo {
    
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
            Config.createNode("Node1", 52, 52 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                    35, 200, 535, 100_000, 70, sharedLatencyMap),
            new GeoLocation(51.5074, -0.1278), "EU", "Azure", false); // London
         
        final ComputingAppliance node2 = new ComputingAppliance(
            Config.createNode("Node2", 64, 64 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                    30, 296, 493, 37_500, 30, sharedLatencyMap),
            new GeoLocation(48.8566, 2.3522), "EU", "AWS", false); // Paris

        final ComputingAppliance node3 = new ComputingAppliance(
            Config.createNode("Node3", 32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                    40, 398, 533, 150_000, 60, sharedLatencyMap),
            new GeoLocation(52.5200, 13.4050), "EU", "Azure", false); // Berlin
        
        final ComputingAppliance node4 = new ComputingAppliance(
            Config.createNode("Node4", 48, 48 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                    30, 150, 535, 37_500, 70, sharedLatencyMap),
            new GeoLocation(41.8781, -87.6298), "US", "AWS", false); // Chicago

        final ComputingAppliance node5 = new ComputingAppliance(
            Config.createNode("Node5", 32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                    40, 200, 506, 150_000, 60, sharedLatencyMap),
            new GeoLocation(29.7604, -95.3698), "US", "Azure", false); // Houston

        new EnergyDataCollector("Node1-energy", node1.iaas,true, true);
        new EnergyDataCollector("Node2-energy", node2.iaas,true,true);
        new EnergyDataCollector("Node3-energy", node3.iaas,true,true);
        new EnergyDataCollector("Node4-energy", node4.iaas,true, true);
        new EnergyDataCollector("Node5-energy", node5.iaas,true,true);
        
        /* agent config */
        VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L);
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);
                
        ResourceAgent ra1 =
                new ResourceAgent("Agent1", 0.00013889, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra1.initResourceAgent(resourceAgentVa, resourceAgentArc, 
                new Capacity(node1, 52, 52 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        ResourceAgent ra2 = 
                new ResourceAgent("Agent2", 0.00277778, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra2.initResourceAgent(resourceAgentVa, resourceAgentArc, 
                new Capacity(node2, 64, 64 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        ResourceAgent ra3 = 
                new ResourceAgent("Agent3", 0.00041667, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra3.initResourceAgent(resourceAgentVa, resourceAgentArc, 
                new Capacity(node3, 32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        ResourceAgent ra4 = 
                new ResourceAgent("Agent4", 0.00000278, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra4.initResourceAgent(resourceAgentVa, resourceAgentArc, 
                new Capacity(node4, 48, 48 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                new Capacity(node5, 32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));
    
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
                    new Submission(file, 2048);
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