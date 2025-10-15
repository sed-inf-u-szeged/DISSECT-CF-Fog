package hu.u_szeged.inf.fog.simulator.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
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
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.Deployment;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.Submission;
import hu.u_szeged.inf.fog.simulator.agent.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.decision.CentralizedAntBasedDecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.strategy.FirstFitAgentStrategy;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.RemoteServer;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.Sun;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.NoiseAppCsvExporter;

public class AgentTestUNC {

    public static void main(String[] args) throws IOException {
        
        SimLogger.setLogging(1, true);
        SeedSyncer.modifySeed(9876543210L);
        
        /** general config */
        long simLength = 1 * 24 * 60 * 60 * 1000L; 
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
        	configuration.put("lengthOfProcessing", 1_700); // ms

        Path inputDir = Paths.get(ScenarioBase.resourcePath + "AGENT_examples");
        // Path inputDir = Paths.get(ScenarioBase.resourcePath + "AGENT_examples3");

        /** ranking config */
        // ResourceAgent.rankingScriptDir = "D:\\Documents\\swarm-deployment\\for_simulator";
        //ResourceAgent.rankingScriptDir = "/home/markus/Documents/projects/swarm-deployment/for_simulator";
        ResourceAgent.rankingScriptDir = "C:\\Users\\schwa\\Szakdolgozat\\swarm-deployment-main\\for_simulator";
                
        ResourceAgent.rankingMethodName = "rank_no_re";
        // ResourceAgent.rankingMethodName = "rank_re_add";
        // ResourceAgent.rankingMethodName = "rank_re_mul";
        // ResourceAgent.rankingMethodName = "vote_wo_reliability";
        // ResourceAgent.rankingMethodName = "vote_w_reliability";
        // ResourceAgent.rankingMethodName = "vote_w_reliability_mul";
        // ResourceAgent.rankingMethodName = "random";

        /** nodes and RPis */
        Map<String, Integer> sharedLatencyMap = new HashMap<>();

        ComputingAppliance node1 = new ComputingAppliance(
                createNode("Node1", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 35, 200, 3550, 100_000, 15, sharedLatencyMap),
                new GeoLocation(59.33, 18.07), "EU", "Azure", true);

        ComputingAppliance node2 = new ComputingAppliance(
                createNode("Node2", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 30, 150, 2200, 37_500, 70, sharedLatencyMap),
                new GeoLocation(53.33, -6.25), "EU", "AWS", true); // San Francisco

        ComputingAppliance node3 = new ComputingAppliance(
                createNode("Node3", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 40, 200, 3500, 150_000, 60, sharedLatencyMap),
                new GeoLocation(51.51, -0.13), "EU", "Azure", true); // Chicago

        ComputingAppliance node4 = new ComputingAppliance(
                createNode("Node4", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 35, 175, 3550, 100_000, 15, sharedLatencyMap),
                new GeoLocation(48.86, 2.35), "EU", "AWS", true); // Los Angeles

        ComputingAppliance node5 = new ComputingAppliance(
                createNode("Node5", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 30, 150, 2200, 37_500, 70, sharedLatencyMap),
                new GeoLocation(50.11, 8.68), "US", "Azure", true); // San Francisco

        ComputingAppliance node6 = new ComputingAppliance(
                createNode("Node6", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 40, 200, 3500, 150_000, 60, sharedLatencyMap),
                new GeoLocation(45.46, 9.19), "EU", "AWS", true); // Chicago

        ComputingAppliance node7 = new ComputingAppliance(
                createNode("Node7", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 35, 175, 3550, 100_000, 15, sharedLatencyMap),
                new GeoLocation(41.39, 2.17), "EU", "Azure", true); // Los Angeles

        ComputingAppliance node8 = new ComputingAppliance(
                createNode("Node8", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 30, 150, 2200, 37_500, 70, sharedLatencyMap),
                new GeoLocation(52.37, 4.90), "EU", "AWS", true); // San Francisco

        ComputingAppliance node9 = new ComputingAppliance(
                createNode("Node9", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 40, 200, 3500, 150_000, 60, sharedLatencyMap),
                new GeoLocation(39.04, -77.49), "US", "Azure", true); // Chicago

        ComputingAppliance node10 = new ComputingAppliance(
                createNode("Node10", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 35, 175, 1200, 100_000, 15, sharedLatencyMap),
                new GeoLocation(37.34, -121.89), "US", "AWS", true); // Los Angeles

        ComputingAppliance node11 = new ComputingAppliance(
                createNode("Node11", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 35, 175, 3550, 37_500, 70, sharedLatencyMap),
                new GeoLocation(37.34, -121.89), "US", "", true); // Los Angeles?

        ComputingAppliance node12 = new ComputingAppliance(
                createNode("Node12", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 35, 175, 2200, 150_000, 30, sharedLatencyMap),
                new GeoLocation(38.34, -120.89), "US", "", true); // Los Angeles?

        ComputingAppliance node13 = new ComputingAppliance(
                createNode("Node13", 256, 1, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L, 35, 175, 4000, 100_000, 15, sharedLatencyMap),
                new GeoLocation(39.34, -122.89), "US", "Azure", true); // Los Angeles?


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
        new EnergyDataCollector("Node11", node11.iaas, true);
        new EnergyDataCollector("Node12", node12.iaas, true);
        new EnergyDataCollector("Node13", node13.iaas, true);

        /** agents */
        VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L);
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);

        ResourceAgent ra1 = new ResourceAgent("Agent1", 0.00013889, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(node1, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        ResourceAgent ra2 = new ResourceAgent("Agent2", 0.00277778, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(node2, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        ResourceAgent ra3 = new ResourceAgent("Agent3", 0.00041667, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(node3, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        ResourceAgent ra4 = new ResourceAgent("Agent4", 0.00000278, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(node4, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        ResourceAgent ra5 = new ResourceAgent("Agent5", 0.00005556, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(node5, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        ResourceAgent ra6 = new ResourceAgent("Agent6", 0.00026889, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(node6, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        ResourceAgent ra7 = new ResourceAgent("Agent7", 0.00527778, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(node7, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        ResourceAgent ra8 = new ResourceAgent("Agent8", 0.00001667, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(node8, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        ResourceAgent ra9 = new ResourceAgent("Agent9", 0.00000278, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(node9, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        ResourceAgent ra10 = new ResourceAgent("Agent10", 0.00005556, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(node10, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        ResourceAgent ra11 = new ResourceAgent("Agent11", 0.00006656, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(node11, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        ResourceAgent ra12 = new ResourceAgent("Agent12", 0.00000556, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(node12, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        ResourceAgent ra13 = new ResourceAgent("Agent13", 0.00800056, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(node13, 256, 256 * 1_073_741_824L, numOfApps * 256 * 1_073_741_824L));

        /** Image service */
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(1, 1, 1, 1, 1);
        
        Deployment.registryService = new Repository(Long.MAX_VALUE, "Image_Service", 125_000, 125_000, 125_000, sharedLatencyMap, 
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage), 
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));
        
        Deployment.setImageRegistry(Deployment.registryService);

        //------------------------

        ArrayList<Map.Entry<ComputingAppliance, ResourceAgent>> nodesToBeClustered = new ArrayList<>();

        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node1, ra1));
        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node2, ra2));
        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node3, ra3));
        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node4, ra4));
        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node5, ra5));
        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node6, ra6));
        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node7, ra7));
        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node8, ra8));
        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node9, ra9));
        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node10, ra10));
        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node11, ra11));
        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node12, ra12));
        nodesToBeClustered.add(new AbstractMap.SimpleEntry<>(node13, ra13));

        //------------------------

        /** submitting applications */
        List<Path> appFiles = Files.list(inputDir)
                .filter(f -> f.toString().endsWith(".json"))
                .collect(Collectors.toList());

        int i = 0;
        int[] delay = {0}; // submission delay
        //int[] delay = {0, 0, 0, 60, 60, 120, 150, 150, 150, 150}; 

        for (Path file : appFiles) {
            new DeferredEvent(delay[i++] * 60 * 1000) {

                @Override
                protected void eventAction() {
                    //new Submission(file.toString(), 2048, 0, configuration, new BroadcastBasedDecisionMaker());
                    new Submission(file.toString(), 2048, 0, configuration, new CentralizedAntBasedDecisionMaker(4, nodesToBeClustered, 50, 200, 0.5, 0.2, 0.15, 0.3));
                }
            };
        }
        
        Sun.init(6, 20, 13, 1.5);
        NoiseAppCsvExporter csvExporter = new NoiseAppCsvExporter(Sun.getInstance());
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

        DecimalFormat df = new DecimalFormat("#.####");

        double totalEnergy = 0;
        for (EnergyDataCollector ec : EnergyDataCollector.energyCollectors) {
            totalEnergy += ec.energyConsumption / 1000 / 3_600_000;
        }
        EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);
              
        long soundFilesNs = 0;
        long soundFilesRs = 0;
        double avgDeploymentTime = 0.0;
        double avgOffers = 0.0;
        
        for(SwarmAgent sa : SwarmAgent.allSwarmAgents) {
            SimLogger.logRes(sa.app.name + " deployment: ");
            if (sa.app.deploymentTime != -1) {
                avgDeploymentTime += sa.app.deploymentTime;
                SimLogger.logRes("\tTime (min.): " + df.format(sa.app.deploymentTime / 1000 / 60));
            } else {
                SimLogger.logRes("\tTime (min.): -1");
            }
            SimLogger.logRes("\tAvailable offers: " + sa.app.offers.size());
            avgOffers += sa.app.offers.size();
            if(sa.app.offers.size() > 0) {
                StringBuilder str = new StringBuilder();
                for(ResourceAgent ra : sa.app.offers.get((sa.app.winningOffer)).agentResourcesMap.keySet()) {
                    str.append(ra.name + " ");
                }
                SimLogger.logRes("\tWinning offer: " + sa.app.offers.get((sa.app.winningOffer)).id + " ( " + str.toString() + ")");
            }
            
            StorageObject resFile = null;      
        	for(Object o : sa.components) {
                if (o.getClass().equals(NoiseSensor.class)) {
                    NoiseSensor ns = (NoiseSensor) o;
                    SimLogger.logRes("\t" + sa.app.getComponentName(ns.util.resource.name) + " is inside: " 
                            + ns.inside + ", exposed to sunlight: " + ns.sunExposed);
                    for (StorageObject so : ns.pm.localDisk.contents()) {
                        if(so.id.contains("Noise-Sensor")) {
                            soundFilesNs++;
                        }
                    }
                } else {
                    RemoteServer rs = (RemoteServer) o;
                    for (StorageObject so : rs.pm.localDisk.contents()) {
                      if (so.id.equals(sa.app.name)) {
                          resFile = so;
                      }
                    }
                }
            }
        	soundFilesRs += resFile.size / sa.app.configuration.get("resFileSize").longValue();
        }
        
        SimLogger.logRes("\nSimulation time (min.): " + df.format(Timed.getFireCount() / 1000.0 / 60.0));
        SimLogger.logRes("Total price (EUR): " + df.format(totalCost));
        SimLogger.logRes("Total energy (kWh): " + df.format(totalEnergy));
        SimLogger.logRes("Size of generated files (MB): " + NoiseSensor.totalGeneratedFileSize / 1_048_576);
        SimLogger.logRes("Number of sound events (pc.): " + NoiseSensor.totalGeneratedFiles);
        SimLogger.logRes("Number of offloaded sound events (pc.): " + NoiseSensor.totalOffloadedFiles);
        SimLogger.logRes("Number of sound events requiring processing (pc.): " + NoiseSensor.totalSoundEventsToProcess);
        SimLogger.logRes("Number of processed files (pc.): " + NoiseSensor.totalProcessedFiles);
        SimLogger.logRes("Average deployment time (min.): " + df.format(avgDeploymentTime / AgentApplication.agentApplications.size() / 1000 / 60));
        SimLogger.logRes("Average number of offers (pc.): " + df.format(avgOffers / AgentApplication.agentApplications.size()));
        SimLogger.logRes("Number of sound files on noise sensors: " + soundFilesNs);
        SimLogger.logRes("Number of sound files on the remote servers: " + soundFilesRs);
        SimLogger.logRes("Time below the temperature threshold (%): " 
                + df.format(AgentTestUNC.calculateTimeBelowThrottling(csvExporter.noiseSensorTemperature.toPath(), 
                        configuration.get("cpuTempTreshold").doubleValue())));

        SimLogger.logRes("Average time to transfer a file over the network (sec.): " + df.format(NoiseSensor.totalTimeOnNetwork / 1000.0 / soundFilesRs));  
        SimLogger.logRes("Runtime (seconds): " + TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));
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