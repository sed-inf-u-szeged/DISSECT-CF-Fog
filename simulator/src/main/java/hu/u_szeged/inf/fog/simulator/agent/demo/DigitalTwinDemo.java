package hu.u_szeged.inf.fog.simulator.agent.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public class DigitalTwinDemo {

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode input = mapper.readTree(System.in);

        Thread.sleep(5000);

        Map<String, Object> result = new LinkedHashMap<>();
        String applicationType = input.path("metadata").path("application_type").asText(null);

        switch (applicationType) {
            case "InnoRenew":
                result.put("status", "ok");
                result.put("type", applicationType);
                result.put("scenario_id", input.path("scenario_id").asText(null));
                result.put("action_count", input.path("actions").size());

                break;

            default:
                System.err.println("Unknown digital twin type: " + applicationType);
                System.exit(1);
                return;
        }

        mapper.writeValue(System.out, result);
    }
}