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

    private static final String APPLICATION_NAME_PATTERN = "[A-Za-z][A-Za-z0-9_]*-[1-9][0-9]*";

    private static final String COMPONENT_KIND_PATTERN = "[A-Za-z][A-Za-z0-9_]*";

    private static final String COMPONENT_ID_PATTERN = "[1-9][0-9]*";

    private AgentApplicationReader() {}

    /**
     * Reads and validates an {@link AgentApplication} from the given JSON file.
     *
     * @param filepath path to the JSON application description file
     * @return the validated application with globally unique component ids
     */
    public static AgentApplication readJson(Path filepath) {
        try {
            AgentApplication application = objectMapper.readValue(filepath.toFile(), AgentApplication.class);

            validateApplicationName(application.name);
            validateComponents(application);
            validateComponentNames(application);
            validateComponentRequirements(application);
            assignApplicationAndComponentNames(application);

            return application;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read application description from " + filepath, e);
        }
    }

    /**
     * Validates the application name read from the JSON file.
     */
    private static void validateApplicationName(String applicationName) {
        if (applicationName == null || !applicationName.matches(APPLICATION_NAME_PATTERN)) {
            throw new IllegalArgumentException(
                    "Application name must follow the '<text>-<positive number>' format: " + applicationName);
        }
    }

    private static void validateComponents(AgentApplication application) {
        if (application.components == null || application.components.isEmpty()) {
            throw new IllegalArgumentException("Application must define at least one component.");
        }
    }

    /**
     * Validates the kind and original id of every component.
     */
    private static void validateComponentNames(AgentApplication application) {
        for (AgentApplication.Component component : application.components) {
            if (component.properties == null || component.properties.kind == null
                    || !component.properties.kind.matches(COMPONENT_KIND_PATTERN)) {
                throw new IllegalArgumentException(
                        "Component kind must start with a letter and contain only letters, digits, or underscores.");
            }

            if (component.id == null || !component.id.matches(COMPONENT_ID_PATTERN)) {
                throw new IllegalArgumentException(
                        "Component id must be a positive integer: " + component.id);
            }
        }
    }

    /**
     * Validates the resource requirements of every component.
     */
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
     * Creates globally unique component ids by prefixing them with the application name and component kind.
     */
    private static void assignApplicationAndComponentNames(AgentApplication application) {
        for (AgentApplication.Component component : application.components) {
            component.id = application.name + "-" + component.properties.kind + "-" + component.id;
        }
    }
}