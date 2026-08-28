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
import java.util.*;
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

        VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L);
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);
        createEvaluationInfrastructure(sharedLatencyMap, resourceAgentVa, resourceAgentArc);

        ResourceAgentManager.getInstance().start((long) Config.DUMMY_CONFIGURATION.get("samplingFreq") * 6,(boolean) Config.DUMMY_CONFIGURATION.get("csvLogging"));

        /* app submission */
        List<Integer> submissionDelays =
                (List<Integer>)
                        Config.DUMMY_CONFIGURATION.get("submissionDelay");

        List<Path> appDescriptionFiles;

        try (var files =
                     Files.list(
                             (Path)
                                     Config.DUMMY_CONFIGURATION.get("inputDir"))) {

            appDescriptionFiles =
                    files
                            .filter(Files::isRegularFile)
                            .filter(file ->
                                    file.toString().endsWith(".json"))
                            .sorted()
                            .toList();
        }

        if (appDescriptionFiles.size() != submissionDelays.size()) {
            SimLogger.logError(
                    "Inconsistent application configuration: "
                            + appDescriptionFiles.size()
                            + " application description files were found, but "
                            + submissionDelays.size()
                            + " submission delays are configured.");
        }

        for (int applicationIndex = 0;
             applicationIndex < appDescriptionFiles.size();
             applicationIndex++) {

            Path file =
                    appDescriptionFiles.get(applicationIndex);

            int submissionDelay =
                    submissionDelays.get(applicationIndex);

            new DeferredEvent(
                    submissionDelay
                            * ScenarioBase.MINUTE_IN_MILLISECONDS) {

                @Override
                protected void eventAction() {
                    new Submission(file, 2_048);
                }
            };
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
        double totalCosts = 0.0;
        double totalGlobalQosScore = 0.0;


        for (AgentApplication application : AgentApplication.allAgentApplications) {
            boolean successful = application.deploymentSuccessful;

            if (successful) {
                successfulApplicationCount++;
                totalSuccessfulDeploymentTime += application.deploymentTime;

                if (application.winningGlobalQosScore != null) {
                    totalGlobalQosScore += application.winningGlobalQosScore;
                }
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
            SimLogger.logRes("\t\tWinning global QoS score: " + (application.winningGlobalQosScore == null ? "not available" : application.winningGlobalQosScore));

            if (successful) {
                Offer winningOffer = application.offers.get(application.winningOffer);
                SimLogger.logRes("\t\tWinning provider count: " + winningOffer.metrics.providerCount);

                SimLogger.logRes("\t\tWinning cost: " + winningOffer.metrics.cost);

                SimLogger.logRes("\t\tWinning projected power (W): " + winningOffer.metrics.energy);

                SimLogger.logRes("\t\tWinning average latency (ms): " + winningOffer.metrics.latency);

                SimLogger.logRes("\t\tWinning average bandwidth (byte/ms): " + winningOffer.metrics.bandwidth);
            }

            SimLogger.logRes("\t\tBroadcast rounds: " + application.broadcastCount);
            double cost = applicationCosts.getOrDefault(application.name, 0.0);
            totalCosts += cost;
            SimLogger.logRes("\t\tTotal cost (unit): " + cost);
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

        double overallAchievedQosYield =
                applicationCount == 0 ? 0.0 : totalGlobalQosScore / applicationCount;

        SimLogger.logEmptyLine();
        SimLogger.logRes("Aggregated application results:");
        SimLogger.logRes("\tSubmitted applications: " + applicationCount);
        SimLogger.logRes("\tSuccessful deployments: " + successfulApplicationCount);
        SimLogger.logRes("\tDeployment success rate (%): " + deploymentSuccessRate * 100.0);
        SimLogger.logRes("\tOverall achieved QoS yield: " + overallAchievedQosYield);
        SimLogger.logRes("\tAverage successful deployment time (sec.): " + averageDeploymentTime / 1000.0);
        SimLogger.logRes("\tAverage local candidate evaluations: " + averageLocalCandidateEvaluations);
        SimLogger.logRes("\tAverage local generation runtime (ms): " + averageLocalGenerationRuntimeMillis);
        SimLogger.logRes("\tAverage global coverage evaluations: " + averageGlobalCoverageEvaluations);
        SimLogger.logRes("\tAverage global selection runtime (ms): " + averageGlobalSelectionRuntimeMillis);
        SimLogger.logRes("\tAverage total selection runtime (ms): " + (averageLocalGenerationRuntimeMillis + averageGlobalSelectionRuntimeMillis));
        if (totalOffersBeforePareto > 0L) {
            double overallParetoReduction = 1.0 - (double) totalOffersAfterPareto / totalOffersBeforePareto;

            SimLogger.logRes("\tTotal local offers before Pareto: " + totalOffersBeforePareto);
            SimLogger.logRes("\tTotal local offers after Pareto: " + totalOffersAfterPareto);
            SimLogger.logRes("\tPareto reduction (%): " + overallParetoReduction * 100.0);
        }
        if ((boolean) Config.DUMMY_CONFIGURATION.get("csvLogging")) {
            ResourceAgentCsvExporter exporter = ResourceAgentCsvExporter.getInstance();

            SimLogger.logRes("\tProvider quality during simulation: " + exporter.getProviderQuality());
            SimLogger.logRes("\t\tAverage active balance: " + exporter.getAverageActiveBalance());
            SimLogger.logRes("\t\tAverage active utilisation: " + exporter.getAverageActiveUtilisation());
            SimLogger.logRes("\t\tAverage active fragmentation: " + exporter.getAverageActiveFragmentation());
            SimLogger.logRes("\t\tAverage active compactness: " + exporter.getAverageActiveCompactness());
        } else {
            SimLogger.logRes("\tProvider quality during simulation: not available because CSV logging is disabled.");
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
        double averageCostPerSuccessfulDeployment =
                successfulApplicationCount == 0 ? 0.0 : totalCosts / successfulApplicationCount;

        double averageEnergyPerSuccessfulDeployment =
                successfulApplicationCount == 0 ? 0.0 : applicationEnergyKwh / successfulApplicationCount;

        SimLogger.logRes("Total cost of deployed applications (unit): " + totalCosts
                + " - average per successful deployment: " + averageCostPerSuccessfulDeployment);

        SimLogger.logRes("Energy consumption of utilized nodes (kWh): " + applicationEnergyKwh
                + " - average per successful deployment: " + averageEnergyPerSuccessfulDeployment);
        SimLogger.logRes("Simulation time (min.): " + TimeUnit.MINUTES.convert(Timed.getFireCount(), TimeUnit.MILLISECONDS));
        SimLogger.logRes("Simulator's runtime (sec.): " + TimeUnit.SECONDS.convert(stoptime - starttime, TimeUnit.NANOSECONDS));

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

    private static void createEvaluationInfrastructure(
            Map<String, Integer> sharedLatencyMap,
            VirtualAppliance resourceAgentVa,
            AlterableResourceConstraints resourceAgentArc) {

        List<Config.ResourceAgentTopology> raTopologies =
                (List<Config.ResourceAgentTopology>)
                        Config.DUMMY_CONFIGURATION.get("raTopologies");

        for (int agentIndex = 0;
             agentIndex < raTopologies.size();
             agentIndex++) {

            Config.ResourceAgentTopology topology =
                    raTopologies.get(agentIndex);

            String region =
                    SeedSyncer.centralRnd.nextBoolean() ? "EU" : "US";

            String cloudProvider =
                    agentIndex % 2 == 0 ? "Azure" : "AWS";

            double cloudAnchorLatitude = region.equals("EU")
                    ? 45.0 + SeedSyncer.centralRnd.nextDouble() * 10.0
                    : 29.0 + SeedSyncer.centralRnd.nextDouble() * 17.0;

            double cloudAnchorLongitude = region.equals("EU")
                    ? -1.0 + SeedSyncer.centralRnd.nextDouble() * 16.0
                    : -123.0 + SeedSyncer.centralRnd.nextDouble() * 46.0;

            List<GeoLocation> cloudLocations = new ArrayList<>();
            List<GeoLocation> fogLocations = new ArrayList<>();
            List<GeoLocation> raspberryPiLocations = new ArrayList<>();

            if (topology.cloudCapacityCount() > 0) {
                cloudLocations.add(
                        new GeoLocation(
                                cloudAnchorLatitude,
                                cloudAnchorLongitude));
            }

            for (int index = 1;
                 index < topology.cloudCapacityCount();
                 index++) {

                double latitude = region.equals("EU")
                        ? 45.0 + SeedSyncer.centralRnd.nextDouble() * 10.0
                        : 29.0 + SeedSyncer.centralRnd.nextDouble() * 17.0;

                double longitude = region.equals("EU")
                        ? -1.0 + SeedSyncer.centralRnd.nextDouble() * 16.0
                        : -123.0 + SeedSyncer.centralRnd.nextDouble() * 46.0;

                cloudLocations.add(
                        new GeoLocation(latitude, longitude));
            }

            for (int index = 0;
                 index < topology.fogCapacityCount();
                 index++) {

                double latitude = cloudAnchorLatitude
                        + (SeedSyncer.centralRnd.nextDouble() - 0.5) * 0.2;

                double longitude = cloudAnchorLongitude
                        + (SeedSyncer.centralRnd.nextDouble() - 0.5) * 0.2;

                fogLocations.add(
                        new GeoLocation(latitude, longitude));
            }

            for (int index = 0;
                 index < topology.edgeCapacityCount();
                 index++) {

                double latitude = cloudAnchorLatitude
                        + (SeedSyncer.centralRnd.nextDouble() - 0.5) * 0.2;

                double longitude = cloudAnchorLongitude
                        + (SeedSyncer.centralRnd.nextDouble() - 0.5) * 0.2;

                raspberryPiLocations.add(
                        new GeoLocation(latitude, longitude));
            }

            List<Capacity> capacities = new ArrayList<>();

            for (int capacityIndex = 0;
                 capacityIndex < topology.cloudCapacityCount();
                 capacityIndex++) {

                String nodeName = "CloudNode"
                        + (agentIndex + 1)
                        + "-"
                        + (capacityIndex + 1);

                int cpu =
                        24 + SeedSyncer.centralRnd.nextInt(17);

                int memoryGb =
                        64 + SeedSyncer.centralRnd.nextInt(33);

                int storageGb =
                        SeedSyncer.centralRnd.nextBoolean()
                                ? 512
                                : 1_024;

                long bandwidth =
                        250_000L
                                + SeedSyncer.centralRnd.nextInt(1_000_001);

                int latency =
                        15 + SeedSyncer.centralRnd.nextInt(56);

                double minimumPower = cpu * 0.5;
                double idlePower = cpu * 3.0;
                double maximumPower = cpu * 8.0;

                ComputingAppliance node = new ComputingAppliance(
                        Config.createNode(
                                nodeName,
                                cpu,
                                memoryGb * ScenarioBase.GB_IN_BYTE,
                                storageGb * ScenarioBase.GB_IN_BYTE,
                                minimumPower,
                                idlePower,
                                maximumPower,
                                bandwidth,
                                latency,
                                sharedLatencyMap),
                        cloudLocations.get(capacityIndex),
                        region,
                        cloudProvider,
                        false);

                new EnergyDataCollector(
                        nodeName + "-energy",
                        node.iaas,
                        true,
                        true,
                        () -> hasRunningApplication(node));

                capacities.add(
                        new Capacity(
                                node,
                                cpu,
                                memoryGb * ScenarioBase.GB_IN_BYTE,
                                storageGb * ScenarioBase.GB_IN_BYTE));
            }

            for (int capacityIndex = 0;
                 capacityIndex < topology.fogCapacityCount();
                 capacityIndex++) {

                String nodeName = "LaptopNode"
                        + (agentIndex + 1)
                        + "-"
                        + (capacityIndex + 1);

                int cpu =
                        12 + SeedSyncer.centralRnd.nextInt(13);

                int memoryGb =
                        SeedSyncer.centralRnd.nextBoolean()
                                ? 16
                                : 32;

                int storageGb =
                        SeedSyncer.centralRnd.nextBoolean()
                                ? 256
                                : 512;

                long bandwidth =
                        75_000L
                                + SeedSyncer.centralRnd.nextInt(175_001);

                int latency =
                        15 + SeedSyncer.centralRnd.nextInt(56);

                double minimumPower = 3.0;
                double idlePower = 8.0 + cpu * 0.4;
                double maximumPower = 30.0 + cpu * 3.0;

                ComputingAppliance node = new ComputingAppliance(
                        Config.createNode(
                                nodeName,
                                cpu,
                                memoryGb * ScenarioBase.GB_IN_BYTE,
                                storageGb * ScenarioBase.GB_IN_BYTE,
                                minimumPower,
                                idlePower,
                                maximumPower,
                                bandwidth,
                                latency,
                                sharedLatencyMap),
                        fogLocations.get(capacityIndex),
                        region,
                        "Laptop",
                        true);

                new EnergyDataCollector(
                        nodeName + "-energy",
                        node.iaas,
                        true,
                        true,
                        () -> hasRunningApplication(node));

                capacities.add(
                        new Capacity(
                                node,
                                cpu,
                                memoryGb * ScenarioBase.GB_IN_BYTE,
                                storageGb * ScenarioBase.GB_IN_BYTE));
            }

            for (int capacityIndex = 0;
                 capacityIndex < topology.edgeCapacityCount();
                 capacityIndex++) {

                String nodeName = "RaspberryPiNode"
                        + (agentIndex + 1)
                        + "-"
                        + (capacityIndex + 1);

                int cpu =
                        4 + SeedSyncer.centralRnd.nextInt(5);

                int memoryGb =
                        SeedSyncer.centralRnd.nextBoolean()
                                ? 4
                                : 8;

                int storageGb =
                        SeedSyncer.centralRnd.nextBoolean()
                                ? 64
                                : 128;

                long bandwidth =
                        50_000L
                                + SeedSyncer.centralRnd.nextInt(75_001);

                int latency =
                        15 + SeedSyncer.centralRnd.nextInt(56);

                double minimumPower = 1.5;
                double idlePower = 3.0;
                double maximumPower = 15.0;

                ComputingAppliance node = new ComputingAppliance(
                        Config.createNode(
                                nodeName,
                                cpu,
                                memoryGb * ScenarioBase.GB_IN_BYTE,
                                storageGb * ScenarioBase.GB_IN_BYTE,
                                minimumPower,
                                idlePower,
                                maximumPower,
                                bandwidth,
                                latency,
                                sharedLatencyMap),
                        raspberryPiLocations.get(capacityIndex),
                        region,
                        "RaspberryPi",
                        true);

                new EnergyDataCollector(
                        nodeName + "-energy",
                        node.iaas,
                        true,
                        true,
                        () -> hasRunningApplication(node));

                capacities.add(
                        new Capacity(
                                node,
                                cpu,
                                memoryGb * ScenarioBase.GB_IN_BYTE,
                                storageGb * ScenarioBase.GB_IN_BYTE));
            }

            double initialHourlyPrice =
                    1.0 + agentIndex % 4 * 0.25;

            ResourceAgent resourceAgent = new ResourceAgent(
                    "Agent" + (agentIndex + 1),
                    initialHourlyPrice,
                    (MappingStrategy)
                            Config.DUMMY_CONFIGURATION.get("mappingStrategy"),
                    new FloodingMessagingStrategy());

            resourceAgent.initResourceAgent(
                    resourceAgentVa,
                    resourceAgentArc,
                    capacities.toArray(new Capacity[0]));
        }
    }

}