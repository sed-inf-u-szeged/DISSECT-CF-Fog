package hu.u_szeged.inf.fog.simulator.demo;

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
import hu.u_szeged.inf.fog.simulator.agent.*;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.agentstrategy.FirstFitAgentStrategy;
import hu.u_szeged.inf.fog.simulator.agent.decision.BroadcastBasedDecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.decision.CentralizedAntBasedDecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.decision.DecentralizedAntBasedDecisionMaker;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.RemoteServer;
import hu.u_szeged.inf.fog.simulator.agent.urbannoise.Sun;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.NoiseAppCsvExporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ACOsTests {

    public static void main(String[] args) throws IOException {
        
        SimLogger.setLogging(1, true);
        //SeedSyncer.modifySeed(9876543210L);
        //SeedSyncer.modifySeed(2169545184L);

        //SeedSyncer.modifySeed(8974562); //=> D.ACO one seed where it doesn't make only one cluster SOMETIMES
        SeedSyncer.modifySeed(8); //=> D.ACO one seed where it doesn't make only one cluster SOMETIMES
        
        /** general config */
        long simLength = 1 * 24 * 60 * 60 * 1000L;

        /** app config */
        HashMap<String, Number> configuration = new HashMap<>();
        	configuration.put("samplingFreq", 10_000);   // 10 sec.
        	configuration.put("soundFileSize", 655_360); // 640 kB
        	configuration.put("resFileSize", 1024);      // 1 kB
        	configuration.put("minSoundLevel", 30);		 // dB
        	configuration.put("maxSoundLevel", 130);	 // dB
        	configuration.put("soundTreshold", 0);		 // dB
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
        //ResourceAgent.rankingScriptDir = "/home/markusa/Documents/SZTE/repos/swarm-deployment/for_simulator";
        StandardResourceAgent.rankingScriptDir = "C:\\Users\\schwa\\Szakdolgozat\\swarm-deployment-main\\for_simulator";

        StandardResourceAgent.rankingMethodName = "rank_no_re";
        // ResourceAgent.rankingMethodName = "rank_re_add";
        // ResourceAgent.rankingMethodName = "rank_re_mul";
        // ResourceAgent.rankingMethodName = "vote_wo_reliability";
        // ResourceAgent.rankingMethodName = "vote_w_reliability";
        // ResourceAgent.rankingMethodName = "vote_w_reliability_mul";
        // ResourceAgent.rankingMethodName = "random";

        /** nodes and RPis */
        Map<String, Integer> sharedLatencyMap = new HashMap<>();
        ArrayList<StandardResourceAgent> agentsToBeClustered = new ArrayList<>();

        VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L);
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);

        /** Cloud and Edge nodes */
        // ===================== CLOUD =====================
        // High-end cloud (jó, de drága)
        ComputingAppliance node1 = new ComputingAppliance(
                createNode("Node1",
                        128, 1,
                        512 * 1_073_741_824L,
                        4096 * 1_073_741_824L,
                        40, 180, 1400,
                        190, 55,
                        sharedLatencyMap),
                new GeoLocation(48.1351, 11.5820),
                "EU",
                "AWS",
                true);

        StandardResourceAgent ra1 = new StandardResourceAgent(
                "Agent1", 0.78,
                resourceAgentVa, resourceAgentArc,
                new FirstFitAgentStrategy(true),
                new Capacity(node1,
                        128,
                        512 * 1_073_741_824L,
                        4096 * 1_073_741_824L));

        agentsToBeClustered.add(ra1);

        // Mid cloud (átlagos)
        ComputingAppliance node2 = new ComputingAppliance(
                createNode("Node2",
                        64, 1,
                        256 * 1_073_741_824L,
                        2048 * 1_073_741_824L,
                        38, 175, 2600,
                        140, 85,
                        sharedLatencyMap),
                new GeoLocation(39.9042, 116.4074),
                "Asia",
                "AlibabaCloud",
                true);

        StandardResourceAgent ra2 = new StandardResourceAgent(
                "Agent2", 0.52,
                resourceAgentVa, resourceAgentArc,
                new FirstFitAgentStrategy(true),
                new Capacity(node2,
                        64,
                        256 * 1_073_741_824L,
                        2048 * 1_073_741_824L));

        agentsToBeClustered.add(ra2);

        // ===================== EDGE =====================
        // erős edge (jó)
        ComputingAppliance node3 = new ComputingAppliance(
                createNode("Node3",
                        32, 1,
                        64 * 1_073_741_824L,
                        512 * 1_073_741_824L,
                        35, 170, 1200,
                        160, 60,
                        sharedLatencyMap),
                new GeoLocation(52.5200, 13.4050),
                "EU",
                "SiemensEdge",
                true);

        StandardResourceAgent ra3 = new StandardResourceAgent(
                "Agent3", 0.33,
                resourceAgentVa, resourceAgentArc,
                new FirstFitAgentStrategy(true),
                new Capacity(node3,
                        32,
                        64 * 1_073_741_824L,
                        512 * 1_073_741_824L));

        agentsToBeClustered.add(ra3);

        // átlagos edge
        ComputingAppliance node4 = new ComputingAppliance(
                createNode("Node4",
                        8, 1,
                        16 * 1_073_741_824L,
                        128 * 1_073_741_824L,
                        30, 160, 2200,
                        100, 110,
                        sharedLatencyMap),
                new GeoLocation(34.6937, 135.5023),
                "Asia",
                "EdgeJapan",
                true);

        StandardResourceAgent ra4 = new StandardResourceAgent(
                "Agent4", 0.18,
                resourceAgentVa, resourceAgentArc,
                new FirstFitAgentStrategy(true),
                new Capacity(node4,
                        8,
                        16 * 1_073_741_824L,
                        128 * 1_073_741_824L));

        agentsToBeClustered.add(ra4);

        // gyenge edge
        ComputingAppliance node5 = new ComputingAppliance(
                createNode("Node5",
                        2, 1,
                        4 * 1_073_741_824L,
                        64 * 1_073_741_824L,
                        25, 150, 3200,
                        40, 190,
                        sharedLatencyMap),
                new GeoLocation(45.4642, 9.1900),
                "EU",
                "LocalEdge",
                true);

        StandardResourceAgent ra5 = new StandardResourceAgent(
                "Agent5", 0.09,
                resourceAgentVa, resourceAgentArc,
                new FirstFitAgentStrategy(true),
                new Capacity(node5,
                        2,
                        4 * 1_073_741_824L,
                        64 * 1_073_741_824L));

        agentsToBeClustered.add(ra5);

        // jó edge
        ComputingAppliance node6 = new ComputingAppliance(
                createNode("Node6",
                        16, 1,
                        32 * 1_073_741_824L,
                        256 * 1_073_741_824L,
                        37, 178, 1500,
                        150, 75,
                        sharedLatencyMap),
                new GeoLocation(37.7749, -122.4194),
                "US",
                "CiscoEdge",
                true);

        StandardResourceAgent ra6 = new StandardResourceAgent(
                "Agent6", 0.29,
                resourceAgentVa, resourceAgentArc,
                new FirstFitAgentStrategy(true),
                new Capacity(node6,
                        16,
                        32 * 1_073_741_824L,
                        256 * 1_073_741_824L));

        agentsToBeClustered.add(ra6);

        // rosszabb edge
        ComputingAppliance node7 = new ComputingAppliance(
                createNode("Node7",
                        4, 1,
                        8 * 1_073_741_824L,
                        128 * 1_073_741_824L,
                        27, 155, 2800,
                        60, 170,
                        sharedLatencyMap),
                new GeoLocation(50.0755, 14.4378),
                "EU",
                "FogNode",
                true);

        StandardResourceAgent ra7 = new StandardResourceAgent(
                "Agent7", 0.13,
                resourceAgentVa, resourceAgentArc,
                new FirstFitAgentStrategy(true),
                new Capacity(node7,
                        4,
                        8 * 1_073_741_824L,
                        128 * 1_073_741_824L));

        agentsToBeClustered.add(ra7);

        // erős edge
        ComputingAppliance node8 = new ComputingAppliance(
                createNode("Node8",
                        32, 1,
                        64 * 1_073_741_824L,
                        512 * 1_073_741_824L,
                        41, 190, 1300,
                        180, 65,
                        sharedLatencyMap),
                new GeoLocation(40.7128, -74.0060),
                "US",
                "GoogleEdge",
                true);

        StandardResourceAgent ra8 = new StandardResourceAgent(
                "Agent8", 0.36,
                resourceAgentVa, resourceAgentArc,
                new FirstFitAgentStrategy(true),
                new Capacity(node8,
                        32,
                        64 * 1_073_741_824L,
                        512 * 1_073_741_824L));

        agentsToBeClustered.add(ra8);

        // átlagos edge
        ComputingAppliance node9 = new ComputingAppliance(
                createNode("Node9",
                        8, 1,
                        8 * 1_073_741_824L,
                        128 * 1_073_741_824L,
                        32, 165, 2400,
                        120, 100,
                        sharedLatencyMap),
                new GeoLocation(28.6139, 77.2090),
                "Asia",
                "EdgeIndia",
                true);

        StandardResourceAgent ra9 = new StandardResourceAgent(
                "Agent9", 0.16,
                resourceAgentVa, resourceAgentArc,
                new FirstFitAgentStrategy(true),
                new Capacity(node9,
                        8,
                        8 * 1_073_741_824L,
                        128 * 1_073_741_824L));

        agentsToBeClustered.add(ra9);

        // nagyon gyenge edge
        ComputingAppliance node10 = new ComputingAppliance(
                createNode("Node10",
                        1, 1,
                        2 * 1_073_741_824L,
                        64 * 1_073_741_824L,
                        25, 150, 3600,
                        30, 200,
                        sharedLatencyMap),
                new GeoLocation(44.4268, 26.1025),
                "EU",
                "TinyEdge",
                true);

        StandardResourceAgent ra10 = new StandardResourceAgent(
                "Agent10", 0.07,
                resourceAgentVa, resourceAgentArc,
                new FirstFitAgentStrategy(true),
                new Capacity(node10,
                        1,
                        2 * 1_073_741_824L,
                        64 * 1_073_741_824L));

        agentsToBeClustered.add(ra10);

        // ===================== +2 EDGE =====================
        // közepes edge
        ComputingAppliance node11 = new ComputingAppliance(
                createNode("Node11",
                        16, 1,
                        16 * 1_073_741_824L,
                        64 * 1_073_741_824L,
                        34, 170, 2000,
                        130, 95,
                        sharedLatencyMap),
                new GeoLocation(41.0082, 28.9784),
                "EU",
                "EdgeTurkey",
                true);

        StandardResourceAgent ra11 = new StandardResourceAgent(
                "Agent11", 0.21,
                resourceAgentVa, resourceAgentArc,
                new FirstFitAgentStrategy(true),
                new Capacity(node11,
                        16,
                        16 * 1_073_741_824L,
                        64 * 1_073_741_824L));

        agentsToBeClustered.add(ra11);

        // jobb edge
        ComputingAppliance node12 = new ComputingAppliance(
                createNode("Node12",
                        16, 1,
                        32 * 1_073_741_824L,
                        128 * 1_073_741_824L,
                        39, 180, 1400,
                        170, 70,
                        sharedLatencyMap),
                new GeoLocation(59.3293, 18.0686),
                "EU",
                "NokiaEdge",
                true);

        StandardResourceAgent ra12 = new StandardResourceAgent(
                "Agent12", 0.31,
                resourceAgentVa, resourceAgentArc,
                new FirstFitAgentStrategy(true),
                new Capacity(node12,
                        16,
                        32 * 1_073_741_824L,
                        128 * 1_073_741_824L));

        agentsToBeClustered.add(ra12);


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
        new EnergyDataCollector("Node11", node10.iaas, true);
        new EnergyDataCollector("Node12", node10.iaas, true);

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
            new DeferredEvent(delay[i++] * 60 * 1000) {

                @Override
                protected void eventAction() {
                    //new Submission(file.toString(), 2048, 0, configuration, new BroadcastBasedDecisionMaker());
                    //new Submission(file.toString(), 2048, 0, configuration, new CentralizedAntBasedDecisionMaker(4, agentsToBeClustered, 50, 200, 0.5, 0.2, 0.15, 0, 0.7));
                    new Submission(file.toString(), 2048, 0, configuration, new DecentralizedAntBasedDecisionMaker(agentsToBeClustered, 50, 200, 0.75, 0.15, 1 * 60 * 1000, 0.7));
                }
            };
        }
        
        Sun.init(6, 20, 13, 1.5);
        long starttime = System.nanoTime();       
        Timed.simulateUntil(simLength);
        long stoptime = System.nanoTime();
        EnergyDataCollector.writeToFile(ScenarioBase.resultDirectory);
        NoiseAppCsvExporter.visualise();
        
        /** results */

        //QLearning Score Table
        /*for (StandardResourceAgent ra : agentsToBeClustered) {
            System.out.println(ra.score);
        }*/

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
                + df.format(ACOsTests.calculateTimeBelowThrottling(NoiseAppCsvExporter.getInstance().noiseSensorTemperature.toPath(),
                        configuration.get("cpuTempTreshold").doubleValue())));

        SimLogger.logRes("Average time to transfer a file over the network (sec.): " + df.format(NoiseSensor.totalTimeOnNetwork / 1000.0 / soundFilesRs));  
        SimLogger.logRes("Runtime (seconds): " + TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));
    }
    
    private static double calculateTimeBelowThrottling(Path path, double cpuThreshold) {
    	List<String> lines;
		try {
			lines = Files.readAllLines(path);
			String[] header = lines.get(0).split(Character.toString(','));
	    	
	    	int n = header.length - 1; 
	    	String[] deviceNames = Arrays.copyOfRange(header, 1, header.length);
	    	
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