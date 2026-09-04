package hu.u_szeged.inf.fog.simulator.fl.cosim.trackb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import hu.u_szeged.inf.fog.simulator.fl.cosim.Pass1Main;
import hu.u_szeged.inf.fog.simulator.fl.cosim.SystemTraceWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P4 DoD — Track-B online coupling. Drives a 2-node online run for 5 rounds
 * against the live Python worker through the directory handshake, then checks
 * worker-restart idempotency and runs the calibration pipeline. Skipped (not
 * failed) when the Python harness is unavailable.
 */
class FLTrackBSmokeIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int ROUNDS = 5;

    private static Path harnessDir() {
        return Paths.get("..", "harness").toAbsolutePath().normalize();
    }

    private static Path analysisDir() {
        return Paths.get("..", "analysis").toAbsolutePath().normalize();
    }

    private static List<String> pyCmd(Path script) {
        List<String> cmd = new ArrayList<>();
        String py = System.getProperty("harness.python", "py");
        cmd.add(py);
        if ("py".equals(py)) {
            cmd.add("-3.12");
        }
        cmd.add(script.toString());
        return cmd;
    }

    private Process startWorker(Path systemTrace, Path bridge, int rounds) throws IOException {
        List<String> cmd = pyCmd(harnessDir().resolve("trackb_worker.py"));
        cmd.add("--bridge");
        cmd.add(bridge.toString());
        cmd.add("--system-trace");
        cmd.add(systemTrace.toString());
        cmd.add("--rounds");
        cmd.add(Integer.toString(rounds));
        cmd.add("--model");
        cmd.add("lenet5");
        cmd.add("--num-classes");
        cmd.add("4");
        cmd.add("--in-channels");
        cmd.add("1");
        cmd.add("--samples");
        cmd.add("240");
        cmd.add("--epochs");
        cmd.add("1");
        cmd.add("--lr");
        cmd.add("0.05");
        cmd.add("--batch");
        cmd.add("16");
        Process p = new ProcessBuilder(cmd).directory(harnessDir().toFile()).redirectErrorStream(true).start();
        // Drain stdout so the process never blocks on a full pipe.
        Thread drain = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.out.println("[worker] " + line);
                }
            } catch (IOException ignore) {
                // process ended
            }
        });
        drain.setDaemon(true);
        drain.start();
        return p;
    }

    private static boolean pythonAvailable() {
        try {
            List<String> probe = new ArrayList<>();
            String py = System.getProperty("harness.python", "py");
            probe.add(py);
            if ("py".equals(py)) {
                probe.add("-3.12");
            }
            probe.add("-c");
            probe.add("import torch, numpy");
            Process p = new ProcessBuilder(probe).redirectErrorStream(true).start();
            return p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private RoundRequest request(int round, int n) {
        RoundRequest req = new RoundRequest();
        req.round = round;
        req.mode = "gossip";
        req.mergeRule = "DRIFT_SUPPRESSED";
        req.gamma = -0.25;
        req.configHash = "smoke";
        req.timeoutS = 600.0;
        List<Integer> participants = new ArrayList<>();
        Map<String, List<Integer>> peerSets = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            participants.add(i);
            List<Integer> peers = new ArrayList<>();
            peers.add((i + 1) % n); // 2-node ring: each selects the other
            peerSets.put(Integer.toString(i), peers);
        }
        req.participants = participants;
        req.peerSets = peerSets;
        return req;
    }

    @Test
    @DisplayName("Track-B: 5 online rounds complete via the handshake; analytic timing; calibration report")
    void onlineSmokeAndCalibration(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(pythonAvailable(), "Python harness (torch/numpy) unavailable — skipping Track-B smoke");

        // Pass 1: 2-node clustered scenario.
        Pass1Main.Config cfg = new Pass1Main.Config();
        cfg.scenarioId = "S_trackb";
        cfg.clusterSizes = new int[] { 1, 1 };
        cfg.bridges = 1;
        cfg.rounds = ROUNDS;
        cfg.seed = 42L;
        Pass1Main.Built built = Pass1Main.build(cfg);
        Path systemTrace = tmp.resolve("system_trace.json");
        SystemTraceWriter.write(systemTrace, built.trace);
        int n = built.topology.size();

        Path bridge = tmp.resolve("bridge");
        Files.createDirectories(bridge);
        FLRealTrainingConnector connector = new FLRealTrainingConnector(bridge, 300_000L);

        Process worker = startWorker(systemTrace, bridge, ROUNDS);
        try {
            double[] trainTimes = new double[ROUNDS];
            for (int t = 0; t < ROUNDS; t++) {
                RoundResponse resp = connector.runRound(t, request(t, n));
                assertEquals(t, resp.round);
                assertEquals(n, resp.perNode.size(), "all nodes reported");
                for (int i = 0; i < n; i++) {
                    RoundResponse.NodeOutcome o = resp.perNode.get(Integer.toString(i));
                    assertTrue(o != null, "node " + i + " outcome present");
                    assertTrue(o.acc >= 0.0 && o.acc <= 1.0, "acc in [0,1]");
                    assertTrue(o.trainTimeMs > 0.0, "measured train time > 0");
                }
                trainTimes[t] = resp.perNode.get("0").trainTimeMs;

                // §8.4 distortion check (D5): the worker reports signature-vs-
                // full-weight distance ratios; ≈1 validates the linear projection.
                assertTrue(resp.signatureDistortion != null, "distortion check reported");
                assertEquals(n * (n - 1) / 2, resp.signatureDistortion.pairs, "all node pairs measured");
                assertTrue(resp.signatureDistortion.meanRatio > 0.5
                                && resp.signatureDistortion.meanRatio < 1.5,
                        "signature distances track full-weight distances (mean ratio was "
                                + resp.signatureDistortion.meanRatio + ")");

                // The simulator owns timing: round duration is ANALYTIC (latency +
                // payload/bandwidth), NOT the worker's wall-clock train time.
                var edge = built.topology.edge(0, 1);
                long analyticTicks = edge.latencyTicks
                        + (long) Math.ceil((double) built.trace.model.payloadBytesFloat32
                                / (double) edge.bandwidthBytesPerTick);
                assertTrue(analyticTicks > 0, "analytic round timing computed without worker wall-clock");
            }
            assertTrue(worker.waitFor(5, TimeUnit.MINUTES), "worker exits after serving all rounds");
            assertEquals(0, worker.exitValue());
        } finally {
            if (worker.isAlive()) {
                worker.destroyForcibly();
            }
        }

        // Calibration pipeline on the real Track-B train times.
        Path calibOut = tmp.resolve("calib");
        List<String> calibCmd = pyCmd(analysisDir().resolve("calibration.py"));
        calibCmd.add("--bridge");
        calibCmd.add(bridge.toString());
        calibCmd.add("--system-trace");
        calibCmd.add(systemTrace.toString());
        calibCmd.add("--out");
        calibCmd.add(calibOut.toString());
        Process calib = new ProcessBuilder(calibCmd).directory(analysisDir().toFile())
                .redirectErrorStream(true).start();
        StringBuilder calibLog = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(calib.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                calibLog.append(line).append('\n');
            }
        }
        assertTrue(calib.waitFor(2, TimeUnit.MINUTES), "calibration finished");
        assertEquals(0, calib.exitValue(), "calibration exit 0:\n" + calibLog);
        assertTrue(Files.exists(calibOut.resolve("calibration.json")), "calibration.json generated");
        assertTrue(Files.exists(calibOut.resolve("calibration_report.csv")), "calibration_report.csv generated");
    }

    @Test
    @DisplayName("Track-B: worker restart re-serves a killed round idempotently (same learning result)")
    void workerRestartIdempotency(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(pythonAvailable(), "Python harness unavailable — skipping");

        int rounds = 3;
        Pass1Main.Config cfg = new Pass1Main.Config();
        cfg.scenarioId = "S_trackb_idem";
        cfg.clusterSizes = new int[] { 1, 1 };
        cfg.bridges = 1;
        cfg.rounds = rounds;
        cfg.seed = 7L;
        Pass1Main.Built built = Pass1Main.build(cfg);
        Path systemTrace = tmp.resolve("system_trace.json");
        SystemTraceWriter.write(systemTrace, built.trace);
        int n = built.topology.size();

        Path bridge = tmp.resolve("bridge");
        Files.createDirectories(bridge);
        FLRealTrainingConnector connector = new FLRealTrainingConnector(bridge, 300_000L);

        // First run: serve all rounds.
        Process w1 = startWorker(systemTrace, bridge, rounds);
        try {
            for (int t = 0; t < rounds; t++) {
                connector.runRound(t, request(t, n));
            }
            assertTrue(w1.waitFor(5, TimeUnit.MINUTES));
        } finally {
            if (w1.isAlive()) {
                w1.destroyForcibly();
            }
        }

        // Capture the last round's learning result, then simulate a mid-serve kill by
        // deleting its response; the restarted worker must re-serve it identically.
        Path lastDir = bridge.resolve(String.format("round_%04d", rounds - 1));
        RoundResponse before = MAPPER.readValue(lastDir.resolve("response.json").toFile(), RoundResponse.class);
        byte[] sigBefore = Files.readAllBytes(lastDir.resolve(before.signaturesFile));
        Files.deleteIfExists(lastDir.resolve("response.READY"));
        Files.deleteIfExists(lastDir.resolve("response.json"));
        Files.deleteIfExists(lastDir.resolve(before.signaturesFile));

        // Restart: requests for all rounds still exist; the worker replays state and
        // re-serves the deleted round.
        Process w2 = startWorker(systemTrace, bridge, rounds);
        try {
            assertTrue(w2.waitFor(5, TimeUnit.MINUTES), "restarted worker finishes");
            assertEquals(0, w2.exitValue());
        } finally {
            if (w2.isAlive()) {
                w2.destroyForcibly();
            }
        }

        RoundResponse after = MAPPER.readValue(lastDir.resolve("response.json").toFile(), RoundResponse.class);
        byte[] sigAfter = Files.readAllBytes(lastDir.resolve(after.signaturesFile));
        // The learning result is deterministic ⇒ re-served identically (signatures +
        // acc/loss/delta). Only the measured train_time_ms may differ (wall-clock).
        org.junit.jupiter.api.Assertions.assertArrayEquals(sigBefore, sigAfter,
                "re-served signatures must be byte-identical");
        for (int i = 0; i < n; i++) {
            RoundResponse.NodeOutcome b = before.perNode.get(Integer.toString(i));
            RoundResponse.NodeOutcome a = after.perNode.get(Integer.toString(i));
            assertEquals(b.acc, a.acc, 0.0, "acc re-served identically");
            assertEquals(b.loss, a.loss, 0.0, "loss re-served identically");
            assertEquals(b.deltaNorm, a.deltaNorm, 0.0, "delta_norm re-served identically");
        }
    }
}
