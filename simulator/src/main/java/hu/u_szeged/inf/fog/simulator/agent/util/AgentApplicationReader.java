package hu.u_szeged.inf.fog.simulator.agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Utility class for reading an {@link AgentApplication} from a JSON file.
 */
public class AgentApplicationReader {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private AgentApplicationReader() {}
    
    /**
     * Reads an {@link AgentApplication} from the given JSON file path.
     *
     * @param filepath path to the JSON application description file
     */
    public static AgentApplication readJson(Path filepath) {
        try {
            AgentApplication agentApplication = objectMapper.readValue(filepath.toFile(), AgentApplication.class);
            validateComponentRequirements(agentApplication);

            String nameWithoutExtension = filepath.getFileName().toString().replaceFirst("\\.[^.]+$", "");
            reName(agentApplication, nameWithoutExtension);
            return agentApplication;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read application description from " + filepath, e);
        }        
    }

    private static void validateComponentRequirements(AgentApplication application) {
        for (AgentApplication.Component component : application.components) {
            if (component.requirements == null) {
                throw new IllegalArgumentException("Component '" + component.id + "' has no requirements.");
            }

            if (component.requirements.cpu == null || component.requirements.cpu <= 0.0) {
                throw new IllegalArgumentException(
                        "Component '" + component.id + "' must define a positive CPU requirement.");
            }

            if (component.requirements.memory == null || component.requirements.memory <= 0L) {
                throw new IllegalArgumentException(
                        "Component '" + component.id + "' must define a positive memory requirement.");
            }

            if (component.requirements.storage == null || component.requirements.storage <= 0L) {
                throw new IllegalArgumentException(
                        "Component '" + component.id + "' must define a positive storage requirement.");
            }
        }
    }

    /**
     * Prefixes component ids with the application name to ensure uniqueness.
     */
    public static void reName(AgentApplication agentApplication, String fileName) {
        agentApplication.name = fileName;
        for (AgentApplication.Component component : agentApplication.components) {
            component.id = agentApplication.name + "-" + component.properties.kind + "-" + component.id;
        }
    }
}