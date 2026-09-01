package hu.u_szeged.inf.fog.simulator.agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

public class AppDescriptionGenerator {

    private static final String[] PROVIDERS = {"AWS", "Azure"};
    private static final String[] LOCATIONS = {"EU", "US"};

    public static void main(String[] args) throws IOException {
        // Workload settings
        String applicationNamePrefix = "App";
        int applicationCount = 6;

        int minimumComponentCount = 4;
        int maximumComponentCount = 4;

        long randomSeed = 1L;

        String outputDirectory = ScenarioBase.RESOURCE_PATH + "AGENT_examples/scen2-energy/";

        // Application Owner QoS weights
        double energyWeight = 0.7;
        double priceWeight = 0.1;
        double latencyWeight = 0.1;
        double bandwidthWeight = 0.1;

        // Application-level hard requirements
        int minProviderCount = 1;
        int maxProviderCount = 4;

        // Permissive QoS limits used primarily as normalization references in Scenario 1
        double maxCost = 10.0;
        double maxLatency = 70.0;
        double minBandwidth = 50_000.0;
        double maxEnergyConsumption = 3_000.0;

        // Component resource requirement ranges
        int minimumCpu = 3;
        int maximumCpu = 9;

        int minimumMemoryGb = 4;
        int maximumMemoryGb = 14;

        int minimumStorageGb = 32;
        int maximumStorageGb = 160;

        double minimumImageSizeGb = 0.5;
        double maximumImageSizeGb = 2.0;

        // Optional component-level placement constraints
        double providerRequirementProbability = 0.0; // 0.0 disables provider requirements.
        double locationRequirementProbability = 0.0; // 0.0 disables location requirements.

        double edgeOnlyApplicationRatio = 0.0; // Applications containing edge=true components.
        double nonEdgeOnlyApplicationRatio = 0.0; // Applications containing edge=false components.
        int edgeConstrainedComponentsPerApplication = 1;

        ObjectMapper mapper = new ObjectMapper();
        Random random = new Random(randomSeed);

        Path outputPath = Path.of(outputDirectory);

        Files.createDirectories(outputPath);

        try (var existingFiles = Files.list(outputPath)) {
            List<Path> generatedApplicationFiles =
                    existingFiles
                            .filter(Files::isRegularFile)
                            .filter(path -> {
                                String fileName =
                                        path.getFileName().toString();

                                return fileName.startsWith(
                                        applicationNamePrefix + "-")
                                        && fileName.endsWith(".json");
                            })
                            .toList();

            for (Path generatedApplicationFile
                    : generatedApplicationFiles) {

                Files.delete(generatedApplicationFile);
            }
        }

        for (int applicationIndex = 1; applicationIndex <= applicationCount; applicationIndex++) {
            String applicationName = applicationNamePrefix + "-" + String.format("%03d", applicationIndex);

            int componentCount;
            if (minimumComponentCount == maximumComponentCount) {
                componentCount = minimumComponentCount;
            } else {
                componentCount =
                        minimumComponentCount
                                + random.nextInt(
                                maximumComponentCount
                                        - minimumComponentCount
                                        + 1);
            }

            double edgeConstraintSelection = random.nextDouble();
            Boolean applicationEdgeRequirement = null;

            if (edgeConstraintSelection < edgeOnlyApplicationRatio) {
                applicationEdgeRequirement = true;
            } else if (edgeConstraintSelection < edgeOnlyApplicationRatio + nonEdgeOnlyApplicationRatio) {
                applicationEdgeRequirement = false;
            }

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

                int cpu = minimumCpu + random.nextInt(maximumCpu - minimumCpu + 1);
                int memoryGb = minimumMemoryGb + random.nextInt(maximumMemoryGb - minimumMemoryGb + 1);
                int storageGb = minimumStorageGb + random.nextInt(maximumStorageGb - minimumStorageGb + 1);

                requirements.put("cpu", cpu);
                requirements.put("memory", memoryGb * ScenarioBase.GB_IN_BYTE);
                requirements.put("storage", storageGb * ScenarioBase.GB_IN_BYTE);

                if (random.nextDouble() < locationRequirementProbability) {
                    String location = LOCATIONS[random.nextInt(LOCATIONS.length)];
                    requirements.put("location", location);
                }

                if (random.nextDouble() < providerRequirementProbability) {
                    String provider = PROVIDERS[random.nextInt(PROVIDERS.length)];
                    requirements.put("provider", provider);
                }

                if (applicationEdgeRequirement != null
                        && componentIndex <= edgeConstrainedComponentsPerApplication) {

                    requirements.put("edge", applicationEdgeRequirement);
                }

                ObjectNode properties = mapper.createObjectNode();
                properties.put("kind", "server");

                int minimumImageSteps = (int) Math.round(minimumImageSizeGb * 2.0);
                int maximumImageSteps = (int) Math.round(maximumImageSizeGb * 2.0);
                int imageSizeSteps = minimumImageSteps + random.nextInt(maximumImageSteps - minimumImageSteps + 1);
                long imageSize = imageSizeSteps * (ScenarioBase.GB_IN_BYTE / 2);

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