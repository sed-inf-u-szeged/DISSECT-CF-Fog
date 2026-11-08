package hu.u_szeged.inf.fog.simulator.demo.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks duration compatibility, configuration bounds and actual simulator completion. */
class MobilityDurationTest {
    @TempDir
    Path temporary;

    private MobilityScenarioConfig config(String text) throws Exception {
        Path path = temporary.resolve("scenario.properties");
        Files.writeString(path, text);
        return MobilityScenarioConfig.load(path);
    }

    @Test
    void omittedDurationKeepsOriginalObservationAndDrainLimits() {
        MobilityScenarioConfig config = MobilityScenarioConfig.defaults();
        assertEquals(121, config.observationCount());
        assertEquals(7_200_000, config.durationMillis());
        assertEquals(14_400_000, config.simulationLimitMillis());
    }

    @Test
    void durationRoundTripsForBothOfficialStrategiesAndAcceptsBounds() throws Exception {
        for (String strategy : new String[] {
                "mobility_strategy=nomadic\nwaypoints_m=0,0;1600,0;1600,600\n",
                "mobility_strategy=random_walk\n"}) {
            for (int minutes : new int[] {10, 232, 1440}) {
                MobilityScenarioConfig original = config(strategy + "duration_minutes=" + minutes);
                assertEquals(minutes + 1, original.observationCount());
                assertEquals(minutes * 60_000L, original.durationMillis());
                original.save(temporary);
                MobilityScenarioConfig restored = MobilityScenarioConfig.load(
                        temporary.resolve("scenario-effective.properties"));
                assertEquals(original.metadata(), restored.metadata());
                assertEquals(original.movement(original.simulationLimitMillis()).getClass(),
                        restored.movement(restored.simulationLimitMillis()).getClass());
            }
        }
    }

    @Test
    void invalidDurationCannotSilentlyUseDefault() {
        for (String value : new String[] {"", "0", "9", "1441", "232.5", "NaN", "999999999999"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> config("mobility_strategy=random_walk\nduration_minutes=" + value), value);
        }
    }

    @Test
    void longOfficialRunsHave233ObservationsAndConserveAllGeneratedBytes() throws Exception {
        Path resources = Path.of("src/main/resources/demo").toAbsolutePath();
        if (!Files.isDirectory(resources)) {
            resources = Path.of("simulator/src/main/resources/demo").toAbsolutePath();
        }
        for (String name : new String[] {"nomadic", "random-walk"}) {
            Path scenario = temporary.resolve(name + ".properties");
            Files.writeString(scenario, Files.readString(resources.resolve("mobility/official-" + name + ".properties"))
                    + "\nduration_minutes=232\n");
            Path output = temporary.resolve(name);
            Path log = temporary.resolve(name + ".log");
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            ProcessBuilder builder = new ProcessBuilder(java, "-cp", classpath,
                    MobilityPredictionSimulation.class.getName(), "--no-ui", "--scenario=" + scenario,
                    resources.resolve("LPDS_original.xml").toString(), output.toString());
            builder.environment().remove("MOBILITY_SHADOW_PYTHON");
            builder.environment().remove("MOBILITY_SHADOW_SCRIPT");
            builder.environment().remove("MOBILITY_SHADOW_MODEL");
            Process process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Simulation timed out");
                assertEquals(0, process.exitValue(), Files.readString(log));
            } finally {
                if (process.isAlive()) { process.destroyForcibly().waitFor(); }
            }
            List<Path> runs;
            try (Stream<Path> paths = Files.list(output)) {
                runs = paths.filter(Files::isDirectory).collect(Collectors.toList());
            }
            assertEquals(1, runs.size());
            Path run = runs.get(0);
            Properties result = new Properties();
            try (Reader reader = Files.newBufferedReader(run.resolve("run.properties"))) { result.load(reader); }
            assertEquals("233", result.getProperty("sample_rows"));
            assertEquals("fixed_233_observations", result.getProperty("sample_window_policy"));
            assertEquals("13920000", result.getProperty("nominal_generation_duration_ms"));
            assertEquals("passed", result.getProperty("accounting_check"));
            assertEquals("23100", result.getProperty("generated_bytes"));
            assertEquals(result.getProperty("generated_bytes"), result.getProperty("upload_completed_bytes"));
            assertEquals(result.getProperty("generated_bytes"), result.getProperty("assigned_bytes_final"));
            List<String> rows = Files.readAllLines(run.resolve("mobility_samples.csv"));
            assertEquals(234, rows.size());
            for (int i = 2; i < rows.size(); i++) {
                assertEquals(60_000, Long.parseLong(rows.get(i).split(",")[2])
                        - Long.parseLong(rows.get(i - 1).split(",")[2]));
            }
        }
    }
}
