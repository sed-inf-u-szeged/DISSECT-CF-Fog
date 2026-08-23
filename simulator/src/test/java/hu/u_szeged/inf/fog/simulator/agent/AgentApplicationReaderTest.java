package hu.u_szeged.inf.fog.simulator.agent;

import hu.u_szeged.inf.fog.simulator.agent.util.AgentApplicationReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentApplicationReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readJson_validApplication_returnsApplicationWithPrefixedComponentIds() throws IOException {
        String json = """
                {
                  "name": "SmartHome-7",
                  "components": [
                    {
                      "id": "1",
                      "requirements": { "cpu": 1.5, "memory": 512, "storage": 1000 },
                      "properties": { "kind": "sensor", "image": 11 }
                    },
                    {
                      "id": "2",
                      "requirements": { "cpu": 2.0, "memory": 1024, "storage": 2000 },
                      "properties": { "kind": "gateway_1", "image": 22 }
                    }
                  ]
                }
                """;
        Path jsonFile = writeJson("valid-app.json", json);

        AgentApplication application = AgentApplicationReader.readJson(jsonFile);

        assertEquals("SmartHome-7", application.name);
        assertEquals(2, application.components.size());
        assertEquals("SmartHome-7-sensor-1", application.components.get(0).id);
        assertEquals("SmartHome-7-gateway_1-2", application.components.get(1).id);
    }

    @Test
    void readJson_invalidApplicationName_throwsIllegalArgumentException() throws IOException {
        String json = """
                {
                  "name": "invalidName",
                  "components": [
                    {
                      "id": "1",
                      "requirements": { "cpu": 1.0, "memory": 128, "storage": 128 },
                      "properties": { "kind": "processor", "image": 1 }
                    }
                  ]
                }
                """;
        Path jsonFile = writeJson("invalid-name.json", json);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentApplicationReader.readJson(jsonFile));

        assertTrue(exception.getMessage().contains("Application name must follow"));
    }

    @Test
    void readJson_missingComponents_throwsIllegalArgumentException() throws IOException {
        String json = """
                {
                  "name": "App-1"
                }
                """;
        Path jsonFile = writeJson("missing-components.json", json);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentApplicationReader.readJson(jsonFile));

        assertEquals("Application must define at least one component.", exception.getMessage());
    }

    @Test
    void readJson_emptyComponents_throwsIllegalArgumentException() throws IOException {
        String json = """
                {
                  "name": "App-1",
                  "components": []
                }
                """;
        Path jsonFile = writeJson("empty-components.json", json);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentApplicationReader.readJson(jsonFile));

        assertEquals("Application must define at least one component.", exception.getMessage());
    }

    @Test
    void readJson_invalidComponentKind_throwsIllegalArgumentException() throws IOException {
        String json = """
                {
                  "name": "App-1",
                  "components": [
                    {
                      "id": "1",
                      "requirements": { "cpu": 1.0, "memory": 128, "storage": 128 },
                      "properties": { "kind": "1invalid", "image": 1 }
                    }
                  ]
                }
                """;
        Path jsonFile = writeJson("invalid-kind.json", json);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentApplicationReader.readJson(jsonFile));

        assertTrue(exception.getMessage().contains("Component kind must start with a letter"));
    }

    @Test
    void readJson_invalidComponentId_throwsIllegalArgumentException() throws IOException {
        String json = """
                {
                  "name": "App-1",
                  "components": [
                    {
                      "id": "0",
                      "requirements": { "cpu": 1.0, "memory": 128, "storage": 128 },
                      "properties": { "kind": "processor", "image": 1 }
                    }
                  ]
                }
                """;
        Path jsonFile = writeJson("invalid-id.json", json);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentApplicationReader.readJson(jsonFile));

        assertTrue(exception.getMessage().contains("Component id must be a positive integer"));
    }

    @Test
    void readJson_missingRequirements_throwsIllegalArgumentException() throws IOException {
        String json = """
                {
                  "name": "App-1",
                  "components": [
                    {
                      "id": "1",
                      "properties": { "kind": "processor", "image": 1 }
                    }
                  ]
                }
                """;
        Path jsonFile = writeJson("missing-requirements.json", json);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentApplicationReader.readJson(jsonFile));

        assertTrue(exception.getMessage().contains("has no requirements"));
    }

    @Test
    void readJson_nonPositiveCpuRequirement_throwsIllegalArgumentException() throws IOException {
        String json = """
                {
                  "name": "App-1",
                  "components": [
                    {
                      "id": "1",
                      "requirements": { "cpu": 0.0, "memory": 128, "storage": 128 },
                      "properties": { "kind": "processor", "image": 1 }
                    }
                  ]
                }
                """;
        Path jsonFile = writeJson("invalid-cpu.json", json);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentApplicationReader.readJson(jsonFile));

        assertTrue(exception.getMessage().contains("must define a positive CPU requirement"));
    }

    @Test
    void readJson_nonPositiveMemoryRequirement_throwsIllegalArgumentException() throws IOException {
        String json = """
                {
                  "name": "App-1",
                  "components": [
                    {
                      "id": "1",
                      "requirements": { "cpu": 1.0, "memory": 0, "storage": 128 },
                      "properties": { "kind": "processor", "image": 1 }
                    }
                  ]
                }
                """;
        Path jsonFile = writeJson("invalid-memory.json", json);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentApplicationReader.readJson(jsonFile));

        assertTrue(exception.getMessage().contains("must define a positive memory requirement"));
    }

    @Test
    void readJson_nonPositiveStorageRequirement_throwsIllegalArgumentException() throws IOException {
        String json = """
                {
                  "name": "App-1",
                  "components": [
                    {
                      "id": "1",
                      "requirements": { "cpu": 1.0, "memory": 128, "storage": 0 },
                      "properties": { "kind": "processor", "image": 1 }
                    }
                  ]
                }
                """;
        Path jsonFile = writeJson("invalid-storage.json", json);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentApplicationReader.readJson(jsonFile));

        assertTrue(exception.getMessage().contains("must define a positive storage requirement"));
    }

    @Test
    void readJson_nonExistingFile_throwsIllegalStateException() {
        Path missingFile = tempDir.resolve("missing-app.json");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> AgentApplicationReader.readJson(missingFile));

        assertTrue(exception.getMessage().contains("Failed to read application description from"));
    }

    private Path writeJson(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
