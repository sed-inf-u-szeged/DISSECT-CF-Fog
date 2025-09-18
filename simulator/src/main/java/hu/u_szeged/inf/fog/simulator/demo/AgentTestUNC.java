package hu.u_szeged.inf.fog.simulator.demo;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.Deployment;
import hu.u_szeged.inf.fog.simulator.agent.Submission;
import hu.u_szeged.inf.fog.simulator.agent.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.DirectMappingAgentStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.FirstFitAgentStrategy;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.RemoteServer;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.Sun;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

public class AgentTestUNC {

    public static void main(String[] args) throws NetworkException, IOException {
        
        SimLogger.setLogging(1, true);
        
        SeedSyncer.modifySeed(9876543210L);
        
        long simLength = 1 * 24 * 60 * 60 * 1000; 
        int numOfApps = 2;
 
        /** ranking config */
        //ResourceAgent.rankingScriptDir = "D:\\Documents\\swarm-deployment\\for_simulator";
        ResourceAgent.rankingScriptDir = "/home/markus/Documents/projects/swarm-deployment/for_simulator";
                
        ResourceAgent.rankingMethodName = "rank_no_re";
        // ResourceAgent.rankingMethodName = "rank_re_add";
        // ResourceAgent.rankingMethodName = "rank_re_mul";
        // ResourceAgent.rankingMethodName = "vote_wo_reliability";
        // ResourceAgent.rankingMethodName = "vote_w_reliability";
        // ResourceAgent.rankingMethodName = "vote_w_reliability_mul";
        // ResourceAgent.rankingMethodName = "random";
        
        /** applications */
        //Path inputDir = Paths.get(ScenarioBase.resourcePath + "AGENT_examples");
        Path inputDir = Paths.get(ScenarioBase.resourcePath + "AGENT_examples2");
        
        /** nodes */
        Map<String, Integer> sharedLatencyMap = new HashMap<>();        
        
        double capacity = 256 * numOfApps; 
        
        for(int i = 0; i < numOfApps; i++) {
        	//for(int j = 0; j < 5; j++) {
        	    ComputingAppliance node1 = new ComputingAppliance(
                    createNode("Node1" + i, 5, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 30, 180, 2200, 12_500, 100, sharedLatencyMap),
                    new GeoLocation(47.50, 19.08), "EU", "AWS", true); // Budapest

                ComputingAppliance node2 = new ComputingAppliance(
                    createNode("Node2" + i, 5, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 40, 225, 3300, 25_000, 50, sharedLatencyMap),
                    new GeoLocation(48.86, 2.35), "EU", "Azure", true); // Paris

                ComputingAppliance node3 = new ComputingAppliance(
                    createNode("Node3" + i, 5, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 50, 170, 3400, 62_500, 20, sharedLatencyMap),
                    new GeoLocation(52.52, 13.40), "EU", "AWS", true); // Berlin
                        
                ComputingAppliance node4 = new ComputingAppliance(
                    createNode("Node4" + i, 5, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 55, 210, 3200, 125_000, 30, sharedLatencyMap),
                    new GeoLocation(41.90, 12.50), "EU", "Azure", true); // Rome
                      
                ComputingAppliance node5 = new ComputingAppliance(
                    createNode("Node5" + i, 5, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 45, 190, 500, 6_250, 80, sharedLatencyMap),
                    new GeoLocation(40.71, -74.00), "EU", "AWS", true); // New York
                
                ComputingAppliance node9 = new ComputingAppliance(
                        createNode("Node9" + i, 5, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 30, 180, 2200, 12_500, 100, sharedLatencyMap),
                        new GeoLocation(47.50, 19.08), "EU", "AWS", true); // Budapest

                    ComputingAppliance node10 = new ComputingAppliance(
                        createNode("Node10" + i, 5, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 40, 225, 3300, 25_000, 50, sharedLatencyMap),
                        new GeoLocation(48.86, 2.35), "EU", "Azure", true); // Paris

                    ComputingAppliance node11 = new ComputingAppliance(
                        createNode("Node11" + i, 5, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 50, 170, 3400, 62_500, 20, sharedLatencyMap),
                        new GeoLocation(52.52, 13.40), "EU", "AWS", true); // Berlin
                            
                    ComputingAppliance node12 = new ComputingAppliance(
                        createNode("Node12" + i, 5, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 55, 210, 3200, 125_000, 30, sharedLatencyMap),
                        new GeoLocation(41.90, 12.50), "EU", "Azure", true); // Rome
                          
                    ComputingAppliance node13 = new ComputingAppliance(
                        createNode("Node13" + i, 5, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 45, 190, 500, 6_250, 80, sharedLatencyMap),
                        new GeoLocation(40.71, -74.00), "EU", "AWS", true);
                
                new EnergyDataCollector("Node1" + i, node1.iaas, true);
                new EnergyDataCollector("Node2" + i, node2.iaas, true);
                new EnergyDataCollector("Node3" + i, node3.iaas, true);
                new EnergyDataCollector("Node4" + i, node4.iaas, true);
                new EnergyDataCollector("Node5" + i, node5.iaas, true);
                new EnergyDataCollector("Node9" + i, node1.iaas, true);
                new EnergyDataCollector("Node10" + i, node2.iaas, true);
                new EnergyDataCollector("Node11" + i, node3.iaas, true);
                new EnergyDataCollector("Node12" + i, node4.iaas, true);
                new EnergyDataCollector("Node13" + i, node5.iaas, true);
        	//}
        }
        
        ComputingAppliance node6 = new ComputingAppliance(
            createNode("Node6", capacity, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 35, 175, 3550, 100_000, 15, sharedLatencyMap),
            new GeoLocation(34.05, -118.25), "US", "Azure", false); // Los Angeles
         
        ComputingAppliance node7 = new ComputingAppliance(
            createNode("Node7", capacity, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 30, 150, 2200, 37_500, 70, sharedLatencyMap),
            new GeoLocation(37.77, -122.42), "US", "AWS", false); // San Francisco

        ComputingAppliance node8 = new ComputingAppliance(
            createNode("Node8", capacity, 1, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L, 40, 200, 3500, 150_000, 60, sharedLatencyMap),
            new GeoLocation(41.88, -87.63), "US", "Azure", false); // Chicago
            
        new EnergyDataCollector("Node6", node6.iaas, true);
        new EnergyDataCollector("Node7", node7.iaas, true);
        new EnergyDataCollector("Node8", node8.iaas, true);

        /** agents */
        VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L); 
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);
        
        Map<String, String> mapping = new HashMap<>();
        
        ResourceAgent ra1 = new ResourceAgent("Agent-1", 0.00002778, resourceAgentVa, resourceAgentArc, new DirectMappingAgentStrategy(mapping));
        ResourceAgent ra2 = new ResourceAgent("Agent-2", 0.00000278, resourceAgentVa, resourceAgentArc, new DirectMappingAgentStrategy(mapping));
        ResourceAgent ra3 = new ResourceAgent("Agent-3", 0.00013889, resourceAgentVa, resourceAgentArc, new DirectMappingAgentStrategy(mapping));
        ResourceAgent ra4 = new ResourceAgent("Agent-4", 0.00037778, resourceAgentVa, resourceAgentArc, new DirectMappingAgentStrategy(mapping));
        ResourceAgent ra5 = new ResourceAgent("Agent-5", 0.00005556, resourceAgentVa, resourceAgentArc, new DirectMappingAgentStrategy(mapping));
        		
        for(int i = 0; i < numOfApps; i++) {
        	
        	mapping.put("UNC-" + i + "-Res-1", "Agent-1");
        	mapping.put("UNC-" + i + "-Res-2", "Agent-2");
        	mapping.put("UNC-" + i + "-Res-3", "Agent-3");
        	mapping.put("UNC-" + i + "-Res-4", "Agent-4");
        	mapping.put("UNC-" + i + "-Res-5", "Agent-5");
        	
        	mapping.put("UNC-" + i + "-Res-6", "Agent-1");
        	mapping.put("UNC-" + i + "-Res-7", "Agent-2");
        	mapping.put("UNC-" + i + "-Res-8", "Agent-3");
        	mapping.put("UNC-" + i + "-Res-9", "Agent-4");
        	mapping.put("UNC-" + i + "-Res-10", "Agent-5");
        	
        	ra1.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("Node1" + i), 5, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        	ra2.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("Node2" + i), 5, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        	ra3.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("Node3" + i), 5, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        	ra4.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("Node4" + i), 5, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        	ra5.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("Node5" + i), 5, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        	ra1.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("Node9" + i), 5, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        	ra2.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("Node10" + i), 5, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        	ra3.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("Node11" + i), 5, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        	ra4.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("Node12" + i), 5, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        	ra5.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("Node13" + i), 5, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        }
        
        ra1.initResourceAgent(resourceAgentVa, resourceAgentArc);
        ra2.initResourceAgent(resourceAgentVa, resourceAgentArc);
        ra3.initResourceAgent(resourceAgentVa, resourceAgentArc);
        ra4.initResourceAgent(resourceAgentVa, resourceAgentArc);
        ra5.initResourceAgent(resourceAgentVa, resourceAgentArc);
            
        ResourceAgent ra6 = new ResourceAgent("Agent-6", 0.00013889, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(node6, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        ResourceAgent ra7 = new ResourceAgent("Agent-7", 0.00277778, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(node7, capacity, (long) capacity * 1_073_741_824L, (long) capacity * 1_073_741_824L));
        
        ResourceAgent ra8 = new ResourceAgent("Agent-8", 0.00041667, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
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
                         new Submission(file.toString(), 2048, 0);
                 });
            
        Sun.init(6, 20, 13, 1.5);
        new CsvExporter(Sun.getInstance());
        long starttime = System.nanoTime();       
        Timed.simulateUntil(simLength);
        long stoptime = System.nanoTime();
        
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

        SimLogger.logRes("Size of generated files (MB): " + NoiseSensor.generatedFileSize / 1_048_576);
        
        SimLogger.logRes("Average time to transfer a file over the network (sec.): " + (NoiseSensor.timeOnNetwork / 1000.0) / NoiseSensor.generatedFiles);	
        
        SimLogger.logRes("Number of sound events (pc.): " + NoiseSensor.generatedFiles);
        
        SimLogger.logRes("Number of offloaded sound events (pc.): " + NoiseSensor.offloadedFiles);
        
        SimLogger.logRes("Number of sound events requiring processing (pc.): " + NoiseSensor.soundEventsReqProcessing);
        
        SimLogger.logRes("Number of processed files (pc.): " + NoiseSensor.processedFiles);
        
        long soundFilesNs = 0;
        long soundFilesRs = 0;
        for(SwarmAgent sa : SwarmAgent.allSwarmAgents) {
        	for(Object o : sa.components) {
                if (o.getClass().equals(NoiseSensor.class)) {
                    NoiseSensor ns = (NoiseSensor) o;
                    //System.out.println("NS: " + ns.util.resource.name);
                    //System.out.println(ns.pm.localDisk.contents().size()); 
                    for (StorageObject so : ns.pm.localDisk.contents()) {
                        if(so.id.contains("Noise-Sensor")) {
                            soundFilesNs++;
                        }
                    }
                    
                } else {
                    RemoteServer rs = (RemoteServer) o;
                    //System.out.println("RS:");
                    //System.out.println(rs.pm.localDisk.contents().size()); 
                    for (StorageObject so : rs.pm.localDisk.contents()) {
                        if(so.id.contains("Noise-Sensor")) {
                            soundFilesRs++;
                        }
                    }
                }
            }
        }
        
        SimLogger.logRes("Number of sound files on noise sensors: " + soundFilesNs);
        //SimLogger.logRes("Number of sound files on the remote servers: " + soundFilesRs);
        
        SimLogger.logRes("Runtime (seconds): " + TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));
        
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
    
    static IaaSService createNode(String name, double cpu, double perCoreProcessing, long memory, long storage, double minpower, 
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

             PhysicalMachine pm1 = new PhysicalMachine(cpu, perCoreProcessing, memory, pmRepo1, 60_000, 60_000, 
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

class CsvExporter extends Timed {

    Sun sun;
        
    public CsvExporter(Sun sun) {

        this.sun = sun;
        subscribe(10_000);
    }

    @Override
    public void tick(long fires) {
    	try (PrintWriter writer = new PrintWriter(new FileWriter(ScenarioBase.resultDirectory + "/classifiers.csv", true))) {
            StringBuilder row = new StringBuilder();
            row.append(String.format(Locale.ROOT, "%.5f",Timed.getFireCount() / 1000.0 / 60.0 / 60.0)); 

            if(SwarmAgent.allSwarmAgents.size() > 0) {
            	row.append(",");
                row.append(String.format(Locale.ROOT, "%d", SwarmAgent.allSwarmAgents.get(0).noiseSensorsWithClassification.size()));
            }
            writer.println(row.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    	
    	try (PrintWriter writer = new PrintWriter(new FileWriter(ScenarioBase.resultDirectory + "/cpuload.csv", true))) {
            StringBuilder row = new StringBuilder();
            row.append(String.format(Locale.ROOT, "%.5f",Timed.getFireCount() / 1000.0 / 60.0 / 60.0)); 

            if(SwarmAgent.allSwarmAgents.size() > 0) {
            	row.append(",");
                row.append(String.format(Locale.ROOT, "%.5f", SwarmAgent.allSwarmAgents.get(0).avgCpu()));
            }
            writer.println(row.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    	
        try (PrintWriter writer = new PrintWriter(new FileWriter(ScenarioBase.resultDirectory + "/temperature.csv", true))) {
            StringBuilder row = new StringBuilder();
            row.append(String.format(Locale.ROOT, "%.5f",Timed.getFireCount() / 1000.0 / 60.0 / 60.0)); 

            if(SwarmAgent.allSwarmAgents.size() > 0) {
            	for (Object o : SwarmAgent.allSwarmAgents.get(0).components) {
                    if (o instanceof NoiseSensor) {
                        NoiseSensor ns = (NoiseSensor) o;
                        row.append(",");
                        row.append(String.format(Locale.ROOT, "%.5f", ns.cpuTemp));
                    }
                }
            }
            

            writer.println(row.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}