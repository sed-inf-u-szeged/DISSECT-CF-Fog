package hu.u_szeged.inf.fog.simulator.agent.util;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.Sun;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.management.GreedyNoiseSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
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

    public final PrintWriter hourlyPriceWriter;

    private ResourceAgentCsvExporter() {
        // TODO: check if NoiseAppCsvExporter also needs this fix!
        this.resourceAgents = new ArrayList<>(ResourceAgent.allResourceAgents.values());

        try {
            hourlyPricePath = Paths.get(
                    ScenarioBase.RESULT_DIRECTORY,
                     "ra-hourly-price.csv"
            );

            hourlyPriceWriter = new PrintWriter(
                    Files.newBufferedWriter(hourlyPricePath, StandardCharsets.UTF_8,
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
    }

    public void log() {
        double time = Timed.getFireCount() / (double) ScenarioBase.HOUR_IN_MILLISECONDS;

        if (!headerWritten) {
            String header = generateHeader();
            hourlyPriceWriter.println(header);
            headerWritten = true;
        }

        StringBuilder rowForHourlyPrice = new StringBuilder();
        rowForHourlyPrice.append(String.format(Locale.ROOT, "%.3f", time));

        for (ResourceAgent ra : resourceAgents) {
            rowForHourlyPrice.append(",");
            rowForHourlyPrice.append(ra.hourlyPrice);
        }

        hourlyPriceWriter.println(rowForHourlyPrice);
    }
}