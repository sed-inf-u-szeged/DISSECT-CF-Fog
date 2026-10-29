package hu.u_szeged.inf.fog.simulator.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.prediction.FeatureManager;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite.SqLiteManager;
import hu.u_szeged.inf.fog.simulator.prediction.settings.ExportSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import hu.u_szeged.inf.fog.simulator.util.ProjectPathResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Collects one development run with Pliant prediction feedback disabled. */
public final class LstmDataCollectionSimulation {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String JAVA_ROOT =
            "src/main/java/hu/u_szeged/inf/fog/simulator/";

    private LstmDataCollectionSimulation() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: LstmDataCollectionSimulation <simulation-seed>");
        }
        long seed = Long.parseLong(args[0]);
        SeedSyncer.modifySeed(seed);

        Path simulator = ProjectPathResolver.getSimulatorDirectory();
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        Path output = simulator.resolve("sim_res").resolve("lstm-data-" + stamp
                + "-seed" + seed + "-" + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectories(output.getParent());
        Files.createDirectory(output);
        ScenarioBase.resultDirectory = output.toString();

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schema_version", 1);
        manifest.put("status", "running");
        manifest.put("started_utc", Instant.now().toString());
        manifest.put("simulation_seed", seed);
        manifest.put("data_use", "development_pilot");
        manifest.put("repeatability", "not_yet_verified");
        manifest.put("prediction_enabled", false);
        manifest.put("training_enabled", false);
        manifest.put("prediction_feedback", "disabled");
        manifest.put("collection_policy", "Pliant without forecast input");
        manifest.put("java_version", System.getProperty("java.version"));
        manifest.put("java_vendor", System.getProperty("java.vendor"));
        manifest.put("os_name", System.getProperty("os.name"));
        manifest.put("os_arch", System.getProperty("os.arch"));
        manifest.put("result_directory", output.toString());
        manifest.put("configuration_note", "Fixed audited PredictionSimulationDefinition; "
                + "source snapshots identify configuration, not a complete environment archive.");
        manifest.put("device_multiplier", 30);
        manifest.put("device_schedule_columns", new String[]{"start_min", "end_min", "devices"});
        manifest.put("device_schedule", new int[][]{
                {0, 60, 60}, {60, 120, 180}, {120, 180, 90},
                {180, 240, 210}, {240, 600, 60}, {360, 600, 150}});
        manifest.put("application_tick_ms", 60000);
        manifest.put("device_frequency_ms", 60000);
        manifest.put("task_size_bytes", 5120);
        manifest.put("task_instructions", 480);
        manifest.put("activation_ratio", 0.9);
        manifest.put("transfer_divider", 2.0);
        manifest.put("target", "node sum(receivedData - processedData), sampled before allocation");
        Path manifestPath = output.resolve("run_manifest.json");
        writeManifest(manifestPath, manifest);
        System.out.println("COLLECTION_DIR=" + output);

        try {
            manifest.put("source_files_sha256", snapshotSources(simulator, output));
            writeManifest(manifestPath, manifest);

            PredictionConfigurator configurator = new PredictionConfigurator(
                    new PredictionSimulationDefinition());
            PredictionConfigurator.PREDICTION_ENABLED = false;
            PredictionConfigurator.CREATE_DATABASE = false;
            SqLiteManager.setEnabled(false);
            configurator.addSimulationSettings(new SimulationSettings(
                    new ExportSettings(true, output.toString(), true, true, false, false),
                    new ArrayList<>(), null, null));
            configurator.execute();

            int predictions = FeatureManager.getInstance().getTotalNumOfPredictions();
            if (predictions != 0 || PredictionConfigurator.PREDICTION_ENABLED
                    || !PredictionConfigurator.predictor.isEmpty()) {
                throw new IllegalStateException("Unexpected prediction activity during collection");
            }
            Path diagnostics = output.resolve("lstm-input-diagnostics.csv");
            if (!Files.isRegularFile(diagnostics) || Files.size(diagnostics) == 0) {
                throw new IllegalStateException("Missing input diagnostics");
            }
            long rows;
            try (Stream<String> lines = Files.lines(diagnostics, StandardCharsets.UTF_8)) {
                rows = lines.count() - 1;
            }
            if (rows < 1) {
                throw new IllegalStateException("Input diagnostics contain no observations");
            }
            manifest.put("diagnostic_rows", rows);
            manifest.put("diagnostics_sha256", sha256(Files.readAllBytes(diagnostics)));
            manifest.put("final_sim_time_ms", Timed.getFireCount());
            manifest.put("prediction_count", predictions);
            manifest.put("finished_utc", Instant.now().toString());
            manifest.put("status", "completed");
            writeManifest(manifestPath, manifest);
            System.out.println("COLLECTION_COMPLETE");
        } catch (Exception failure) {
            manifest.put("status", "failed");
            manifest.put("finished_utc", Instant.now().toString());
            manifest.put("failure", failure.toString());
            try {
                writeManifest(manifestPath, manifest);
            } catch (Exception writeFailure) {
                failure.addSuppressed(writeFailure);
            }
            throw failure;
        }
    }

    private static Map<String, String> snapshotSources(Path simulator, Path output)
            throws Exception {
        String[] names = {
            "pom.xml",
            JAVA_ROOT + "demo/PredictionSimulationDefinition.java",
            JAVA_ROOT + "demo/LstmDataCollectionSimulation.java",
            JAVA_ROOT + "prediction/Feature.java",
            JAVA_ROOT + "application/strategy/PliantApplicationStrategy.java",
            "src/main/resources/demo/LPDS_original.xml",
            "src/main/resources/demo/XML_examples/LPDS_32.xml",
            "src/main/resources/demo/XML_examples/LPDS_16.xml"
        };
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String name : names) {
            Path source = simulator.resolve(name);
            if (!Files.isRegularFile(source)) {
                throw new IllegalStateException("Missing required source snapshot: " + source);
            }
            byte[] bytes = Files.readAllBytes(source);
            if (name.equals(JAVA_ROOT + "demo/PredictionSimulationDefinition.java")) {
                String normalized = new String(bytes, StandardCharsets.UTF_8)
                        .replace("\r\n", "\n");
                if (normalized.startsWith("\uFEFF")) {
                    normalized = normalized.substring(1);
                }
                if (!sha256(normalized.getBytes(StandardCharsets.UTF_8)).equals(
                        "8c6213c0c17bfe1c5e5baa8e0395a1dc6bcc1993c05a59a2e46844a99bc1675e")) {
                    throw new IllegalStateException(
                            "Scenario definition changed; update the collector configuration before running");
                }
            }
            Path copy = output.resolve("source_snapshot").resolve(name);
            Files.createDirectories(copy.getParent());
            Files.write(copy, bytes);
            hashes.put(name, sha256(bytes));
        }
        return hashes;
    }

    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format("%02x", value & 255));
        }
        return result.toString();
    }

    private static void writeManifest(Path path, Map<String, Object> manifest)
            throws Exception {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), manifest);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}