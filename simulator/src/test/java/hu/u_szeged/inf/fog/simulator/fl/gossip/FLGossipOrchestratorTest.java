package hu.u_szeged.inf.fog.simulator.fl.gossip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.uibk.dps.cloud.simulator.test.PMRelatedFoundation;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

import hu.u_szeged.inf.fog.simulator.fl.FLEdgeDevice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import hu.u_szeged.inf.fog.simulator.fl.cosim.TidRecorder;
import hu.u_szeged.inf.fog.simulator.fl.merge.DriftSuppressed;
import hu.u_szeged.inf.fog.simulator.fl.selection.CompositeScore;
import hu.u_szeged.inf.fog.simulator.fl.selection.GammaSchedule;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import hu.u_szeged.inf.fog.simulator.fl.topology.TopologyFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2.3 + DoD item 6: a synthetic-mode gossip run completes deterministically —
 * two runs with the same seed produce byte-identical telemetry. Also checks the
 * round-level structure (rows, ring degeneracy in the orchestrator path).
 */
class FLGossipOrchestratorTest extends PMRelatedFoundation {

    // Realistic (fast) repo bandwidth so the native energy-metering transfer completes
    // within the analytic round — the round barrier is then quiescent for forceSample.
    private static final long REPO_BW = 100_000_000L;
    private static final long DEVICE_START = 1_000_000_000L;
    private static final int SIG_DIM = 16;
    private static final int N = 6;
    private static final int ROUNDS = 10;
    private static final long SEED = 2026L;

    @AfterEach
    void cleanup() {
        EnergyDataCollectorFL.clearAll();
        Device.allDevices.clear();
    }

    private FLGossipNode makeNode(int id, Map<String, Integer> latency) {
        Repository repo = new Repository(1_000_000_000L, "n" + id, REPO_BW, REPO_BW, REPO_BW,
                latency, defaultStorageTransitions, defaultNetworkTransitions);
        try {
            repo.setState(NetworkNode.State.RUNNING);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        PhysicalMachine pm = new PhysicalMachine(4, 0.001, 8L * 1024 * 1024 * 1024, repo, 0, 0,
                defaultHostTransitions);
        RandomWalkMobilityStrategy mobility = new RandomWalkMobilityStrategy(
                new GeoLocation(47.0 + id * 0.01, 19.0 + id * 0.01), 0.0027, 0.0055, 10_000);
        FLEdgeDevice device = new FLEdgeDevice(
                DEVICE_START, DEVICE_START + 1000L, 1024L, 60_000L,
                mobility, new RandomDeviceStrategy(), pm,
                5.0e-6, 1, REPO_BW, 0.004, 0.0, 0.0, false);
        float[] sig = new float[SIG_DIM];
        for (int i = 0; i < SIG_DIM; i++) {
            sig[i] = 0.01f * (id + 1) * (i + 1);
        }
        return new FLGossipNode(id, device, sig, 10 + id);
    }

    private String runOnce() {
        Timed.resetTimed();
        EnergyDataCollectorFL.clearAll();
        Device.allDevices.clear();
        SimRandom.setSeed(SEED);

        Map<String, Integer> latency = new HashMap<>();
        for (int i = 0; i < N; i++) {
            latency.put("n" + i, 1);
        }
        List<FLGossipNode> nodes = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            nodes.add(makeNode(i, latency));
        }
        FLTopology ring = TopologyFactory.ring(N);

        FLGossipOrchestrator orch = new FLGossipOrchestrator(
                ring, nodes,
                new CompositeScore(GammaSchedule.EXPLORE_THEN_EXPLOIT),
                new DriftSuppressed(),
                /* k */ 2, /* maxRounds */ ROUNDS, /* roundInterval */ 1000L,
                /* exchangeFailureProb */ 0.0, /* compression */ 1.0,
                /* loadProfile */ null, /* paramCount */ 1000L,
                /* dynamic */ false, /* pLink */ 1.0, SEED);
        boolean[] finished = { false };
        orch.setFinishedCallback(() -> finished[0] = true);

        // The per-node EnergyDataCollectorFL meters are perpetual Timed subscribers
        // (they re-fire every 60 000 ticks by design), so simulateUntilLastEvent would
        // never terminate. Bound the run below the first meter tick (60 000) and below
        // the device start (1e9); ~10 short rounds finish well within this window.
        Timed.simulateUntil(59_000L);
        if (!finished[0]) {
            throw new IllegalStateException("gossip run did not finish within the bounded window");
        }
        return orch.telemetryCsv();
    }

