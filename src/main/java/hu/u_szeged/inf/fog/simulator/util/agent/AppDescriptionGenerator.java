package hu.u_szeged.inf.fog.simulator.util.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class AppDescriptionGenerator {
    
    private static final String[] PROVIDERS = {"AWS", "Azure"};
    private static final String[] LOCATIONS = {"EU", "US"};
    private static final Random RANDOM = new Random();
    private static final long GIGABYTE = 1_073_741_824L;

    public static void main(String[] args) throws IOException {
        String appName = "App-" + RANDOM.nextInt(Integer.MAX_VALUE);
        generateAppDescription(3, 1, 500, 6, 10, appName, ScenarioBase.resourcePath + "AGENT_examples");
    }

    public static void generateAppDescription(int numCompute, int numStorage, int imageSizeScaler, int cpuAndMemoryScaler, 
            int storageScaler, String appName, String outputDir) throws IOException {
        
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode appNode = mapper.createObjectNode();
        appNode.put("name", appName);
        appNode.put("energyPriority", 1.0);
        appNode.put("pricePriority", 0.0);
        appNode.put("latencyPriority", 0.0);
        appNode.put("bandwidthPriority", 0.0);
       
        ArrayNode components = mapper.createArrayNode();
        ArrayNode resources = mapper.createArrayNode();
        ArrayNode mappings = mapper.createArrayNode();

        int componentIndex = 1;

        // Compute components and resources
        for (int i = 0; i < numCompute; i++, componentIndex++) {
            ObjectNode component = mapper.createObjectNode();
            component.put("name", "Comp-" + componentIndex);
            component.put("type", "compute");
            component.put("image", String.valueOf((GIGABYTE / 1024) * (RANDOM.nextInt(imageSizeScaler) + 1)));

            ObjectNode resource = mapper.createObjectNode();
            resource.put("name", "Res-" + componentIndex);
            resource.put("cpu", String.valueOf(RANDOM.nextInt(cpuAndMemoryScaler) + 1));
            resource.put("memory", String.valueOf(GIGABYTE * (RANDOM.nextInt(cpuAndMemoryScaler) + 1)));
            if (RANDOM.nextBoolean()) {
                resource.put("instances", String.valueOf(RANDOM.nextInt(cpuAndMemoryScaler / 2) + 1));
            }
            if (RANDOM.nextBoolean()) {
                resource.put("provider", PROVIDERS[RANDOM.nextInt(PROVIDERS.length)]);
            }
            if (RANDOM.nextBoolean()) {
                resource.put("location", LOCATIONS[RANDOM.nextInt(LOCATIONS.length)]);
            }

            components.add(component);
            resources.add(resource);

            // Mapping
            ObjectNode mapping = mapper.createObjectNode();
            mapping.put("component", "Comp-" + componentIndex);
            mapping.put("resource", "Res-" + componentIndex);
            mappings.add(mapping);
        }

        // Storage components and resources
        for (int i = 0; i < numStorage; i++, componentIndex++) {
            ObjectNode component = mapper.createObjectNode();
            component.put("name", "Comp-" + componentIndex);
            component.put("type", "storage");

            ObjectNode resource = mapper.createObjectNode();
            resource.put("name", "Res-" + componentIndex);
            resource.put("size", String.valueOf(GIGABYTE * (RANDOM.nextInt(storageScaler) + 1)));
            if (RANDOM.nextBoolean()) {
                resource.put("provider", PROVIDERS[RANDOM.nextInt(PROVIDERS.length)]);
            }
            if (RANDOM.nextBoolean()) {
                resource.put("location", LOCATIONS[RANDOM.nextInt(LOCATIONS.length)]);
            }
            
            components.add(component);
            resources.add(resource);

            // Mapping
            ObjectNode mapping = mapper.createObjectNode();
            mapping.put("component", "Comp-" + componentIndex);
            mapping.put("resource", "Res-" + componentIndex);
            mappings.add(mapping);
        }

        appNode.set("components", components);
        appNode.set("resources", resources);
        appNode.set("mapping", mappings);

        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputDir + File.separator + appName + ".json"), appNode);
    }
}