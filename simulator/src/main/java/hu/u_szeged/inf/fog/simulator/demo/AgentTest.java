package hu.u_szeged.inf.fog.simulator.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
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
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
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
        
        SeedSyncer.modifySeed(9876543210L);

        /** ranking config */
        //ResourceAgent.rankingScriptDir = "D:\\Documents\\swarm-deployment\\for_simulator";
        ResourceAgent.rankingScriptDir = "/home/markusa/Documents/SZTE/repos/swarm-deployment/for_simulator";
                
        // ResourceAgent.rankingMethodName = "random";
        // ResourceAgent.rankingMethodName = "vote_wo_reliability";
        ResourceAgent.rankingMethodName = "rank_no_re";
         
        // ResourceAgent.rankingMethodName = "rank_re_add";
        // ResourceAgent.rankingMethodName = "rank_re_mul";
        // ResourceAgent.rankingMethodName = "vote_w_reliability";
        // ResourceAgent.rankingMethodName = "vote_w_reliability_mul";
        
        /** applications */
        // Path inputDir = Paths.get(ScenarioBase.resourcePath + "AGENT_energy");
        // Path inputDir = Paths.get(ScenarioBase.resourcePath + "AGENT_price");
        // Path inputDir = Paths.get(ScenarioBase.resourcePath + "AGENT_latency");
        // Path inputDir = Paths.get(ScenarioBase.resourcePath + "AGENT_bandwidth");
        Path inputDir = Paths.get(ScenarioBase.resourcePath + "AGENT_examples");
        
        /** nodes */
        Map<String, Integer> sharedLatencyMap = new HashMap<>();        
        
        double capacity = 100; 
        
        ComputingAppliance node1 = new ComputingAppliance(
                createNode("node1", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 30, 180, 2200, 12_500, 100, sharedLatencyMap),
                new GeoLocation(47.50, 19.08), "EU", "AWS"); // Budapest

            ComputingAppliance node2 = new ComputingAppliance(
                createNode("node2", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 40, 225, 3300, 25_000, 50, sharedLatencyMap),
                new GeoLocation(48.86, 2.35), "EU", "Azure"); // Paris

            ComputingAppliance node3 = new ComputingAppliance(
                createNode("node3", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 50, 170, 3400, 62_500, 20, sharedLatencyMap),
                new GeoLocation(52.52, 13.40), "EU", "AWS"); // Berlin
                
            ComputingAppliance node4 = new ComputingAppliance(
                createNode("node4", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 55, 210, 3200, 125_000, 30, sharedLatencyMap),
                new GeoLocation(41.90, 12.50), "EU", "Azure"); // Rome
                
            ComputingAppliance node5 = new ComputingAppliance(
                createNode("node5", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 45, 190, 500, 6_250, 80, sharedLatencyMap),
                new GeoLocation(40.71, -74.00), "US", "AWS"); // New York

            ComputingAppliance node6 = new ComputingAppliance(
                createNode("node6", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 35, 175, 3550, 100_000, 15, sharedLatencyMap),
                new GeoLocation(34.05, -118.25), "US", "Azure"); // Los Angeles

            ComputingAppliance node7 = new ComputingAppliance(
                createNode("node7", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 30, 150, 2200, 37_500, 70, sharedLatencyMap),
                new GeoLocation(37.77, -122.42), "US", "AWS"); // San Francisco

            ComputingAppliance node8 = new ComputingAppliance(
                createNode("node8", capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 40, 200, 3500, 150_000, 60, sharedLatencyMap),
                new GeoLocation(41.88, -87.63), "US", "Azure"); // Chicago
        
        new EnergyDataCollector("node-1", node1.iaas, true);
        new EnergyDataCollector("node-2", node2.iaas, true);
        new EnergyDataCollector("node-3", node3.iaas, true);
        new EnergyDataCollector("node-4", node4.iaas, true);
        new EnergyDataCollector("node-5", node5.iaas, true);
        new EnergyDataCollector("node-6", node6.iaas, true);
        new EnergyDataCollector("node-7", node7.iaas, true);
        new EnergyDataCollector("node-8", node8.iaas, true);

        /** agents */
        VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L); 
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);
        
        new ResourceAgent("Agent-1", 0.00002778, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false), 
                new Capacity(node1, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        new ResourceAgent("Agent-2", 0.00000278, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(node2, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));

        new ResourceAgent("Agent-3", 0.00013889, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(node3, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));

        new ResourceAgent("Agent-4", 0.00037778, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(node4, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        new ResourceAgent("Agent-5", 0.00005556, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(node5, 16, (long) 16 * 1_073_741_824L, (long) 16 * 1_073_741_824L));
        
        new ResourceAgent("Agent-6", 0.00013889, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(node6, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        new ResourceAgent("Agent-7", 0.00277778, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(node7, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        new ResourceAgent("Agent-8", 0.00041667, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(node8, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));

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
            double cores = 0;
            for (Capacity cap : agent.capacities) {
                SimLogger.logRes("\t" + cap);
                for (Utilisation util : cap.utilisations) {
                   SimLogger.logRes("\t\t" + util);
                   runtime += (Timed.getFireCount() - util.initTime);
                   cores += util.utilisedCpu;
                }
            }
            totalCost += cores * agent.hourlyPrice * (runtime / 1000 / 60 / 60);
        }

        double avgDeploymentTime = 0.0;
        double avgOffers = 0.0;
        for (AgentApplication app : AgentApplication.agentApplications) {
            SimLogger.logRes(app.name + " deployment: ");
            if(app.deploymentTime != -1) {
                avgDeploymentTime += app.deploymentTime;
                SimLogger.logRes("\tTime (min.): " + app.deploymentTime / 1000 / 60);
            } else {
                SimLogger.logRes("\tTime (min.): -1");
            }
            SimLogger.logRes("\tAvailable offers: " + app.offers.size());
            avgOffers += app.offers.size();
            if(app.offers.size() > 0) {
                StringBuilder str = new StringBuilder();
                for(ResourceAgent ra : app.offers.get((app.winningOffer)).agentResourcesMap.keySet()) {
                    str.append(ra.name + " ");
                }
                SimLogger.logRes("\tWinning offer: " + app.offers.get((app.winningOffer)).id + " ( " + str.toString() + ")");
            }
        }
        
        SimLogger.logRes("\nSimulation time (min.): " + Timed.getFireCount() / 1000.0 / 60.0);
        
        DecimalFormat df = new DecimalFormat("#.####");
        SimLogger.logRes("Total price (EUR): " + df.format(totalCost));
        SimLogger.logRes("Average deployment time (min.): " + (avgDeploymentTime / AgentApplication.agentApplications.size() / 1000 / 60));
        
        double totalEnergy = 0;
        for (EnergyDataCollector ec : EnergyDataCollector.energyCollectors) {
            totalEnergy += ec.energyConsumption / 1000 / 3_600_000;
        }
        SimLogger.logRes("Total energy (kWh): " + totalEnergy);
        
        SimLogger.logRes("Average number of offers (pc.): " + (avgOffers / AgentApplication.agentApplications.size()));

        EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);
        
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
    
    private static IaaSService createNode(String name, double cpu, long memory, long storage, double minpower, 
            double idlepower, double maxpower, long bandwidth, int latency, Map<String, Integer> latencyMap) { 
        
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
             Repository nodeRepo = new Repository(storage, name + "-nodeRepo", bandwidth, bandwidth, bandwidth, latencyMap, 
                     transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                     transitions.get(PowerTransitionGenerator.PowerStateKind.network));
             
             iaas.registerRepository(nodeRepo);
             latencyMap.put(name + "-localRepo", latency);
             latencyMap.put(name + "-nodeRepo", latency);
             
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return iaas;
    }
}