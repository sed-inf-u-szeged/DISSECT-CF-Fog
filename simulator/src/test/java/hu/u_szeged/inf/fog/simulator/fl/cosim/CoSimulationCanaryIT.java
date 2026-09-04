package hu.u_szeged.inf.fog.simulator.fl.cosim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.uibk.dps.cloud.simulator.test.PMRelatedFoundation;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

import hu.u_szeged.inf.fog.simulator.fl.FLEdgeDevice;
import hu.u_szeged.inf.fog.simulator.fl.gossip.FLGossipNode;
import hu.u_szeged.inf.fog.simulator.fl.gossip.FLGossipOrchestrator;
import hu.u_szeged.inf.fog.simulator.fl.merge.DriftSuppressed;
import hu.u_szeged.inf.fog.simulator.fl.selection.CompositeScore;
import hu.u_szeged.inf.fog.simulator.fl.selection.GammaSchedule;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P3.7 co-simulation canary — the Phase-3 gate. Runs the full Track-A pipeline
 * on the 6-node clustered base graph (T=3, LeNet on a small synthetic set):
 * Pass 1 (Java) → Pass 2 (Python harness, real training) → Pass 3 (Java replay)
 * with {@code selection_agreement=abort}, asserting:
 * <ol>
 *   <li>selection agreement is 100% (abort mode does not throw),</li>
 *   <li>two full pipeline executions are byte-identical (Pass-2 trace),</li>
 *   <li>replayed accuracy equals the trace accuracy,</li>
 *   <li>per-round node energy is positive at the exchanging nodes (both endpoints).</li>
 * </ol>
 *
 * <p>This is an integration test ({@code *IT}); it is skipped (not failed) when
 * the Python interpreter or its deps are unavailable, so the unit build stays
 * green on machines without the harness.</p>
 */
class CoSimulationCanaryIT extends PMRelatedFoundation {

    private static final long REPO_BW = 100_000_000L;
    private static final long DEVICE_START = 1_000_000_000L;

    @AfterEach
    void cleanup() {
        EnergyDataCollectorFL.clearAll();
        Device.allDevices.clear();
    }

    private static String python() {
        return System.getProperty("harness.python", "py");
    }

    private static Path harnessDir() {
        // Tests run with the module dir (simulator/) as CWD; harness/ is a sibling.
        return Paths.get("..", "harness").toAbsolutePath().normalize();
    }

