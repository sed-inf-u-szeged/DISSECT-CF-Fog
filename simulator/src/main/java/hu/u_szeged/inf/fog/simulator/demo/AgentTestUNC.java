package hu.u_szeged.inf.fog.simulator.demo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
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
import hu.u_szeged.inf.fog.simulator.agent.agentstrategy.DirectMappingAgentStrategy;
import hu.u_szeged.inf.fog.simulator.agent.agentstrategy.FirstFitAgentStrategy;
import hu.u_szeged.inf.fog.simulator.agent.messagestrategy.GuidedSearchMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.Sun;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.AgentVisualiser;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

public class AgentTestUNC {

    public static void main(String[] args) throws NetworkException, IOException {

        SimLogger.setLogging(1, true);
        SeedSyncer.modifySeed(9876543210L);

        /** general config */
        long simLength = 1 * 24 * 60 * 60 * 1000;
        int numOfApps = 1;

        /** app config */
        HashMap<String, Number> configuration = new HashMap<>();
        	configuration.put("samplingFreq", 10_000);   // 10 sec.
        	configuration.put("soundFileSize", 655_360); // 640 kB
        	configuration.put("resFileSize", 1024);      // 1 kB
        	configuration.put("minSoundLevel", 30);		 // dB
        	configuration.put("maxSoundLevel", 130);	 // dB
        	configuration.put("soundTreshold", 70);		 // dB
        	configuration.put("cpuTimeWindow", 600_000); // 10 min.
        	configuration.put("minCpuTemp", 55);		 // ℃
        	configuration.put("cpuTempTreshold", 80);	 // ℃
        	configuration.put("maxCpuTemp", 85);		 // ℃
        	configuration.put("minContainerCount", 2);	 // pc.
        	configuration.put("cpuLoadScaleUp", 70);	 // %
        	configuration.put("cpuLoadScaleDown", 30);   // %

         Path inputDir = Paths.get(ScenarioBase.resourcePath + "AGENT_examples");
        // Path inputDir = Paths.get(ScenarioBase.resourcePath + "AGENT_examples3");

        /** ranking config */
        // ResourceAgent.rankingScriptDir = "D:\\Documents\\swarm-deployment\\for_simulator";
        ResourceAgent.rankingScriptDir = "/home/markus/Documents/projects/swarm-deployment/for_simulator";

        ResourceAgent.rankingMethodName = "rank_no_re";
        // ResourceAgent.rankingMethodName = "rank_re_add";
        // ResourceAgent.rankingMethodName = "rank_re_mul";
        // ResourceAgent.rankingMethodName = "vote_wo_reliability";
        // ResourceAgent.rankingMethodName = "vote_w_reliability";
        // ResourceAgent.rankingMethodName = "vote_w_reliability_mul";
        // ResourceAgent.rankingMethodName = "random";

        /** nodes and RPis */
        Map<String, Integer> sharedLatencyMap = new HashMap<>();

        for (int i = 1; i <= numOfApps; i++) {
        	ComputingAppliance rpi1 = new ComputingAppliance(
               createNode("RPi1" + i, 5, 1, 8 * 1_073_741_824L, 256 * 1_073_741_824L, 2, 4, 15, 12_500, 5, sharedLatencyMap),
               new GeoLocation(45.51, 13.79), "EU", "", true);

            ComputingAppliance rpi2 = new ComputingAppliance(
               createNode("RPi2" + i, 5, 1, 8 * 1_073_741_824L, 256 * 1_073_741_824L, 2, 4, 15, 6_250, 10, sharedLatencyMap),
               new GeoLocation(45.52, 13.69), "EU", "", true);

            ComputingAppliance rpi3 = new ComputingAppliance(
               createNode("RPi3" + i, 5, 1, 8 * 1_073_741_824L, 256 * 1_073_741_824L, 2, 4, 15, 10_000, 6, sharedLatencyMap),
               new GeoLocation(45.58, 13.66), "", "", true);

            ComputingAppliance rpi4 = new ComputingAppliance(
               createNode("RPi4" + i, 5, 1, 8 * 1_073_741_824L, 256 * 1_073_741_824L, 2, 4, 15, 15_000, 9, sharedLatencyMap),
               new GeoLocation(45.46, 13.81), "", "", true);

            ComputingAppliance rpi5 = new ComputingAppliance(
               createNode("RPi5" + i, 5, 1, 8 * 1_073_741_824L, 256 * 1_073_741_824L, 2, 4, 15, 9_500, 7, sharedLatencyMap),
               new GeoLocation(45.53, 13.71), "", "", true);

            ComputingAppliance rpi6 = new ComputingAppliance(
               createNode("RPi6" + i, 5, 1, 8 * 1_073_741_824L, 256 * 1_073_741_824L, 2, 4, 15, 8_750, 8, sharedLatencyMap),
               new GeoLocation(45.58, 13.74), "", "", true);

            ComputingAppliance rpi7 = new ComputingAppliance(
               createNode("RPi7" + i, 5, 1, 8 * 1_073_741_824L, 256 * 1_073_741_824L, 2, 4, 15, 15_000, 10, sharedLatencyMap),
               new GeoLocation(45.50, 13.70), "", "", true);

            ComputingAppliance rpi8 = new ComputingAppliance(
               createNode("RPi8" + i, 5, 1, 8 * 1_073_741_824L, 256 * 1_073_741_824L, 2, 4, 15, 62_500, 9, sharedLatencyMap),
               new GeoLocation(45.62, 13.76), "", "", true);

            ComputingAppliance rpi9 = new ComputingAppliance(
               createNode("RPi9" + i, 5, 1, 8 * 1_073_741_824L, 256 * 1_073_741_824L, 2, 4, 15, 125_000, 8, sharedLatencyMap),
               new GeoLocation(45.59, 13.67), "", "", true);

            ComputingAppliance rpi10 = new ComputingAppliance(
               createNode("RPi10" + i, 5, 1, 8 * 1_073_741_824L, 256 * 1_073_741_824L, 2, 4, 15, 12_500, 8, sharedLatencyMap),
               new GeoLocation(45.52, 13.66), "", "", true);

            new EnergyDataCollector("RPi1" + i, rpi1.iaas, true);
            new EnergyDataCollector("RPi2" + i, rpi2.iaas, true);
            new EnergyDataCollector("RPi3" + i, rpi3.iaas, true);
            new EnergyDataCollector("RPi4" + i, rpi4.iaas, true);
            new EnergyDataCollector("RPi5" + i, rpi5.iaas, true);
            new EnergyDataCollector("RPi6" + i, rpi6.iaas, true);
            new EnergyDataCollector("RPi7" + i, rpi7.iaas, true);
            new EnergyDataCollector("RPi8" + i, rpi8.iaas, true);
            new EnergyDataCollector("RPi9" + i, rpi9.iaas, true);
            new EnergyDataCollector("RPi10" + i, rpi10.iaas, true);
       }

       ComputingAppliance node1 = new ComputingAppliance(
           createNode("Node1", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 35, 200, 3550, 100_000, 15, sharedLatencyMap),
           new GeoLocation(59.33, 18.07), "EU", "Azure", false);

       ComputingAppliance node2 = new ComputingAppliance(
           createNode("Node2", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 30, 150, 2200, 37_500, 70, sharedLatencyMap),
           new GeoLocation(53.33, -6.25), "EU", "AWS", false); // San Francisco

       ComputingAppliance node3 = new ComputingAppliance(
           createNode("Node3", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 40, 200, 3500, 150_000, 60, sharedLatencyMap),
           new GeoLocation(51.51, -0.13), "EU", "Azure", false); // Chicago

       ComputingAppliance node4 = new ComputingAppliance(
           createNode("Node4", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 35, 175, 3550, 100_000, 15, sharedLatencyMap),
           new GeoLocation(48.86, 2.35), "EU", "AWS", false); // Los Angeles

       ComputingAppliance node5 = new ComputingAppliance(
           createNode("Node5", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 30, 150, 2200, 37_500, 70, sharedLatencyMap),
           new GeoLocation(50.11, 8.68), "EU", "Azure", false); // San Francisco

       ComputingAppliance node6 = new ComputingAppliance(
           createNode("Node6", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 40, 200, 3500, 150_000, 60, sharedLatencyMap),
           new GeoLocation(45.46, 9.19), "EU", "AWS", false); // Chicago

       ComputingAppliance node7 = new ComputingAppliance(
           createNode("Node7", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 35, 175, 3550, 100_000, 15, sharedLatencyMap),
           new GeoLocation(41.39, 2.17), "EU", "Azure", false); // Los Angeles

       ComputingAppliance node8 = new ComputingAppliance(
           createNode("Node8", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 30, 150, 2200, 37_500, 70, sharedLatencyMap),
           new GeoLocation(52.37, 4.90), "EU", "AWS", false); // San Francisco

       ComputingAppliance node9 = new ComputingAppliance(
           createNode("Node9", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 40, 200, 3500, 150_000, 60, sharedLatencyMap),
           new GeoLocation(39.04, -77.49), "US", "Azure", false); // Chicago

       ComputingAppliance node10 = new ComputingAppliance(
           createNode("Node10", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 35, 175, 3550, 100_000, 15, sharedLatencyMap),
           new GeoLocation(37.34, -121.89), "US", "AWS", false); // Los Angeles

       new EnergyDataCollector("Node1", node1.iaas, true);
       new EnergyDataCollector("Node2", node2.iaas, true);
       new EnergyDataCollector("Node3", node3.iaas, true);
       new EnergyDataCollector("Node4", node4.iaas, true);
       new EnergyDataCollector("Node5", node5.iaas, true);
       new EnergyDataCollector("Node6", node6.iaas, true);
       new EnergyDataCollector("Node7", node7.iaas, true);
       new EnergyDataCollector("Node8", node8.iaas, true);
       new EnergyDataCollector("Node9", node9.iaas, true);
       new EnergyDataCollector("Node10", node10.iaas, true);

       /** agents */
       VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L);
       AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);

       Map<String, String> mapping = new HashMap<>();

       ResourceAgent ra0 =
               new ResourceAgent("Agent0", 0.00002778, resourceAgentVa, resourceAgentArc, new DirectMappingAgentStrategy(mapping), new GuidedSearchMessagingStrategy());

        for(int i = 1; i <= numOfApps; i++) {
        	mapping.put("UNC-" + i + "-Res-1", "Agent0");
        	mapping.put("UNC-" + i + "-Res-2", "Agent0");
        	mapping.put("UNC-" + i + "-Res-3", "Agent0");
        	mapping.put("UNC-" + i + "-Res-4", "Agent0");
        	mapping.put("UNC-" + i + "-Res-5", "Agent0");
        	mapping.put("UNC-" + i + "-Res-6", "Agent0");
        	mapping.put("UNC-" + i + "-Res-7", "Agent0");
        	mapping.put("UNC-" + i + "-Res-8", "Agent0");
        	mapping.put("UNC-" + i + "-Res-9", "Agent0");
        	mapping.put("UNC-" + i + "-Res-10", "Agent0");

        	ra0.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("RPi1" + i), 5, 8 * 1_073_741_824L, 256 * 1_073_741_824L));
        	ra0.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("RPi2" + i), 5, 8 * 1_073_741_824L, 256 * 1_073_741_824L));
        	ra0.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("RPi3" + i), 5, 8 * 1_073_741_824L, 256 * 1_073_741_824L));
        	ra0.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("RPi4" + i), 5, 8 * 1_073_741_824L, 256 * 1_073_741_824L));
        	ra0.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("RPi5" + i), 5, 8 * 1_073_741_824L, 256 * 1_073_741_824L));
        	ra0.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("RPi6" + i), 5, 8 * 1_073_741_824L, 256 * 1_073_741_824L));
        	ra0.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("RPi7" + i), 5, 8 * 1_073_741_824L, 256 * 1_073_741_824L));
        	ra0.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("RPi8" + i), 5, 8 * 1_073_741_824L, 256 * 1_073_741_824L));
        	ra0.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("RPi9" + i), 5, 8 * 1_073_741_824L, 256 * 1_073_741_824L));
        	ra0.registerCapacity(new Capacity(ComputingAppliance.findApplianceByName("RPi10" + i), 5, 8 * 1_073_741_824L, 256 * 1_073_741_824L));
        }

        ra0.initResourceAgent(resourceAgentVa, resourceAgentArc);

        new ResourceAgent("Agent1", 0.00013889, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true), new GuidedSearchMessagingStrategy(),
                new Capacity(node1, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        new ResourceAgent("Agent2", 0.00277778, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false), new GuidedSearchMessagingStrategy(),
                new Capacity(node2, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        new ResourceAgent("Agent3", 0.00041667, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true), new GuidedSearchMessagingStrategy(),
                new Capacity(node3, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        new ResourceAgent("Agent4", 0.00000278, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false), new GuidedSearchMessagingStrategy(),
                new Capacity(node4, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        new ResourceAgent("Agent5", 0.00005556, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true), new GuidedSearchMessagingStrategy(),
                new Capacity(node5, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        new ResourceAgent("Agent6", 0.00013889, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true), new GuidedSearchMessagingStrategy(),
                new Capacity(node6, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        new ResourceAgent("Agent7", 0.00277778, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false), new GuidedSearchMessagingStrategy(),
                new Capacity(node7, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        new ResourceAgent("Agent8", 0.00041667, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true), new GuidedSearchMessagingStrategy(),
                new Capacity(node8, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        new ResourceAgent("Agent9", 0.00000278, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false), new GuidedSearchMessagingStrategy(),
                new Capacity(node9, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        new ResourceAgent("Agent10", 0.00005556, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true), new GuidedSearchMessagingStrategy(),
                new Capacity(node10, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        /** Image service */
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(1, 1, 1, 1, 1);

        Deployment.registryService = new Repository(Long.MAX_VALUE, "Image_Service", 125_000, 125_000, 125_000, sharedLatencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));

        Deployment.setImageRegistry(Deployment.registryService);

        /** submitting applications */
        List<Path> appFiles = Files.list(inputDir)
                .filter(f -> f.toString().endsWith(".json"))
                .collect(Collectors.toList());

        int i = 0;
        int[] delay = {0}; // submission delay
        //int[] delay = {0, 0, 0, 60, 60, 120, 150, 150, 150, 150};

        for (Path file : appFiles) {
            new DeferredEvent(delay[i] * 60 * 1000) {

                @Override
                protected void eventAction() {
                    new Submission(file.toString(), 2048, 0, configuration);
                }
            };
        }
        
        Sun.init(6, 20, 13, 1.5);
        CsvExporter csvExporter = new CsvExporter(Sun.getInstance());
        long starttime = System.nanoTime();       
        Timed.simulateUntil(simLength);
        long stoptime = System.nanoTime();
        csvExporter.visualise();
        
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
        
        SimLogger.logRes("Time above the temperature threshold (%): " 
                + AgentTestUNC.calculateTimeBelowThrottling(csvExporter.noiseSensorTemperature.toPath(), configuration.get("cpuTempTreshold").doubleValue()));

        SimLogger.logRes("Average time to transfer a file over the network (sec.): " + (NoiseSensor.timeOnNetwork / 1000.0) / NoiseSensor.generatedFiles);	
        
        SimLogger.logRes("Number of sound events (pc.): " + NoiseSensor.generatedFiles);
        
        SimLogger.logRes("Number of offloaded sound events (pc.): " + NoiseSensor.offloadedFiles);
        
        SimLogger.logRes("Number of sound events requiring processing (pc.): " + NoiseSensor.soundEventsReqProcessing);
        
        SimLogger.logRes("Number of processed files (pc.): " + NoiseSensor.processedFiles);
        
        long soundFilesNs = 0;
        // long soundFilesRs = 0;
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
                	/*
                    RemoteServer rs = (RemoteServer) o;
                    System.out.println("RS:");
                    System.out.println(rs.pm.localDisk.contents().size()); 
                    for (StorageObject so : rs.pm.localDisk.contents()) {
                        if(so.id.contains("Noise-Sensor")) {
                            soundFilesRs++;
                        }
                    }
                    */
                }
            }
        }
        
        SimLogger.logRes("Number of sound files on noise sensors: " + soundFilesNs);
        //SimLogger.logRes("Number of sound files on the remote servers: " + soundFilesRs);
        
        SimLogger.logRes("Runtime (seconds): " + TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));
        /*
        long usedStorage = 0;
        int files = 0;
        Repository r = null;
        for (SwarmAgent sa : SwarmAgent.allSwarmAgents) {
        	for (Object o : sa.components) {
        		if (o instanceof RemoteServer) {
        			RemoteServer rs = (RemoteServer) o;
        			r = rs.pm.localDisk;
        			for (StorageObject so : rs.pm.localDisk.contents()) {
        				if (so.id.contains("Noise-Sensor")) {
        					usedStorage += so.size;
        					files++;
        				}
        			}
        		}
        	}
        }
        
        System.out.println(usedStorage + " " + files + " " + usedStorage / files);
        System.out.println("used: " + (r.getMaxStorageCapacity()-r.getFreeStorageCapacity()));
        System.out.println((r.getMaxStorageCapacity()-r.getFreeStorageCapacity())-usedStorage);
        for(StorageObject so : r.contents()) {
        	if(!so.id.contains("Noise-Sensor")) {
        		System.out.println(so);
        	}
        }
     
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
    
    private static double calculateTimeBelowThrottling(Path path, double cpuThreshold) {
    	List<String> lines;
		try {
			lines = java.nio.file.Files.readAllLines(path);
			String[] header = lines.get(0).split(Character.toString(','));
	    	
	    	int n = header.length - 1; 
	    	String[] deviceNames = java.util.Arrays.copyOfRange(header, 1, header.length);
	    	
	    	long[] total = new long[n];
	    	long[] below = new long[n];
	    	
	    	for (int li = 1; li < lines.size(); li++) {
	    	    String[] p = lines.get(li).split(Character.toString(','));
	    	    for (int i = 0; i < n; i++) {
	    	        double v = Double.parseDouble(p[i + 1].trim());
	    	        total[i]++;
	    	        if (v < cpuThreshold) below[i]++;
	    	    }
	    	}
	    	
	    	Map<String, Double> percentPerDevice = new LinkedHashMap<>();
	    	double sum = 0.0;

	    	for (int i = 0; i < n; i++) {
	    	    double pct = 100.0 * below[i] / total[i];
	    	    percentPerDevice.put(deviceNames[i], pct); 
	    	    sum += pct;
	    	}

	    	return (sum / n);
		} catch (IOException e) {
			e.printStackTrace();
		}	
		return -1;
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
    
    File fileSunIntensity;
    
    File avgCpuLoad;
    
    File noOfNoiseSensorClassifiers;
    
    File noiseSensorTemperature;
    
    File noOfFilesToProcess;
    
    File noOfFileMigrations;
        
    public CsvExporter(Sun sun) {
    	this.fileSunIntensity = new File(ScenarioBase.resultDirectory + "/sun-intensity.csv");
    	this.avgCpuLoad = new File(ScenarioBase.resultDirectory + "/avg-cpu-load.csv");
    	this.noOfNoiseSensorClassifiers = new File(ScenarioBase.resultDirectory + "/no-of-noise-sensor-classifiers.csv");
    	this.noiseSensorTemperature = new File(ScenarioBase.resultDirectory + "/noise-sensor-temperature.csv");
    	this.noOfFilesToProcess = new File(ScenarioBase.resultDirectory + "/no-of-files-to-process.csv");
    	this.noOfFileMigrations = new File(ScenarioBase.resultDirectory + "/no-of-file-migrations.csv");
    	
        this.sun = sun;
        subscribe(10_000);
    }
    
    public void visualise() {
		try {
			Path csv = Paths.get(this.avgCpuLoad.getAbsolutePath());
	        String content;
			content = new String(Files.readAllBytes(csv), StandardCharsets.UTF_8);
			String header = "time,avg-cpu-load";
			String newContent = header + System.lineSeparator() + content;
			Files.write(csv, newContent.getBytes(StandardCharsets.UTF_8));
			
			csv = Paths.get(this.noOfNoiseSensorClassifiers.getAbsolutePath());
			content = new String(Files.readAllBytes(csv), StandardCharsets.UTF_8);
			header = "time,no-of-classifiers";
			newContent = header + System.lineSeparator() + content;
			Files.write(csv, newContent.getBytes(StandardCharsets.UTF_8));
			
			csv = Paths.get(this.noOfFilesToProcess.getAbsolutePath());
			content = new String(Files.readAllBytes(csv), StandardCharsets.UTF_8);
			header = "time,no-of-files-to-process";
			newContent = header + System.lineSeparator() + content;
			Files.write(csv, newContent.getBytes(StandardCharsets.UTF_8));
			
			csv = Paths.get(this.noOfFileMigrations.getAbsolutePath());
			content = new String(Files.readAllBytes(csv), StandardCharsets.UTF_8);
			header = "time,no-of-file-migrations";
			newContent = header + System.lineSeparator() + content;
			Files.write(csv, newContent.getBytes(StandardCharsets.UTF_8));
			
			csv = Paths.get(this.noiseSensorTemperature.getAbsolutePath());
			content = new String(Files.readAllBytes(csv), StandardCharsets.UTF_8);

			List<String> names = new ArrayList<>();
			for (Object o : SwarmAgent.allSwarmAgents.get(0).components) {
			    if (o instanceof NoiseSensor) {
			        NoiseSensor ns = (NoiseSensor) o;
			        names.add(SwarmAgent.allSwarmAgents.get(0).app.getComponentName(ns.util.resource.name)); 
			    }
			}

			header = "time";
			if (!names.isEmpty()) {
			    header += "," + String.join(",", names);
			}

			newContent = header + System.lineSeparator() + content;
			Files.write(csv, newContent.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
		}        
        
    	AgentVisualiser.visualise(fileSunIntensity.toPath(), avgCpuLoad.toPath(), noOfNoiseSensorClassifiers.toPath(),
    			this.noiseSensorTemperature.toPath(), this.noOfFilesToProcess.toPath(), this.noOfFileMigrations.toPath());
    }

    @Override
    public void tick(long fires) {
    	double time = Timed.getFireCount() / 1000.0 / 60.0 / 60.0;
    	
    	// sun intensity
    	try (PrintWriter writer = new PrintWriter(new FileWriter(fileSunIntensity.getAbsolutePath(), true))) {
    	    if (fileSunIntensity.length() == 0) {
    	        writer.println("time,sun_intensity"); 
    	    }
    	    
            StringBuilder row = new StringBuilder();
            row.append(String.format(Locale.ROOT, "%.3f", time));
            row.append(",");
            row.append(String.format(Locale.ROOT, "%.3f", sun.getSunStrength())); 
            writer.println(row.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    	
    	if (SwarmAgent.allSwarmAgents.size() > 0) {
    		SwarmAgent sa = SwarmAgent.allSwarmAgents.get(0);
    		
    		// avg cpu load
        	try (PrintWriter writer = new PrintWriter(new FileWriter(avgCpuLoad.getAbsolutePath(), true))) {
        			StringBuilder row = new StringBuilder();
                    row.append(String.format(Locale.ROOT, "%.3f", time));
                    row.append(",");
        			row.append(String.format(Locale.ROOT, "%.3f", sa.avgCpu()));
                    writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        	
        	// noise sensor classifier count
        	try (PrintWriter writer = new PrintWriter(new FileWriter(noOfNoiseSensorClassifiers.getAbsolutePath(), true))) {
        		StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.ROOT, "%.3f", time));
        		row.append(",");
        		row.append(String.format(Locale.ROOT, "%d", sa.noiseSensorsWithClassifier.size()));
                writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        	
        	// no. of files to process
        	try (PrintWriter writer = new PrintWriter(new FileWriter(noOfFilesToProcess.getAbsolutePath(), true))) {
        			StringBuilder row = new StringBuilder();
                    row.append(String.format(Locale.ROOT, "%.3f", time));
                    row.append(",");
        			row.append(String.format(Locale.ROOT, "%d", sa.noOfFilesToProcess()));
                    writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        	
        	// no. of file migrations
        	try (PrintWriter writer = new PrintWriter(new FileWriter(noOfFileMigrations.getAbsolutePath(), true))) {
        			StringBuilder row = new StringBuilder();
                    row.append(String.format(Locale.ROOT, "%.3f", time));
                    row.append(",");
                    int i = 0;
                    for (Object o : sa.components) {
                        if (o instanceof NoiseSensor) {
                            NoiseSensor ns = (NoiseSensor) o;
                            i += ns.underMigration;
                        }
                    }
                    row.append(String.format(Locale.ROOT, "%d", i));
                    writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        	
        	// noise sensor temperature
        	try (PrintWriter writer = new PrintWriter(new FileWriter(noiseSensorTemperature.getAbsolutePath(), true))) {
        			StringBuilder row = new StringBuilder();
                    row.append(String.format(Locale.ROOT, "%.3f", time));
                    	for (Object o : sa.components) {
                            if (o instanceof NoiseSensor) {
                                NoiseSensor ns = (NoiseSensor) o;
                                row.append(",");
                                row.append(String.format(Locale.ROOT, "%.3f", ns.cpuTemp));
                            }
                        }
                    writer.println(row.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
    	}
    }
}