package hu.u_szeged.inf.fog.simulator.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.Deployment;
import hu.u_szeged.inf.fog.simulator.agent.Submission;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.FirstFitAgentStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentApplicationReader;

public class AgentTest {

    public static void main(String[] args) throws NetworkException, IOException {
        
        SimLogger.setLogging(1, true);

        /** ranking config */
        //ResourceAgent.rankingScriptDir = "D:\\Documents\\swarm-deployment\\for_simulator";
        ResourceAgent.rankingScriptDir = "/home/markusa/Documents/SZTE/repos/swarm-deployment/for_simulator";
                
        // ResourceAgent.rankingMethodName = "random";
        // ResourceAgent.rankingMethodName = "rank_no_re";
        // ResourceAgent.rankingMethodName = "rank_re_add";
        // ResourceAgent.rankingMethodName = "rank_re_mul";
        // ResourceAgent.rankingMethodName = "vote_wo_reliability";
        // ResourceAgent.rankingMethodName = "vote_w_reliability";
         ResourceAgent.rankingMethodName = "vote_w_reliability_mul";
        
        /** applications */
        Path inputDir = Paths.get(ScenarioBase.resourcePath + "AGENT_examples");

        /** clouds */
        Map<String, Integer> sharedLatencyMap = new HashMap<>();        
        
        double capacity = 100; 
        
        ComputingAppliance cloud1 = new ComputingAppliance(
                createCloud("cloud1", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 100_000, 30, 180, 300, sharedLatencyMap, 89),
                new GeoLocation(47.50, 19.08), "EU", "AWS"); // Budapest

            ComputingAppliance cloud2 = new ComputingAppliance(
                createCloud("cloud2", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 125_000, 35, 200, 320, sharedLatencyMap, 70),
                new GeoLocation(48.86, 2.35), "EU", "Azure"); // Paris

            ComputingAppliance cloud3 = new ComputingAppliance(
                createCloud("cloud3", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 190_000, 25, 170, 290, sharedLatencyMap, 95),
                new GeoLocation(52.52, 13.40), "EU", "AWS"); // Berlin

            ComputingAppliance cloud4 = new ComputingAppliance(
                createCloud("cloud4", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 170_000, 40, 210, 330, sharedLatencyMap, 60),
                new GeoLocation(41.90, 12.50), "EU", "Azure"); // Rome

            ComputingAppliance cloud5 = new ComputingAppliance(
                createCloud("cloud5", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 130_000, 20, 190, 310, sharedLatencyMap, 58),
                new GeoLocation(40.71, -74.00), "US", "AWS"); // New York

            ComputingAppliance cloud6 = new ComputingAppliance(
                createCloud("cloud6", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 140_000, 25, 220, 360, sharedLatencyMap, 79),
                new GeoLocation(34.05, -118.25), "US", "Azure"); // Los Angeles

            ComputingAppliance cloud7 = new ComputingAppliance(
                createCloud("cloud7", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 195_000, 15, 150, 280, sharedLatencyMap, 55),
                new GeoLocation(37.77, -122.42), "US", "AWS"); // San Francisco

            ComputingAppliance cloud8 = new ComputingAppliance(
                createCloud("cloud8", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 120_000, 30, 200, 340, sharedLatencyMap, 99),
                new GeoLocation(41.88, -87.63), "US", "Azure"); // Chicago
        
        
        new EnergyDataCollector(cloud1.iaas, 60 * 1000);
        new EnergyDataCollector(cloud2.iaas, 60 * 1000);
        new EnergyDataCollector(cloud3.iaas, 60 * 1000);
        new EnergyDataCollector(cloud4.iaas, 60 * 1000);
        new EnergyDataCollector(cloud5.iaas, 60 * 1000);
        new EnergyDataCollector(cloud6.iaas, 60 * 1000);
        new EnergyDataCollector(cloud7.iaas, 60 * 1000);
        new EnergyDataCollector(cloud8.iaas, 60 * 1000);

        /** agents */
        VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L); 
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);
        
        new ResourceAgent("Agent-1", 0.0119, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false), 
                new Capacity(cloud1, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        new ResourceAgent("Agent-2", 0.0115, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(cloud2, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));

        new ResourceAgent("Agent-3", 0.0113, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(cloud3, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));

        new ResourceAgent("Agent-4", 0.0111, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(cloud4, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        new ResourceAgent("Agent-5", 0.0112, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(cloud5, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        new ResourceAgent("Agent-6", 0.0114, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(cloud6, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        new ResourceAgent("Agent-7", 0.0116, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(cloud7, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        new ResourceAgent("Agent-8", 0.0118, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(cloud8, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));

        /** Image service */
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(1, 1, 1, 1, 1);
        
        Deployment.registryService = new Repository(Long.MAX_VALUE, "Image_Service", 125_000, 125_000, 125_000, sharedLatencyMap, 
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage), 
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));
        
        Deployment.setImageRegistry(Deployment.registryService);

        /** submitting applications */
            Files.list(inputDir)
                .filter(file -> file.toString().endsWith(".json"))
                .forEach(file -> {
                         new Submission(AgentApplicationReader.readAgentApplications(file.toString()), 2048, 0);
                 });
        
        Timed.simulateUntilLastEvent();
        
        /** results */
        SimLogger.logRes("\nSimulation completed.");
        
        SimLogger.logRes("\nCapacity usage: ");
        double totalCost = 0;
        for (ResourceAgent agent : ResourceAgent.resourceAgents) {
            double runtime = 0;
            for (Capacity cap : agent.capacities) {
                SimLogger.logRes("\t" + cap);
                for (Utilisation util : cap.utilisations) {
                   SimLogger.logRes("\t\t" + util);
                   runtime += (Timed.getFireCount() - util.initTime);
                }
            }
            totalCost += agent.hourlyPrice * (runtime / 1000 / 60 / 60);
        }

        SimLogger.logRes("\nSimulation time (min.): " + Timed.getFireCount() / 1000.0 / 60.0);
        SimLogger.logRes("Total price (EUR): " + totalCost);
        
        
        double avgDeploymentTime = 0.0;
        for (AgentApplication app : AgentApplication.agentApplications) {
            SimLogger.logRes(app.name + " deployment: ");
            if(app.deploymentTime != -1) {
                avgDeploymentTime += app.deploymentTime;
                SimLogger.logRes("\tTime (min.): " + app.deploymentTime / 1000 / 60);
            } else {
                SimLogger.logRes("\tTime (min.): -1");
            }
            SimLogger.logRes("\tTime (min.): " + (app.deploymentTime != -1 ? app.deploymentTime / 1000 / 60 : -1));
            SimLogger.logRes("\tAvailable offers: " + app.offers.size());
            if(app.offers.size() > 0) {
                StringBuilder str = new StringBuilder();
                for(ResourceAgent ra : app.offers.get((app.winningOffer)).agentResourcesMap.keySet()) {
                    str.append(ra.name + " ");
                }
                SimLogger.logRes("\tWinning offer: " + app.offers.get((app.winningOffer)).id + " ( " + str.toString() + ")");
            }
        }
        SimLogger.logRes("Average deployment time: (min.) " + (avgDeploymentTime / AgentApplication.agentApplications.size() / 1000 / 60));
        double totalEnergy = 0;
        for (EnergyDataCollector ec : EnergyDataCollector.energyCollectors) {
            totalEnergy += ec.energyConsumption / 1000 / 3_600_000;
        }
        SimLogger.logRes("Total energy (kWh): " + totalEnergy);
        
        /*
        for(ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            SimLogger.logRes(ca.name + ":");
            SimLogger.logRes("\t Contents:");
            for(StorageObject so : ca.iaas.repositories.get(0).contents()) {
                SimLogger.logRes("\t\t" + so);
            }
            SimLogger.logRes("\t VMs:");
            for(VirtualMachine vm : ca.iaas.listVMs()) {
                SimLogger.logRes("\t\t" + vm.toString());
            }
        }
        
        SimLogger.logRes("Image Registry's contents");
        for(StorageObject so : Submission.imageRegistry.contents()) {
            SimLogger.logRes("\t" + so);
        }
        */
    }
    
    private static IaaSService createCloud(String name, double cpu, long memory, long storage, long bandwidth,
            double minpower, double idlepower, double maxpower, Map<String, Integer> latencyMap, int latency) { 
        
        IaaSService iaas = null;
        
        try {
             iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);     
             final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                     PowerTransitionGenerator.generateTransitions(minpower, idlepower, maxpower, 10, 10);
             
             // PM
             Repository pmRepo1 = new Repository(storage, name + "-localRepo", bandwidth, bandwidth, bandwidth, latencyMap, 
                     transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                     transitions.get(PowerTransitionGenerator.PowerStateKind.network));

             PhysicalMachine pm1 = new PhysicalMachine(cpu, 1, memory, pmRepo1, 60_000, 60_000, 
                     transitions.get(PowerTransitionGenerator.PowerStateKind.host));

             iaas.registerHost(pm1);
             
             // Repository
             Repository cloudRepo = new Repository(storage, name + "-cloudRepo", bandwidth, bandwidth, bandwidth, latencyMap, 
                     transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                     transitions.get(PowerTransitionGenerator.PowerStateKind.network));
             
             iaas.registerRepository(cloudRepo);
             latencyMap.put(name + "-localRepo", latency);
             latencyMap.put(name + "-cloudRepo", latency);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return iaas;
    }
}