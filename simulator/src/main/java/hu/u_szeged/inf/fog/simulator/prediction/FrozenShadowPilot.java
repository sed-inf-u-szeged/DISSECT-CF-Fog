package hu.u_szeged.inf.fog.simulator.prediction;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.LstmDataCollectionSimulation;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

/** Opt-in diagnostic process; never registers a formal prediction. */
public final class FrozenShadowPilot {
    private static boolean armed;
    private static String[] config;
    private static Process worker;
    private static BufferedWriter writer;
    private static BufferedReader reader;
    private static Path output;
    private static RuntimeException failure;
    private static final ExecutorService IO =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "frozen-shadow-reader");
                t.setDaemon(true);
                return t;
            });

    private FrozenShadowPilot() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Arguments: seed python scripts-directory export-directory");
        }
        Long.parseLong(args[0]);
        config = args.clone();
        armed = true;

        try {
            LstmDataCollectionSimulation.main(new String[]{args[0]});
            if (failure != null) {
                throw failure;
            }
            if (worker == null) {
                throw new IllegalStateException("No shadow observations");
            }

            exchange("END", "DONE");
            writer.close();

            if (!worker.waitFor(30, TimeUnit.SECONDS)
                    || worker.exitValue() != 0) {
                throw new IllegalStateException(
                        "Shadow worker did not exit successfully");
            }
            System.out.println("FROZEN_SHADOW_COMPLETE=" + output);
        } finally {
            armed = false;
            if (worker != null && worker.isAlive()) {
                worker.destroyForcibly();
            }
            IO.shutdownNow();
        }
    }

    public static void observe(Feature feature) {
        if (!armed) {
            return;
        }
        if (failure != null) {
            throw failure;
        }

        String name = feature.getName();
        if (!name.endsWith("::notYetProcessedData")
                || name.contains("_cloud::")) {
            return;
        }

        try {
            if (name.contains("\t") || name.contains("\n")
                    || name.contains("\r")) {
                throw new IllegalArgumentException("Invalid feature name");
            }
            if (worker == null) {
                start();
            }

            int index = feature.getValues().size() - 1;
            exchange(name + "\t" + index + "\t" + Timed.getFireCount()
                    + "\t" + feature.getValues().get(index), "OK");
        } catch (Exception e) {
            failure = new IllegalStateException(
                    "Shadow failed; inspect " + output, e);
            if (worker != null) {
                worker.destroyForcibly();
            }
            throw failure;
        }
    }

    private static void start() throws Exception {
        output = Paths.get(ScenarioBase.resultDirectory, "frozen-shadow");
        Files.createDirectory(output);

        ProcessBuilder pb = new ProcessBuilder(
                config[1], "-u",
                Paths.get(config[2], "frozen_shadow_worker.py").toString(),
                config[3], output.toString(), config[0]);
        pb.directory(Paths.get(config[2]).toFile());
        pb.redirectError(output.resolve("worker-stderr.log").toFile());
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("TF_CPP_MIN_LOG_LEVEL", "2");

        worker = pb.start();
        writer = new BufferedWriter(new OutputStreamWriter(
                worker.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(
                worker.getInputStream(), StandardCharsets.UTF_8));
        expect("READY");
    }

    private static void exchange(String message, String expected)
            throws Exception {
        writer.write(message);
        writer.newLine();
        writer.flush();
        expect(expected);
    }

    private static void expect(String expected) throws Exception {
        Future<String> response = IO.submit(() -> reader.readLine());
        String actual = response.get(300, TimeUnit.SECONDS);
        if (!expected.equals(actual)) {
            throw new IOException(
                    "Expected " + expected + ", received " + actual);
        }
    }
}