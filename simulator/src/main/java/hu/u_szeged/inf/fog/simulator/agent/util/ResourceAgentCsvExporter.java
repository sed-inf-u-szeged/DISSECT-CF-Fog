package hu.u_szeged.inf.fog.simulator.agent.util;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.mapping.offer.LocalMetricsCalculator;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ResourceAgentCsvExporter implements Closeable {

    private static ResourceAgentCsvExporter instance;

    public static ResourceAgentCsvExporter getInstance() {
        if (instance == null) {
            instance = new ResourceAgentCsvExporter();
        }

        return instance;
    }

    private final List<ResourceAgent> resourceAgents;

    private boolean headerWritten;

    public final Path hourlyPricePath;
    public final Path resourceMetricsPath;

    public final PrintWriter hourlyPriceWriter;
    public final PrintWriter resourceUtilityWriter;

    private double weightedProviderQualitySum;
    private double providerQualityWeightSum;

    private final LocalMetricsCalculator localMetricsCalculator = new LocalMetricsCalculator();

    private ResourceAgentCsvExporter() {
        this.resourceAgents = ResourceAgent.allResourceAgents.values().stream()
                .sorted(Comparator.comparing(resourceAgent -> resourceAgent.name))
                .toList();

        try {
            hourlyPricePath = Paths.get(
                    ScenarioBase.RESULT_DIRECTORY,
                    "ra-hourly-price.csv");

            resourceMetricsPath = Paths.get(
                    ScenarioBase.RESULT_DIRECTORY,
                    "ra-resource-metrics.csv");

            hourlyPriceWriter = new PrintWriter(
                    Files.newBufferedWriter(
                            hourlyPricePath,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND),
                    true);

            resourceUtilityWriter = new PrintWriter(
                    Files.newBufferedWriter(
                            resourceMetricsPath,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND),
                    true);

        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private String generateHeader() {
        List<String> columns = new ArrayList<>();
        columns.add("time");

        for (ResourceAgent resourceAgent : resourceAgents) {
            columns.add(resourceAgent.name);
        }

        return String.join(",", columns);
    }

    private String generateResourceUtilityHeader() {
        List<String> columns = new ArrayList<>();
        columns.add("time");

        for (ResourceAgent resourceAgent : resourceAgents) {
            columns.add(resourceAgent.name + "-Balance");
            columns.add(resourceAgent.name + "-Utilisation");
            columns.add(resourceAgent.name + "-Fragmentation");
            columns.add(resourceAgent.name + "-Compactness");
            columns.add(resourceAgent.name + "-Utility");
        }

        columns.add("Average-Balance");
        columns.add("Average-Utilisation");
        columns.add("Average-Fragmentation");
        columns.add("Average-Compactness");
        columns.add("Average-Utility");

        return String.join(",", columns);
    }

    @Override
    public void close() throws IOException {
        hourlyPriceWriter.close();
        resourceUtilityWriter.close();
    }

    public void log() {
        double time = Timed.getFireCount() / (double) ScenarioBase.HOUR_IN_MILLISECONDS;

        if (!headerWritten) {
            hourlyPriceWriter.println(generateHeader());
            resourceUtilityWriter.println(generateResourceUtilityHeader());
            headerWritten = true;
        }

        StringBuilder rowForHourlyPrice = new StringBuilder();
        StringBuilder rowForResourceUtility = new StringBuilder();

        rowForHourlyPrice.append(String.format(Locale.ROOT, "%.3f", time));
        rowForResourceUtility.append(String.format(Locale.ROOT, "%.3f", time));

        double currentBalanceSum = 0.0;
        double currentUtilisationSum = 0.0;
        double currentFragmentationSum = 0.0;
        double currentCompactnessSum = 0.0;
        double currentResourceUtilitySum = 0.0;

        for (ResourceAgent resourceAgent : resourceAgents) {
            rowForHourlyPrice.append(",");
            rowForHourlyPrice.append(resourceAgent.hourlyPrice);

            double balance = localMetricsCalculator.calculateCurrentBalance(resourceAgent);
            double utilisation = localMetricsCalculator.calculateCurrentUtilisation(resourceAgent);
            double fragmentation =
                    localMetricsCalculator.calculateCurrentCapacityFragmentation(resourceAgent);
            double compactness = localMetricsCalculator.calculateCurrentCompactness(resourceAgent);
            double utility = localMetricsCalculator.calculateCurrentResourceUtility(resourceAgent);

            double consolidation = ((1.0 - fragmentation) + compactness) / 2.0;
            double providerQuality = (balance + consolidation) / 2.0;

            weightedProviderQualitySum += utilisation * providerQuality;
            providerQualityWeightSum += utilisation;

            currentBalanceSum += balance;
            currentUtilisationSum += utilisation;
            currentFragmentationSum += fragmentation;
            currentCompactnessSum += compactness;
            currentResourceUtilitySum += utility;

            rowForResourceUtility.append(",");
            rowForResourceUtility.append(balance);
            rowForResourceUtility.append(",");
            rowForResourceUtility.append(utilisation);
            rowForResourceUtility.append(",");
            rowForResourceUtility.append(fragmentation);
            rowForResourceUtility.append(",");
            rowForResourceUtility.append(compactness);
            rowForResourceUtility.append(",");
            rowForResourceUtility.append(utility);
        }

        int resourceAgentCount = resourceAgents.size();

        double averageBalance =
                resourceAgentCount == 0 ? 0.0 : currentBalanceSum / resourceAgentCount;

        double averageUtilisation =
                resourceAgentCount == 0 ? 0.0 : currentUtilisationSum / resourceAgentCount;

        double averageFragmentation =
                resourceAgentCount == 0 ? 0.0 : currentFragmentationSum / resourceAgentCount;

        double averageCompactness =
                resourceAgentCount == 0 ? 0.0 : currentCompactnessSum / resourceAgentCount;

        double averageUtility =
                resourceAgentCount == 0 ? 0.0 : currentResourceUtilitySum / resourceAgentCount;

        rowForResourceUtility.append(",");
        rowForResourceUtility.append(averageBalance);
        rowForResourceUtility.append(",");
        rowForResourceUtility.append(averageUtilisation);
        rowForResourceUtility.append(",");
        rowForResourceUtility.append(averageFragmentation);
        rowForResourceUtility.append(",");
        rowForResourceUtility.append(averageCompactness);
        rowForResourceUtility.append(",");
        rowForResourceUtility.append(averageUtility);

        hourlyPriceWriter.println(rowForHourlyPrice);
        resourceUtilityWriter.println(rowForResourceUtility);
    }

    public double getProviderQuality() {
        if (providerQualityWeightSum == 0.0) {
            return 0.0;
        }

        return weightedProviderQualitySum / providerQualityWeightSum;
    }
}