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
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgentManager;
import hu.u_szeged.inf.fog.simulator.agent.*;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.MappingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.strategy.message.FloodingMessagingStrategy;
import hu.u_szeged.inf.fog.simulator.agent.util.ResourceAgentCsvExporter;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class    DummyAppDemo {
    
    public static void main(String[] args) throws IOException {

        SimLogger.setLogging(1, true);
        SeedSyncer.setSeed(987654321);
        
        Map<String, Integer> sharedLatencyMap = new HashMap<>();
        
        /* image service config */
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(1, 1, 1, 1, 1);
        Deployment.setImageRegistry(new Repository(Long.MAX_VALUE, "Image-service", 125_000, 125_000, 125_000, sharedLatencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network)));

        /* node config */
        final ComputingAppliance node1 = new ComputingAppliance(
                Config.createNode(
                        "Node1",
                        64, 64 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        30, 200, 500, // minimum, idle and maximum power
                        100_000, // bandwidth
                        20, sharedLatencyMap), // latency
                new GeoLocation(51.5074, -0.1278), "EU", "Azure", false); // London

        final ComputingAppliance node2 = new ComputingAppliance(
                Config.createNode(
                        "Node2",
                        32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        20, 100, 250, // minimum, idle and maximum power
                        50_000, // bandwidth
                        35, sharedLatencyMap), // latency
                new GeoLocation(52.5200, 13.4050), "EU", "Azure", false); // Berlin

        final ComputingAppliance node3 = new ComputingAppliance(
                Config.createNode(
                        "Node3",
                        64, 64 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        30, 200, 500, // minimum, idle and maximum power
                        150_000, // bandwidth
                        15, sharedLatencyMap), // latency
                new GeoLocation(48.8566, 2.3522), "EU", "AWS", false); // Paris

        final ComputingAppliance node4 = new ComputingAppliance(
                Config.createNode(
                        "Node4",
                        32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        20, 100, 250, // minimum, idle and maximum power
                        75_000, // bandwidth
                        25, sharedLatencyMap), // latency
                new GeoLocation(50.1109, 8.6821), "EU", "AWS", false); // Frankfurt

        final ComputingAppliance node5 = new ComputingAppliance(
                Config.createNode(
                        "Node5",
                        64, 64 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        30, 200, 500, // minimum, idle and maximum power
                        100_000, // bandwidth
                        50, sharedLatencyMap), // latency
                new GeoLocation(41.8781, -87.6298), "US", "Azure", false); // Chicago

        final ComputingAppliance node6 = new ComputingAppliance(
                Config.createNode(
                        "Node6",
                        32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        20, 100, 250, // minimum, idle and maximum power
                        50_000, // bandwidth
                        65, sharedLatencyMap), // latency
                new GeoLocation(29.7604, -95.3698), "US", "Azure", false); // Houston

        final ComputingAppliance node7 = new ComputingAppliance(
                Config.createNode(
                        "Node7",
                        64, 64 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        30, 200, 500, // minimum, idle and maximum power
                        150_000, // bandwidth
                        40, sharedLatencyMap), // latency
                new GeoLocation(39.0438, -77.4874), "US", "AWS", false); // Virginia

        final ComputingAppliance node8 = new ComputingAppliance(
                Config.createNode(
                        "Node8",
                        32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE,
                        20, 100, 250, // minimum, idle and maximum power
                        75_000, // bandwidth
                        70, sharedLatencyMap), // latency
                new GeoLocation(45.5152, -122.6784), "US", "AWS", false); // Oregon

        new EnergyDataCollector("Node1-energy", node1.iaas, true, true, () -> hasRunningApplication(node1));
        new EnergyDataCollector("Node2-energy", node2.iaas, true, true, () -> hasRunningApplication(node2));
        new EnergyDataCollector("Node3-energy", node3.iaas, true, true, () -> hasRunningApplication(node3));
        new EnergyDataCollector("Node4-energy", node4.iaas, true, true, () -> hasRunningApplication(node4));
        new EnergyDataCollector("Node5-energy", node5.iaas, true, true, () -> hasRunningApplication(node5));
        new EnergyDataCollector("Node6-energy", node6.iaas, true, true, () -> hasRunningApplication(node6));
        new EnergyDataCollector("Node7-energy", node7.iaas, true, true, () -> hasRunningApplication(node7));
        new EnergyDataCollector("Node8-energy", node8.iaas, true, true, () -> hasRunningApplication(node8));
        
        /* agent config */
        VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L);
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);

        ResourceAgent ra1 = new ResourceAgent(
                "Agent1", 1.00,
                (MappingStrategy) Config.DUMMY_CONFIGURATION.get("mappingStrategy"),
                new FloodingMessagingStrategy());

        ra1.initResourceAgent(
                resourceAgentVa,
                resourceAgentArc,
                new Capacity(node1, 64, 64 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                new Capacity(node2, 32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        ResourceAgent ra2 = new ResourceAgent(
                "Agent2", 1.25,
                (MappingStrategy) Config.DUMMY_CONFIGURATION.get("mappingStrategy"),
                new FloodingMessagingStrategy());

        ra2.initResourceAgent(
                resourceAgentVa,
                resourceAgentArc,
                new Capacity(node3, 64, 64 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                new Capacity(node4, 32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        ResourceAgent ra3 = new ResourceAgent(
                "Agent3", 1.50,
                (MappingStrategy) Config.DUMMY_CONFIGURATION.get("mappingStrategy"),
                new FloodingMessagingStrategy());

        ra3.initResourceAgent(
                resourceAgentVa,
                resourceAgentArc,
                new Capacity(node5, 64, 64 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                new Capacity(node6, 32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        ResourceAgent ra4 = new ResourceAgent(
                "Agent4", 1.75,
                (MappingStrategy) Config.DUMMY_CONFIGURATION.get("mappingStrategy"),
                new FloodingMessagingStrategy());

        ra4.initResourceAgent(
                resourceAgentVa,
                resourceAgentArc,
                new Capacity(node7, 64, 64 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE),
                new Capacity(node8, 32, 32 * ScenarioBase.GB_IN_BYTE, 256 * ScenarioBase.GB_IN_BYTE));

        ResourceAgentManager.getInstance().start((long) Config.DUMMY_CONFIGURATION.get("samplingFreq") * 6,(boolean) Config.DUMMY_CONFIGURATION.get("csvLogging"));

        /* app submission */
        List<Path> appDescriptionFiles = Files.list((Path) Config.DUMMY_CONFIGURATION.get("inputDir"))
                .filter(f -> f.toString().endsWith(".json"))
                .sorted()
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
        //Timed.simulateUntil((long) Config.DUMMY_CONFIGURATION.get("simLength"));
        Timed.simulateUntilLastEvent();
        final long stoptime = System.nanoTime();
        EnergyDataCollector.writeToFile(ScenarioBase.RESULT_DIRECTORY);

        if ((boolean) Config.DUMMY_CONFIGURATION.get("csvLogging")) {
            CsvVisualiser.visualise(
                    "RA-metrics",
                    ResourceAgentCsvExporter.getInstance().hourlyPricePath,
                    ResourceAgentCsvExporter.getInstance().resourceMetricsPath
            ).write();
        }
    
        /* results */
        SimLogger.logEmptyLine();
        for (StorageObject so : Deployment.registryService.contents()){
            SimLogger.logRes("\t(Registry) " + so);
        }

        SimLogger.logEmptyLine();
        long totalReceivedDataSize = 0L;
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

            StorageObject receivedData = ca.iaas.machines.get(0).localDisk.lookup("DummyApp-files");
            if (receivedData != null) {
                totalReceivedDataSize += receivedData.size;
            }
        }

        SimLogger.logEmptyLine();

        Map<String, Double> applicationCosts = new HashMap<>();
        for (ResourceAgent agent : ResourceAgent.allResourceAgents.values()) {
            for (Capacity cap : agent.capacities.values()) {
                SimLogger.logRes("\t(" + agent.name + ") " + cap);

                for (Utilisation util : cap.utilisations) {
                    SimLogger.logRes("\t\t" + util);

                    if (util.state != Utilisation.State.TERMINATED) {
                        continue;
                    }

                    double durationHours = (util.endTime - util.initTime) / 3_600_000.0;

                    double totalCost = util.actualCost * durationHours;

                    String[] resourceNameParts =
                            util.component.id.split("-");

                    String applicationName =
                            resourceNameParts[0] + "-" + resourceNameParts[1];

                    applicationCosts.merge(
                            applicationName,
                            totalCost,
                            Double::sum);
                }
            }
        }

        SimLogger.logEmptyLine();
        SimLogger.logRes("Application results:");

        int applicationCount = AgentApplication.allAgentApplications.size();
        int successfulApplicationCount = 0;

        long totalLocalCandidateEvaluations = 0L;
        long totalLocalGenerationRuntimeNanos = 0L;
        long totalGlobalCoverageEvaluations = 0L;
        long totalGlobalSelectionRuntimeNanos = 0L;
        long totalOffersBeforePareto = 0L;
        long totalOffersAfterPareto = 0L;

        double totalSuccessfulDeploymentTime = 0.0;

        for (AgentApplication application : AgentApplication.allAgentApplications) {
            boolean successful = application.deploymentTime != -1;

            if (successful) {
                successfulApplicationCount++;
                totalSuccessfulDeploymentTime += application.deploymentTime;
            }

            totalLocalCandidateEvaluations += application.localCandidateEvaluationCount;
            totalLocalGenerationRuntimeNanos += application.localGenerationRuntimeNanos;
            totalGlobalCoverageEvaluations += application.globalCoverageEvaluationCount;
            totalGlobalSelectionRuntimeNanos += application.globalSelectionRuntimeNanos;
            totalOffersBeforePareto += application.localOffersBeforePareto;
            totalOffersAfterPareto += application.localOffersAfterPareto;

            SimLogger.logRes("\t" + application.name + ":");
            SimLogger.logRes("\t\tDeployment successful: " + successful);
            SimLogger.logRes("\t\tDeployment time (ms): " + application.deploymentTime);
            SimLogger.logRes("\t\tBroadcast rounds: " + application.broadcastCount);
            SimLogger.logRes("\t\tTotal cost (unit): " + applicationCosts.getOrDefault(application.name, 0.0));
            SimLogger.logRes("\t\tLocal candidate evaluations: " + application.localCandidateEvaluationCount);
            SimLogger.logRes("\t\tLocal generation runtime (ms): " + application.localGenerationRuntimeNanos / 1_000_000.0);
            SimLogger.logRes("\t\tGlobal coverage evaluations: " + application.globalCoverageEvaluationCount);
            SimLogger.logRes("\t\tGlobal selection runtime (ms): " + application.globalSelectionRuntimeNanos / 1_000_000.0);

            if (application.localOffersBeforePareto > 0L) {
                double paretoReduction =
                        1.0 - (double) application.localOffersAfterPareto / application.localOffersBeforePareto;

                SimLogger.logRes("\t\tLocalOffers before Pareto: " + application.localOffersBeforePareto);
                SimLogger.logRes("\t\tLocalOffers after Pareto: " + application.localOffersAfterPareto);
                SimLogger.logRes("\t\tPareto reduction (%): " + paretoReduction * 100.0);
            }
        }

        double deploymentSuccessRate =
                applicationCount == 0 ? 0.0 : (double) successfulApplicationCount / applicationCount;

        double averageDeploymentTime =
                successfulApplicationCount == 0 ? 0.0 : totalSuccessfulDeploymentTime / successfulApplicationCount;

        double averageLocalCandidateEvaluations =
                applicationCount == 0 ? 0.0 : (double) totalLocalCandidateEvaluations / applicationCount;

        double averageLocalGenerationRuntimeMillis =
                applicationCount == 0 ? 0.0 : totalLocalGenerationRuntimeNanos / 1_000_000.0 / applicationCount;

        double averageGlobalCoverageEvaluations =
                applicationCount == 0 ? 0.0 : (double) totalGlobalCoverageEvaluations / applicationCount;

        double averageGlobalSelectionRuntimeMillis =
                applicationCount == 0 ? 0.0 : totalGlobalSelectionRuntimeNanos / 1_000_000.0 / applicationCount;

        SimLogger.logEmptyLine();
        SimLogger.logRes("Aggregated application results:");
        SimLogger.logRes("\tSubmitted applications: " + applicationCount);
        SimLogger.logRes("\tSuccessful deployments: " + successfulApplicationCount);
        SimLogger.logRes("\tDeployment success rate (%): " + deploymentSuccessRate * 100.0);
        SimLogger.logRes("\tAverage successful deployment time (ms): " + averageDeploymentTime);
        SimLogger.logRes("\tAverage local candidate evaluations: " + averageLocalCandidateEvaluations);
        SimLogger.logRes("\tAverage local generation runtime (ms): " + averageLocalGenerationRuntimeMillis);
        SimLogger.logRes("\tAverage global coverage evaluations: " + averageGlobalCoverageEvaluations);
        SimLogger.logRes("\tAverage global selection runtime (ms): " + averageGlobalSelectionRuntimeMillis);
        if (totalOffersBeforePareto > 0L) {
            double overallParetoReduction = 1.0 - (double) totalOffersAfterPareto / totalOffersBeforePareto;

            SimLogger.logRes("\tTotal local offers before Pareto: " + totalOffersBeforePareto);
            SimLogger.logRes("\tTotal local offers after Pareto: " + totalOffersAfterPareto);
            SimLogger.logRes("\tPareto reduction (%): " + overallParetoReduction * 100.0);
        }
        if ((boolean) Config.DUMMY_CONFIGURATION.get("csvLogging")) {
            SimLogger.logRes("\tAverage Resource Agent utility during simulation: "
                    + ResourceAgentCsvExporter.getInstance().getAverageResourceUtility());
        } else {
            SimLogger.logRes("\tAverage Resource Agent utility during simulation: not available (CSV logging disabled)");
        }

        double applicationEnergyKwh = 0.0;

        for (EnergyDataCollector energyCollector : EnergyDataCollector.allEnergyCollectors.values()) {
            applicationEnergyKwh += energyCollector.accumulatedEnergy / ScenarioBase.TO_KWH;
        }


        long totalGeneratedFiles = 0L;
        long totalGeneratedDataSize = 0L;
        long totalFileDeliveryTime = 0L;

        for (SwarmAgent swarmAgent : SwarmAgent.allSwarmAgents) {
            if (!swarmAgent.app.type.equals("dummy")) {
                continue;
            }

            totalGeneratedFiles += swarmAgent.totalGeneratedFiles;
            totalGeneratedDataSize += swarmAgent.totalGeneratedDataSize;
            totalFileDeliveryTime += swarmAgent.totalFileDeliveryTime;
        }

        double averageFileDeliveryLatency = totalGeneratedFiles == 0L ? 0.0 : (double) totalFileDeliveryTime / totalGeneratedFiles;

        double averageFileThroughput =
                totalFileDeliveryTime == 0L ? 0.0 : totalGeneratedDataSize * 1_000.0  / ScenarioBase.MB_IN_BYTE / totalFileDeliveryTime;

        SimLogger.logEmptyLine();
        SimLogger.logRes("Generated files: " + totalGeneratedFiles);
        SimLogger.logRes("Generated data / received data (MB): "
                + (double) totalGeneratedDataSize / ScenarioBase.MB_IN_BYTE + " / " + (double) totalReceivedDataSize / ScenarioBase.MB_IN_BYTE);
        SimLogger.logRes("Average file delivery latency (ms): " + averageFileDeliveryLatency);
        SimLogger.logRes("Average file throughput (MB/s): " + averageFileThroughput);
        SimLogger.logRes("Energy consumption of utilized nodes (kWh): " + applicationEnergyKwh);
        SimLogger.logRes("Simulation time (hour): " + TimeUnit.HOURS.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
        SimLogger.logRes("Simulator's runtime (sec.): " + TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));

        SimLogger.logEmptyLine();
        MappingStrategy mappingStrategy = (MappingStrategy) Config.APP_TYPE.get("mappingStrategy");
        boolean atomicOffers = (boolean) Config.APP_TYPE.get("atomicOffers");
        boolean onlyFirstOffer = (boolean) Config.APP_TYPE.get("onlyFirstOffer");

        String globalSelectionMethod;

        if (atomicOffers) {
            globalSelectionMethod = "Global SA";
        } else if (onlyFirstOffer) {
            globalSelectionMethod = "First hard-valid coverage";
        } else {
            globalSelectionMethod = "All hard-valid coverages + " + Config.APP_TYPE.get("rankingMethod") + " ranking";
        }

        SimLogger.logEmptyLine();
        SimLogger.logRes("Algorithm configuration:");
        SimLogger.logRes("\tLocal mapping strategy: " + mappingStrategy.getClass().getSimpleName());
        SimLogger.logRes("\tGlobal selection method: " + globalSelectionMethod);
        SimLogger.logRes("\tAtomic LocalOffers: " + atomicOffers);
    }

    private static boolean hasRunningApplication(ComputingAppliance node) {
        return ResourceAgent.allResourceAgents.values().stream()
                .flatMap(agent -> agent.capacities.values().stream())
                .filter(capacity -> capacity.node == node)
                .flatMap(capacity -> capacity.utilisations.stream())
                .anyMatch(utilisation -> utilisation.component != null
                        && utilisation.state == Utilisation.State.ALLOCATED);
    }
}