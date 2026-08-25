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
        // Workload settings
        String applicationNamePrefix = "App";
        int applicationCount = 60;
        int componentCount = 4;
        long randomSeed = 1L;

        String outputDirectory = ScenarioBase.RESOURCE_PATH + "AGENT_examples/scen1/";

        // Application Owner QoS weights
        double energyWeight = 0.25;
        double priceWeight = 0.25;
        double latencyWeight = 0.25;
        double bandwidthWeight = 0.25;

        // Application-level hard requirements
        int minProviderCount = 1;
        int maxProviderCount = 4;

        // Permissive QoS limits used primarily as normalization references in Scenario 1
        double maxCost = 10.0;
        double maxLatency = 70.0;
        double minBandwidth = 50_000.0;
        double maxEnergyConsumption = 3_000.0;

        // Component resource requirement ranges
        int maximumCpu = 4; // 1-4 CPU.
        int maximumMemoryGb = 4; // 1-4 GB.
        int maximumStorageGb = 8; // 1-8 GB.
        int maximumImageSizeGb = 2; // 0.5-2 GB.

        // Optional component-level placement constraints
        double providerRequirementProbability = 0.0; // 0.0 disables provider requirements.
        double locationRequirementProbability = 0.0; // 0.0 disables location requirements.

        double edgeOnlyApplicationRatio = 0.0; // Applications containing edge=true components.
        double nonEdgeOnlyApplicationRatio = 0.0; // Applications containing edge=false components.
        int edgeConstrainedComponentsPerApplication = 1;

        ObjectMapper mapper = new ObjectMapper();
        Random random = new Random(randomSeed);

        Files.createDirectories(Path.of(outputDirectory));

        for (int applicationIndex = 1; applicationIndex <= applicationCount; applicationIndex++) {
            String applicationName = applicationNamePrefix + "-" + String.format("%03d", applicationIndex);

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

                requirements.put("cpu", random.nextInt(maximumCpu) + 1);

                requirements.put(
                        "memory",
                        (random.nextInt(maximumMemoryGb) + 1L) * ScenarioBase.GB_IN_BYTE);

                requirements.put(
                        "storage",
                        (random.nextInt(maximumStorageGb) + 1L) * ScenarioBase.GB_IN_BYTE);

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