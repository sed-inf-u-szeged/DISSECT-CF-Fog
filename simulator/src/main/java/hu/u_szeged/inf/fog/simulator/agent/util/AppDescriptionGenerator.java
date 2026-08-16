package hu.u_szeged.inf.fog.simulator.agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public class AppDescriptionGenerator {

    private static final String[] PROVIDERS = {"AWS", "Azure"};
    private static final String[] LOCATIONS = {"EU", "US"};

    public static void main(String[] args) throws IOException {
        // global settings
        String applicationNamePrefix = "App";
        int applicationCount = 1;
        int componentCount = 3;
        long randomSeed = 1L;
        String outputDirectory = ScenarioBase.RESOURCE_PATH + "AGENT_examples";

        // QoS weights [0..1]
        double energyWeight = 0.5;
        double priceWeight = 0.4;
        double latencyWeight = 0.7;
        double bandwidthWeight = 0.7;

        // hard requirements
        int minProviderCount = 1;
        int maxProviderCount = 3;
        double maxCost = 100.0;
        double maxLatency = 50.0;
        double minBandwidth = 100.0;
        double maxEnergyConsumption = 2_000.0;

        // max. resource requirement per component
        int maximumCpu = 8;
        int maximumMemoryGb = 8;
        int maximumStorageGb = 8;
        int maximumImageSizeGb = 2;
        //Boolean edgeRequirement = true;  // only edge
        //Boolean edgeRequirement = false; // only non-edge
        Boolean edgeRequirement = null;  // any node

        ObjectMapper mapper = new ObjectMapper();
        Random random = new Random(randomSeed);

        Files.createDirectories(Path.of(outputDirectory));

        for (int applicationIndex = 1; applicationIndex <= applicationCount; applicationIndex++) {
            String applicationName = applicationNamePrefix + "-" + applicationIndex;

            ObjectNode root = mapper.createObjectNode();

            root.put("name", applicationName);
            root.put("type", "dummy");

            root.put("energy", energyWeight);
            root.put("price", priceWeight);
            root.put("latency", latencyWeight);
            root.put("bandwidth", bandwidthWeight);

            root.put("minProviderCount", minProviderCount);
            root.put("maxProviderCount", maxProviderCount);
            root.put("maxCost", maxCost);
            root.put("maxLatency", maxLatency);
            root.put("minBandwidth", minBandwidth);
            root.put("maxEnergyConsumption", maxEnergyConsumption);

            ArrayNode components = mapper.createArrayNode();

            for (int componentIndex = 1; componentIndex <= componentCount; componentIndex++) {
                ObjectNode component = mapper.createObjectNode();
                component.put("id", String.valueOf(componentIndex));

                ObjectNode requirements = mapper.createObjectNode();

                requirements.put("cpu", random.nextInt(maximumCpu) + 1);
                requirements.put("memory", (random.nextInt(maximumMemoryGb) + 1L) * ScenarioBase.GB_IN_BYTE);

                if (random.nextBoolean()) {
                    requirements.put("storage", (random.nextInt(maximumStorageGb) + 1L) * ScenarioBase.GB_IN_BYTE);
                }

                if (random.nextBoolean()) {
                    requirements.put("location", LOCATIONS[random.nextInt(LOCATIONS.length)]);
                }

                if (random.nextBoolean()) {
                    requirements.put("provider", PROVIDERS[random.nextInt(PROVIDERS.length)]);
                }

                if (edgeRequirement != null) {
                    requirements.put("edge", edgeRequirement);
                }

                ObjectNode properties = mapper.createObjectNode();

                properties.put("kind", "server");

                long imageSizeStep = ScenarioBase.GB_IN_BYTE / 2;
                int maximumImageSteps = maximumImageSizeGb * 2;
                long imageSize = (random.nextInt(maximumImageSteps) + 1L) * imageSizeStep;

                properties.put("image", imageSize);

                component.set("requirements", requirements);
                component.set("properties", properties);
                components.add(component);
            }

            root.set("components", components);

            File outputFile = Path.of(outputDirectory, applicationName + ".json").toFile();
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, root);
        }
    }
}