    @Test
    @DisplayName("synthetic gossip: 10 rounds complete and produce the expected telemetry shape")
    void runsAndProducesTelemetry() {
        String csv = runOnce();
        String[] lines = csv.split("\n");
        // header + ROUNDS*N data rows
        assertEquals(1 + ROUNDS * N, lines.length, "header + one row per (round,node)");
        assertEquals("round,node,peers,ul_bytes,dl_bytes,busy_ticks,train_ticks,round_duration_ticks,idle_ticks",
                lines[0]);
        // Ring degeneracy in the orchestrator path: node 0 (neighbours 1,5), k=2 ⇒ peers "1;5".
        // Row for round 0, node 0 is lines[1] (rows are appended round-major, node-minor).
        String[] r0n0 = lines[1].split(",", -1);
        assertEquals("0", r0n0[0]);
        assertEquals("0", r0n0[1]);
        assertEquals("1;5", r0n0[2], "6-node ring, k=2 ⇒ both neighbours selected");
    }

    @Test
    @DisplayName("determinism: two same-seed runs produce byte-identical telemetry")
    void deterministicAcrossRuns() {
        String a = runOnce();
        String b = runOnce();
        assertEquals(a, b, "same seed ⇒ byte-identical gossip telemetry");
        assertTrue(a.length() > 0);
    }

    /** Same scenario as {@link #runOnce()} but constructed via the Builder,
     *  relying on its defaults for everything the canonical run passes explicitly
     *  (policy, merge, k, interval, failure, compression, static topology). */
    private String runOnceViaBuilder() {
        Timed.resetTimed();
        EnergyDataCollectorFL.clearAll();
        Device.allDevices.clear();
        SimRandom.setSeed(SEED);

        Map<String, Integer> latency = new HashMap<>();
        for (int i = 0; i < N; i++) {
            latency.put("n" + i, 1);
        }
        List<FLGossipNode> nodes = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            nodes.add(makeNode(i, latency));
        }
        FLGossipOrchestrator orch = new FLGossipOrchestrator.Builder(TopologyFactory.ring(N), nodes)
                .rounds(ROUNDS)
                .paramCount(1000L)
                .seed(SEED)
                .build();
        boolean[] finished = { false };
        orch.setFinishedCallback(() -> finished[0] = true);
        Timed.simulateUntil(59_000L);
        if (!finished[0]) {
            throw new IllegalStateException("builder gossip run did not finish within the bounded window");
        }
        return orch.telemetryCsv();
    }

    @Test
    @DisplayName("F5: Builder defaults reproduce the canonical constructor byte-for-byte")
    void builderEquivalentToConstructor() {
        String viaConstructor = runOnce();
        String viaBuilder = runOnceViaBuilder();
        assertEquals(viaConstructor, viaBuilder,
                "Builder with defaults must be telemetry-identical to the full constructor");
    }

    @Test
    @DisplayName("P5.1: exportAll writes the full Pass-3 artefact set")
    void exportsPass3Artefacts(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Timed.resetTimed();
        EnergyDataCollectorFL.clearAll();
        Device.allDevices.clear();
        SimRandom.setSeed(SEED);

        Map<String, Integer> latency = new HashMap<>();
        for (int i = 0; i < N; i++) {
            latency.put("n" + i, 1);
        }
        List<FLGossipNode> nodes = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            nodes.add(makeNode(i, latency));
        }
        FLTopology ring = TopologyFactory.ring(N);
        FLGossipOrchestrator orch = new FLGossipOrchestrator(
                ring, nodes, new CompositeScore(GammaSchedule.EXPLORE_THEN_EXPLOIT),
                new DriftSuppressed(), 2, ROUNDS, 1000L, 0.0, 1.0, null, 1000L, false, 1.0, SEED);
        orch.setScenarioId("S_export_test");
        orch.setTidRecorder(new TidRecorder(ring));
        boolean[] finished = { false };
        orch.setFinishedCallback(() -> finished[0] = true);
        Timed.simulateUntil(59_000L);
        assertTrue(finished[0]);

        orch.exportAll(dir);
        for (String f : new String[] { "per_exchange.csv", "idle_time.csv", "gossip_round.csv",
                "run_metadata.json", "gossip_telemetry.csv", "tid_timeseries.csv" }) {
            assertTrue(Files.exists(dir.resolve(f)), "missing artefact: " + f);
        }

        // idle_time.csv: header + ROUNDS*N rows.
        assertEquals(1 + ROUNDS * N, Files.readAllLines(dir.resolve("idle_time.csv")).size());
        // gossip_round.csv: header + ROUNDS rows.
        assertEquals(1 + ROUNDS, Files.readAllLines(dir.resolve("gossip_round.csv")).size());
        // per_exchange.csv has the documented header.
        assertEquals("round,src,dst,bytes,delay_ticks,src_mj,dst_mj,failed",
                Files.readAllLines(dir.resolve("per_exchange.csv")).get(0));

        // run_metadata.json parses and carries the key fields.
        JsonNode meta = new ObjectMapper().readTree(dir.resolve("run_metadata.json").toFile());
        assertEquals("S_export_test", meta.get("scenario_id").asText());
        assertEquals(ring.topologyHash(), meta.get("topology_hash").asText());
        assertEquals(1.0, meta.get("selection_agreement_rate").asDouble(), 0.0); // synthetic ⇒ nothing compared ⇒ 1.0
        assertTrue(meta.has("lambda2") && meta.has("code_git_hash"));
    }
}
