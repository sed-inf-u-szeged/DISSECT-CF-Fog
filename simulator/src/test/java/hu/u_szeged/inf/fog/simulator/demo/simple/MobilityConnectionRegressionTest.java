package hu.u_szeged.inf.fog.simulator.demo.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** Exercises actual broker restart and data draining in independent simulator JVMs. */
class MobilityConnectionRegressionTest {
    @TempDir
    Path temporary;

    @Test
    void originalCoveredRouteStillCompletesWithoutRestart() throws Exception {
        Properties result = run("rect_01_s20");
        assertEquals("0", result.getProperty("broker_wake_requests"));
    }

    @Test
    void returningDeviceWakesStoppedApplications() throws Exception {
        Properties result = run("rect_02_s20");
        assertTrue(Long.parseLong(result.getProperty("broker_wake_requests")) > 0);
    }

    @Test
    void reconnectOnReadyAvoidsImmediateIdleShutdown() throws Exception {
        Properties result = run("rect_03_s20");
        assertTrue(Long.parseLong(result.getProperty("broker_wake_requests")) > 0);
    }

    @Test
    void drainMovementDoesNotExtendTheFixedSampleWindow() throws Exception {
        Properties result = run("rect_02_s41");
        assertTrue(Long.parseLong(result.getProperty("drain_observation_rows")) > 0);
    }

    private Properties run(String name) throws Exception {
        Path resources = Path.of("src/main/resources/demo").toAbsolutePath();
        if (!Files.isDirectory(resources)) {
            resources = Path.of("simulator/src/main/resources/demo").toAbsolutePath();
        }
        Path config = resources.resolve("mobility/dataset-v1/train/" + name + ".properties");
        assertTrue(Files.isRegularFile(config), "Install mobility-dataset-plan-v1 before this test");
        Path log = temporary.resolve("simulation.log");
        Path output = temporary.resolve("output");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(java, "-cp", classpath, MobilityPredictionSimulation.class.getName(),
                "--no-ui", "--scenario=" + config, resources.resolve("LPDS_original.xml").toString(), output.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
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
        assertEquals("passed", result.getProperty("accounting_check"));
        assertEquals("11900", result.getProperty("generated_bytes"));
        assertEquals(result.getProperty("generated_bytes"), result.getProperty("upload_completed_bytes"));
        assertEquals(result.getProperty("generated_bytes"), result.getProperty("assigned_bytes_final"));
        assertEquals("121", result.getProperty("sample_rows"));
        assertEquals("fixed_121_observations", result.getProperty("sample_window_policy"));
        List<String> samples = Files.readAllLines(run.resolve("mobility_samples.csv"));
        List<String> observations = Files.readAllLines(run.resolve("observations.csv"));
        assertEquals(122, samples.size());
        assertEquals(observations.size() - 1, Integer.parseInt(result.getProperty("observation_rows")));
        assertEquals(observations.size() - samples.size(), Integer.parseInt(result.getProperty("drain_observation_rows")));
        long first = Long.parseLong(samples.get(1).split(",")[2]);
        long last = Long.parseLong(samples.get(121).split(",")[2]);
        assertEquals(7_200_000, last - first);
        assertEquals(first, Long.parseLong(result.getProperty("sample_window_start_ms")));
        assertEquals(last, Long.parseLong(result.getProperty("sample_window_end_ms")));
        return result;
    }
}
