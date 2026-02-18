package hu.u_szeged.inf.fog.simulator.agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class AppDescriptionGenerator {

    private static final String[] PROVIDERS = {"AWS", "Azure"};
    private static final String[] LOCATIONS = {"EU", "US"};
    private static final String[] KINDS = {"server"};
    private static final Random random = new Random();
    
    public static void main(String[] args) throws IOException {
        String appName = "App-" + random.nextInt(10_000);
        generate(appName, 3, ScenarioBase.RESOURCE_PATH + "AGENT_examples");
    }
    
    public static void generate(String appName, int componentCount, String outputDir) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        root.put("name", appName);
        root.put("type", "dummy");
        root.put("energy", random.nextInt(11) / 10.0);
        root.put("price", random.nextInt(11) / 10.0);
        root.put("latency", random.nextInt(11) / 10.0);
        root.put("bandwidth", random.nextInt(11) / 10.0);

        ArrayNode components = mapper.createArrayNode();

        for (int i = 1; i <= componentCount; i++) {
            ObjectNode component = mapper.createObjectNode();
            component.put("id", String.valueOf(i));

            // requirements 
            ObjectNode requirements = mapper.createObjectNode();

            boolean includeCpu = random.nextBoolean();
            boolean includeStorage = random.nextBoolean();

            if (!includeCpu && !includeStorage) {
                includeCpu = true;
            }

            if (includeCpu) {
                requirements.put("cpu", random.nextInt(8) + 1);
                requirements.put("memory", (random.nextInt(8) + 1) * ScenarioBase.GB_IN_BYTE);
            }

            if (includeStorage) {
                requirements.put("storage", (random.nextInt(8) + 1) * ScenarioBase.GB_IN_BYTE); 
            }

            if (random.nextBoolean()) {
                requirements.put("location", LOCATIONS[random.nextInt(LOCATIONS.length)]);
            }

            if (random.nextBoolean()) {
                requirements.put("provider", PROVIDERS[random.nextInt(PROVIDERS.length)]);
            }

            requirements.put("edge", random.nextBoolean());

            component.set("requirements", requirements);

            // properties
            ObjectNode properties = mapper.createObjectNode();
            properties.put("kind", KINDS[random.nextInt(KINDS.length)]);
            properties.put("image", (long) ((random.nextInt(4) + 1) * 0.5 * ScenarioBase.GB_IN_BYTE));

            component.set("properties", properties);

            components.add(component);
        }

        root.set("components", components);
        
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputDir + File.separator + appName + ".json"), root);
    }
}
