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
import java.util.*;

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

    private final LocalMetricsCalculator localMetricsCalculator = new LocalMetricsCalculator();

    private ResourceAgentCsvExporter() {
        // TODO: check if NoiseAppCsvExporter also needs this fix!
        this.resourceAgents = new ArrayList<>(ResourceAgent.allResourceAgents.values());

        try {
            hourlyPricePath = Paths.get(ScenarioBase.RESULT_DIRECTORY,"ra-hourly-price.csv");
            resourceMetricsPath = Paths.get(ScenarioBase.RESULT_DIRECTORY, "ra-resource-metrics.csv");

            hourlyPriceWriter = new PrintWriter(
                    Files.newBufferedWriter(hourlyPricePath, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    true
            );
            resourceUtilityWriter = new PrintWriter(
                    Files.newBufferedWriter(resourceMetricsPath, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    true
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String generateHeader() {
        List<String> names = new ArrayList<>();
        for (ResourceAgent ra : resourceAgents) {
            names.add(ra.name);
        }

        return "time" + "," + String.join(",", names);
    }

    @Override
    public void close() throws IOException {
        hourlyPriceWriter.close();
        resourceUtilityWriter.close();
    }

    public void log() {
        double time = Timed.getFireCount() / (double) ScenarioBase.HOUR_IN_MILLISECONDS;

        if (!headerWritten) {
            String header = generateHeader();
            hourlyPriceWriter.println(header);
            resourceUtilityWriter.println(header);
            headerWritten = true;
        }

        StringBuilder rowForHourlyPrice = new StringBuilder();
        StringBuilder rowForResourceUtility = new StringBuilder();

        rowForHourlyPrice.append(String.format(Locale.ROOT, "%.3f", time));
        rowForResourceUtility.append(String.format(Locale.ROOT, "%.3f", time));

        for (ResourceAgent ra : resourceAgents) {
            rowForHourlyPrice.append(",");
            rowForHourlyPrice.append(ra.hourlyPrice);

            double balance = localMetricsCalculator.calculateCurrentBalance(ra);
            double utilisation = localMetricsCalculator.calculateCurrentUtilisation(ra);
            double fragmentation = localMetricsCalculator.calculateCurrentCapacityFragmentation(ra);
            double compactness = localMetricsCalculator.calculateCurrentCompactness(ra);
            double resourceUtility = (balance + utilisation + (1.0 - fragmentation) + compactness) / 4.0;

            rowForResourceUtility.append(",");
            rowForResourceUtility.append(resourceUtility);
        }

        hourlyPriceWriter.println(rowForHourlyPrice);
        resourceUtilityWriter.println(rowForResourceUtility);
    }
}