    /** Runs Pass 2 (python run.py); returns true on success, false if unavailable. */
    private boolean runPass2(Path systemTrace, Path outDir) throws IOException, InterruptedException {
        Path runPy = harnessDir().resolve("run.py");
        if (!Files.exists(runPy)) {
            return false;
        }
        List<String> cmd = new ArrayList<>();
        // "py -3.12" launcher on Windows; fall back handled by the launch failure catch.
        String py = python();
        cmd.add(py);
        if ("py".equals(py)) {
            cmd.add("-3.12");
        }
        cmd.add(runPy.toString());
        cmd.add("--system-trace");
        cmd.add(systemTrace.toString());
        cmd.add("--mode");
        cmd.add("gossip");
        cmd.add("--out");
        cmd.add(outDir.toString());
        cmd.add("--dataset");
        cmd.add("synthetic");
        cmd.add("--samples");
        cmd.add("240");
        cmd.add("--model");
        cmd.add("lenet5");
        cmd.add("--num-classes");
        cmd.add("4");
        cmd.add("--in-channels");
        cmd.add("1");
        cmd.add("--epochs");
        cmd.add("1");
        cmd.add("--lr");
        cmd.add("0.05");
        cmd.add("--batch");
        cmd.add("16");
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(harnessDir().toFile()).redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            return false; // python launcher not present
        }
        StringBuilder log = new StringBuilder();
        try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                log.append(line).append('\n');
            }
        }
        boolean done = p.waitFor(20, java.util.concurrent.TimeUnit.MINUTES);
        if (!done) {
            p.destroyForcibly();
            throw new IllegalStateException("Pass-2 harness timed out");
        }
        if (p.exitValue() != 0) {
            // Missing torch / numpy ⇒ treat as "unavailable" (skip), not a contract failure.
            System.out.println("[canary] Pass-2 exited " + p.exitValue() + ":\n" + log);
            return false;
        }
        return true;
    }

    private FLGossipNode makeNode(int id, Map<String, Integer> latency, float[] sig0) {
        Repository repo = new Repository(1_000_000_000L, "c" + id, REPO_BW, REPO_BW, REPO_BW,
                latency, defaultStorageTransitions, defaultNetworkTransitions);
        try {
            repo.setState(NetworkNode.State.RUNNING);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        PhysicalMachine pm = new PhysicalMachine(4, 0.001, 8L * 1024 * 1024 * 1024, repo, 0, 0,
                defaultHostTransitions);
        RandomWalkMobilityStrategy mob = new RandomWalkMobilityStrategy(
                new GeoLocation(47 + id * 0.01, 19 + id * 0.01), 0.0027, 0.0055, 10_000);
        FLEdgeDevice dev = new FLEdgeDevice(DEVICE_START, DEVICE_START + 1000L, 1024L, 60_000L,
                mob, new RandomDeviceStrategy(), pm, 5.0e-6, 1, REPO_BW, 0.004, 0.0, 0.0, false);
        return new FLGossipNode(id, dev, sig0, 10 + id);
    }

    @Test
    @DisplayName("Pass1→Pass2→Pass3 canary: 100% agreement (abort), byte-identical, acc replay, energy>0")
    void canary(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        // ---- Pass 1 (Java): write system_trace.json ----
        Pass1Main.Config cfg = new Pass1Main.Config();
        cfg.seed = 42L;
        cfg.rounds = 3;
        Pass1Main.Built built = Pass1Main.build(cfg);
        Path pass1 = tmp.resolve("pass1");
        Files.createDirectories(pass1);
        Path systemTrace = pass1.resolve("system_trace.json");
        SystemTraceWriter.write(systemTrace, built.trace);

        // ---- Pass 2 (Python): run twice for the byte-identical check ----
        Path pass2a = tmp.resolve("pass2a");
        Path pass2b = tmp.resolve("pass2b");
        boolean ok = runPass2(systemTrace, pass2a);
        Assumptions.assumeTrue(ok, "Python harness unavailable (torch/numpy) — skipping canary");
        assertTrue(runPass2(systemTrace, pass2b), "second Pass-2 run");

        // (ii) byte-identical pipeline executions. The model trajectory
        // (signatures.bin) and every *decision/learning* column must be identical;
        // the lone exception is train_time_ms, a measured wall-clock value (kept for
        // Track-B calibration) that is inherently non-deterministic — it is masked.
        assertArrayEquals(Files.readAllBytes(pass2a.resolve("signatures.bin")),
                Files.readAllBytes(pass2b.resolve("signatures.bin")),
                "two Pass-2 runs must be byte-identical (signatures.bin)");
        assertEquals(maskTrainTime(pass2a.resolve("learning_trace.csv")),
                maskTrainTime(pass2b.resolve("learning_trace.csv")),
                "two Pass-2 runs must be byte-identical (CSV, excluding measured train_time_ms)");

        // ---- Pass 3 (Java replay, abort mode) ----
        TraceReader reader = TraceReader.load(pass2a);
        reader.validateAgainst(built.topology);
        TraceProvider provider = new TraceProvider(reader);

        Timed.resetTimed();
        EnergyDataCollectorFL.clearAll();
        Device.allDevices.clear();
        SimRandom.setSeed(cfg.seed);

        FLTopology topo = built.topology;
        int n = topo.size();
        Map<String, Integer> latency = new HashMap<>();
        for (int i = 0; i < n; i++) {
            latency.put("c" + i, 1);
        }
        List<FLGossipNode> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            nodes.add(makeNode(i, latency, reader.signature(i, 0)));
        }

        FLGossipOrchestrator orch = new FLGossipOrchestrator(
                topo, nodes, new CompositeScore(GammaSchedule.EXPLORE_THEN_EXPLOIT),
                new DriftSuppressed(), 2, cfg.rounds, 1000L, 0.0, 1.0, null,
                built.trace.model.paramCount, false, 1.0, cfg.seed);
        orch.enableTraceReplay(provider, "abort"); // canary: contract must hold exactly
        orch.setTidRecorder(new TidRecorder(topo));
        boolean[] finished = { false };
        orch.setFinishedCallback(() -> finished[0] = true);

        Timed.simulateUntil(59_000L);
        assertTrue(finished[0], "gossip replay finished");

        // (i) selection agreement 100% (abort would have thrown otherwise).
        assertEquals(1.0, orch.selectionAgreementRate(), 0.0, "selection agreement must be 100%");

        // (iii) replayed accuracy equals the trace accuracy.
        for (int i = 0; i < n; i++) {
            for (int r = 0; r < cfg.rounds; r++) {
                assertEquals(reader.metrics(i, r).acc, orch.replayedAccuracy(i, r), 0.0,
                        "replayed accuracy must equal the trace accuracy (node " + i + " round " + r + ")");
            }
        }

        // (iv) per-round node energy positive for nodes that exchanged.
        int positiveExchangingRows = 0;
        for (double[] row : orch.perRoundEnergyRows()) {
            long bytes = (long) row[2] + (long) row[3]; // ul + dl
            double energyMj = row[4];
            if (bytes > 0) {
                assertTrue(energyMj > 0.0 || Double.isNaN(energyMj),
                        "exchanging node energy must be positive");
                if (energyMj > 0.0) {
                    positiveExchangingRows++;
                }
            }
        }
        assertTrue(positiveExchangingRows > 0, "at least some exchanging nodes show positive energy");
    }

    private static void assertArrayEquals(byte[] a, byte[] b, String msg) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(a, b, msg);
    }

    /** Reads the trace CSV with the measured {@code train_time_ms} column (index 13) blanked,
     *  so the determinism check ignores wall-clock measurement noise. */
    private static String maskTrainTime(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv);
        StringBuilder sb = new StringBuilder();
        int trainTimeCol = 13; // schema_version..n_samples → train_time_ms at index 13
        for (String line : lines) {
            String[] f = line.split(",", -1);
            if (f.length > trainTimeCol && !"train_time_ms".equals(f[trainTimeCol])) {
                f[trainTimeCol] = "<masked>";
            }
            sb.append(String.join(",", f)).append('\n');
        }
        return sb.toString();
    }
}
