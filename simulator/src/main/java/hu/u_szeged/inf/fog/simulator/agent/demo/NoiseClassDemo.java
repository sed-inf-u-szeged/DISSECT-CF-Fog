package hu.u_szeged.inf.fog.simulator.agent.demo;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.agent.*;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.RemoteServer;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.Sun;
import hu.u_szeged.inf.fog.simulator.agent.forecast.ForecasterManager;
import hu.u_szeged.inf.fog.simulator.agent.management.ForecastBasedSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.management.GreedyNoiseSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.DirectMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.FirstFitMappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.util.NoiseAppCsvExporter;
import hu.u_szeged.inf.fog.simulator.common.util.CsvVisualiser;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class NoiseClassDemo {

    public static void main(String[] args) throws Exception {

        SimLogger.setLogging(1, true);

        Map<String, Integer> sharedLatencyMap = new HashMap<>();

        List<Path> appDescriptionFiles = Files.list((Path) Config.NOISE_CLASS_CONFIGURATION.get("inputDir"))
                .filter(f -> f.toString().endsWith(".json"))
                .sorted(Comparator.comparing(f -> f.getFileName().toString()))
                .toList();

        boolean hasDuplicates = appDescriptionFiles.stream()
                .map(p -> p.getFileName().toString())
                .distinct()
                .count() != appDescriptionFiles.size();

        List<Integer> delays = (List<Integer>) Config.NOISE_CLASS_CONFIGURATION.get("submissionDelay");
        if (appDescriptionFiles.size() != delays.size() || hasDuplicates) {
            SimLogger.logError(
                    "The number of application description files must be the same as the number of submission delays, and the filenames must be unique."
            );
        }

        /* forecaster */
        if (Config.NOISE_CLASS_CONFIGURATION.get("swarmAgentType").equals("forecast")) {
            ForecasterManager.getInstance((String) Config.NOISE_CLASS_CONFIGURATION.get("predictorDir"),
                    4, 1337, (String) Config.NOISE_CLASS_CONFIGURATION.get("predictorModelPath"));
        }

        /* image service config */
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(1, 1, 1, 1, 1);
        Deployment.setImageRegistry(new Repository(Long.MAX_VALUE, "Image-service", 125_000, 125_000, 125_000, sharedLatencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network)));

        /* RPi config */
        for (int i = 0; i < delays.size(); i++) {
            ComputingAppliance rpi1 = new ComputingAppliance(
                Config.createNode("RPi1" + i, 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        2, 4, 15, 12_500, 15, sharedLatencyMap),
                new GeoLocation(45.51, 13.79), "EU", "", true);

            ComputingAppliance rpi2 = new ComputingAppliance(
                    Config.createNode("RPi2" + i, 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                            2, 4, 15, 12_500, 15, sharedLatencyMap),
                new GeoLocation(45.52, 13.69), "EU", "", true);

            ComputingAppliance rpi3 = new ComputingAppliance(
                    Config.createNode("RPi3" + i, 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                            2, 4, 15, 12_500, 15, sharedLatencyMap),
                new GeoLocation(45.58, 13.66), "", "", true);

            ComputingAppliance rpi4 = new ComputingAppliance(
                    Config.createNode("RPi4" + i, 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                            2, 4, 15, 12_500, 15, sharedLatencyMap),
                new GeoLocation(45.46, 13.81), "", "", true);

            ComputingAppliance rpi5 = new ComputingAppliance(
                    Config.createNode("RPi5" + i, 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                            2, 4, 15, 12_500, 15, sharedLatencyMap),
                new GeoLocation(45.53, 13.71), "", "", true);

            ComputingAppliance rpi6 = new ComputingAppliance(
                    Config.createNode("RPi6" + i, 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                            2, 4, 15, 12_500, 15, sharedLatencyMap),
                new GeoLocation(45.58, 13.74), "", "", true);

            ComputingAppliance rpi7 = new ComputingAppliance(
                    Config.createNode("RPi7" + i, 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                            2, 4, 15, 12_500, 15, sharedLatencyMap),
                new GeoLocation(45.50, 13.70), "", "", true);

            ComputingAppliance rpi8 = new ComputingAppliance(
                    Config.createNode("RPi8" + i, 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                            2, 4, 15, 12_500, 15, sharedLatencyMap),
                new GeoLocation(45.62, 13.76), "", "", true);

            ComputingAppliance rpi9 = new ComputingAppliance(
                    Config.createNode("RPi9" + i, 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                            2, 4, 15, 12_500, 15, sharedLatencyMap),
                new GeoLocation(45.59, 13.67), "", "", true);

            ComputingAppliance rpi10 = new ComputingAppliance(
                    Config.createNode("RPi10" + i, 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                            2, 4, 15, 12_500, 15, sharedLatencyMap),
                new GeoLocation(45.52, 13.66), "", "", true);

            new EnergyDataCollector("RPi1" + i + "-energy", rpi1.iaas,true,true);
            new EnergyDataCollector("RPi2" + i + "-energy", rpi2.iaas,true, true);
            new EnergyDataCollector("RPi3" + i + "-energy", rpi3.iaas,true, true);
            new EnergyDataCollector("RPi4" + i + "-energy", rpi4.iaas,true, true);
            new EnergyDataCollector("RPi5" + i + "-energy", rpi5.iaas,true, true);
            new EnergyDataCollector("RPi6" + i + "-energy", rpi6.iaas,true, true);
            new EnergyDataCollector("RPi7" + i + "-energy", rpi7.iaas,true, true);
            new EnergyDataCollector("RPi8" + i + "-energy", rpi8.iaas,true, true);
            new EnergyDataCollector("RPi9" + i + "-energy", rpi9.iaas,true, true);
            new EnergyDataCollector("RPi10" + i + "-energy", rpi10.iaas,true, true);
        }

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
        new EnergyDataCollector("Node2-energy", node2.iaas,true, true);
        new EnergyDataCollector("Node3-energy", node3.iaas,true, true);
        new EnergyDataCollector("Node4-energy", node4.iaas,true, true);
        new EnergyDataCollector("Node5-energy", node5.iaas,true, true);

        /* agent config */
        VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L);
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);

        Map<String, String> mapping = new HashMap<>();
        ResourceAgent ra0 = new ResourceAgent("Agent0", 0.00002778, new DirectMappingStrategy(mapping), new FloodingMessagingStrategy());

        for (int i = 0; i < delays.size(); i++) {
            String nameWithoutExtension = appDescriptionFiles.get(i).getFileName().toString().replaceFirst("\\.[^.]+$", "");

            mapping.put(nameWithoutExtension + "-noise-sensor-01", "Agent0");
            mapping.put(nameWithoutExtension + "-noise-sensor-02", "Agent0");
            mapping.put(nameWithoutExtension + "-noise-sensor-03", "Agent0");
            mapping.put(nameWithoutExtension + "-noise-sensor-04", "Agent0");
            mapping.put(nameWithoutExtension + "-noise-sensor-05", "Agent0");
            mapping.put(nameWithoutExtension + "-noise-sensor-06", "Agent0");
            mapping.put(nameWithoutExtension + "-noise-sensor-07", "Agent0");
            mapping.put(nameWithoutExtension + "-noise-sensor-08", "Agent0");
            mapping.put(nameWithoutExtension + "-noise-sensor-09", "Agent0");
            mapping.put(nameWithoutExtension + "-noise-sensor-10", "Agent0");

            ra0.initResourceAgent(resourceAgentVa, resourceAgentArc,
                    new Capacity(ComputingAppliance.allComputingAppliances.get("RPi1" + i), 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                    new Capacity(ComputingAppliance.allComputingAppliances.get("RPi2" + i), 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                    new Capacity(ComputingAppliance.allComputingAppliances.get("RPi3" + i), 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                    new Capacity(ComputingAppliance.allComputingAppliances.get("RPi4" + i), 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                    new Capacity(ComputingAppliance.allComputingAppliances.get("RPi5" + i), 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                    new Capacity(ComputingAppliance.allComputingAppliances.get("RPi6" + i), 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                    new Capacity(ComputingAppliance.allComputingAppliances.get("RPi7" + i), 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                    new Capacity(ComputingAppliance.allComputingAppliances.get("RPi8" + i), 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                    new Capacity(ComputingAppliance.allComputingAppliances.get("RPi9" + i), 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                    new Capacity(ComputingAppliance.allComputingAppliances.get("RPi10" + i), 5, 8 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE)
            );
        }
        
        ResourceAgent ra1 = 
                new ResourceAgent("Agent1", 0.00013889, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra1.initResourceAgent(resourceAgentVa, resourceAgentArc, new Capacity(node1, 10, 52 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        ResourceAgent ra2 = 
                new ResourceAgent("Agent2", 0.00277778, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra2.initResourceAgent(resourceAgentVa, resourceAgentArc, new Capacity(node2, 10, 64 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        ResourceAgent ra3 = 
                new ResourceAgent("Agent3", 0.00041667, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra3.initResourceAgent(resourceAgentVa, resourceAgentArc, new Capacity(node3, 32, 32 * ScenarioBase.GB_IN_BYTE,256 * ScenarioBase.GB_IN_BYTE));

        ResourceAgent ra4 = 
                new ResourceAgent("Agent4", 0.00000278, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra4.initResourceAgent(resourceAgentVa, resourceAgentArc, new Capacity(node4, 48, 48 * ScenarioBase.GB_IN_BYTE,256 * ScenarioBase.GB_IN_BYTE));

        ResourceAgent ra5 = 
                new ResourceAgent("Agent5", 0.00005556, new FirstFitMappingStrategy(true), new FloodingMessagingStrategy());
        ra5.initResourceAgent(resourceAgentVa, resourceAgentArc, new Capacity(node5, 32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        /* app submission */
        int i = 0;
        for (Path filepath : appDescriptionFiles) {
            new DeferredEvent(delays.get(i) * ScenarioBase.MINUTE_IN_MILLISECONDS) {

                @Override
                protected void eventAction() {
                    new Submission(filepath, 2048);
                }
            };
            i++;
        }

        Sun.init(6, 20, 13, 1.5);
        long starttime = System.nanoTime();
        //Timed.simulateUntil((long) Config.NOISE_CLASS_ONFIGURATION.get("simLength"));
        Timed.simulateUntilLastEvent();
        long stoptime = System.nanoTime();
        Path energyValues = EnergyDataCollector.writeToFile(ScenarioBase.RESULT_DIRECTORY);
        for (NoiseAppCsvExporter noiseAppCsvExporter : NoiseAppCsvExporter.allNoiseAppCsvExporters.values()){
            CsvVisualiser.visualise(
                    noiseAppCsvExporter.appName,
                    noiseAppCsvExporter.soundValuesPath,
                    noiseAppCsvExporter.noiseSensorTemperaturePath,
                    noiseAppCsvExporter.noiseSensorCpuLoadPath,
                    noiseAppCsvExporter.noiseSensorClassifierCountPath,
                    noiseAppCsvExporter.processedFilePath,
                    noiseAppCsvExporter.fileMigrationCountPath,
                    noiseAppCsvExporter.sunIntensityPath,
                    energyValues
            ).write();
        }


        /* results */

        /*
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
        */

        SimLogger.logEmptyLine();
        for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
            for (Capacity cap : agent.capacities.values()) {
                SimLogger.logRes("\t(" + agent.name + ") " + cap);
                for (Capacity.Utilisation util : cap.utilisations) {
                    SimLogger.logRes("\t\t" + util);
                }
            }
        }

        long soundFilesOnNoiseSensors = 0;
        long soundFilesOnRemoteServers = 0;
        double avgDeploymentTime = 0.0;
        double avgOffers = 0.0;
        long totalGeneratedFiles = 0;

        for (SwarmAgent sa : SwarmAgent.allSwarmAgents) {
            SimLogger.logRes(sa.app.name + " deployment: ");
            if (sa.app.deploymentTime != -1) {
                avgDeploymentTime += sa.app.deploymentTime;
                SimLogger.logRes("\tTime (min.): " + sa.app.deploymentTime / ScenarioBase.MINUTE_IN_MILLISECONDS);
            } else {
                SimLogger.logRes("\tTime (min.): -1");
            }
            SimLogger.logRes("\tAvailable offers: " + sa.app.offers.size());
            avgOffers += sa.app.offers.size();
            if (!sa.app.offers.isEmpty()) {
                StringBuilder str = new StringBuilder();
                for (ResourceAgent ra : sa.app.offers.get((sa.app.winningOffer)).agentComponentsMap.keySet()) {
                    str.append(ra.name).append(" ");
                }
                SimLogger.logRes("\tWinning offer: " + sa.app.offers.get((sa.app.winningOffer)).id + " ( " + str.toString() + ")");
            }

            StorageObject resFile = null;
            for (Object o : sa.observedAppComponents) {
                if (o instanceof NoiseSensor ns) {
                    SimLogger.logRes("\t" + ns.util.component.id + " is inside: " + ns.inside + ", exposed to sunlight: " + ns.sunExposed);
                    for (StorageObject so : ns.util.vm.getResourceAllocation().getHost().localDisk.contents()) {
                        if (so.id.contains("noise-sensor")) {
                            soundFilesOnNoiseSensors++;
                        }
                    }
                } else if (o instanceof RemoteServer rs) {
                    for (StorageObject so : rs.util.vm.getResourceAllocation().getHost().localDisk.contents()) {
                        if (so.id.equals(sa.app.name)) {
                            resFile = so;
                        }
                    }
                }
            }
            soundFilesOnRemoteServers += resFile.size / (long) Config.NOISE_CLASS_CONFIGURATION.get("resFileSize");
            totalGeneratedFiles += sa.totalGeneratedFiles;

            if (sa instanceof GreedyNoiseSwarmAgent gnsa){
                SimLogger.logRes("Scaling decision type - count:");
                gnsa.decisionType.forEach((key, value) ->
                        SimLogger.logRes("\t" + key + ": " + value)
                );
            }
            if (sa instanceof ForecastBasedSwarmAgent fbsa){
                SimLogger.logRes("\tNumber of forecasts: " + fbsa.forecastingTimes.size());
            }


        }

        double totalEnergy = 0;
        for (EnergyDataCollector ec : EnergyDataCollector.allEnergyCollectors.values()) {
            totalEnergy += ec.accumulatedEnergy / ScenarioBase.TO_KWH;
            // TODO: calculate only for nodes used by the application
        }

        SimLogger.logEmptyLine();
        SimLogger.logRes("Simulated time (hour): " + TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
        SimLogger.logRes("Simulator runtime (seconds): " + TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));

        //SimLogger.logRes("Total price (EUR): " + df.format(totalCost));
        SimLogger.logRes("Average deployment time (min.): " + avgDeploymentTime / SwarmAgent.allSwarmAgents.size() / ScenarioBase.MINUTE_IN_MILLISECONDS);
        SimLogger.logRes("Average number of offers (pc.): " + avgOffers / SwarmAgent.allSwarmAgents.size());

        SimLogger.logRes("Total energy (kWh): " + totalEnergy);

        SimLogger.logRes("Size of generated files (MB): " + totalGeneratedFiles * (long) Config.NOISE_CLASS_CONFIGURATION.get("soundFileSize") / ScenarioBase.MB_IN_BYTE);
        SimLogger.logRes("Number of sound events (pc.): " + totalGeneratedFiles);

        SimLogger.logRes("Number of offloaded sound events (pc.): " + NoiseSensor.totalOffloadedFiles);
        SimLogger.logRes("Number of sound events requiring processing (pc.): " + NoiseSensor.totalSoundEventsToProcess);
        SimLogger.logRes("Number of processed files (pc.): " + NoiseSensor.totalProcessedFiles);
        SimLogger.logRes("Number of sound files on noise sensors: " + soundFilesOnNoiseSensors);
        SimLogger.logRes("Number of sound files on the remote servers: " + soundFilesOnRemoteServers);

        SimLogger.logRes("Average end-to-end latency (sec.): " + RemoteServer.totalEndToEndLatency / soundFilesOnRemoteServers / 1000.0);
        double avgTimeBelowThrottling = 0.0;
        for (NoiseAppCsvExporter noiseAppCsvExporter : NoiseAppCsvExporter.allNoiseAppCsvExporters.values()){
            avgTimeBelowThrottling += calculateTimeBelowThrottling(
                    noiseAppCsvExporter.noiseSensorTemperaturePath, (double) Config.NOISE_CLASS_CONFIGURATION.get("cpuTempTreshold"));
        }
        SimLogger.logRes("Time below the temperature threshold (%):" + avgTimeBelowThrottling / NoiseAppCsvExporter.allNoiseAppCsvExporters.size());
    }

    private static double calculateTimeBelowThrottling(Path path, double cpuThreshold) {
        try (BufferedReader br = Files.newBufferedReader(path)) {

            String headerLine = br.readLine();
            String[] header = headerLine.split(",");
            int n = header.length - 1;

            long[] total = new long[n];
            long[] below = new long[n];

            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                for (int i = 0; i < n; i++) {
                    double v = Double.parseDouble(p[i + 1]);
                    total[i]++;
                    if (v <= cpuThreshold) below[i]++;
                }
            }

            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                sum += 100.0 * below[i] / total[i];
            }

            return sum / n;

        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }
}