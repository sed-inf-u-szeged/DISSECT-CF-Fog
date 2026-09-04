package hu.u_szeged.inf.fog.simulator.fl.demos;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.fl.FLAggregator;
import hu.u_szeged.inf.fog.simulator.fl.FLEdgeDevice;
import hu.u_szeged.inf.fog.simulator.fl.FLHierarchicalSupervisor;
import hu.u_szeged.inf.fog.simulator.fl.FLOrchestrator;
import hu.u_szeged.inf.fog.simulator.fl.FLTelemetry;
import hu.u_szeged.inf.fog.simulator.fl.GlobalModelBroadcastEvent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <h2>Hierarchical Federated Learning demo</h2>
 *
 * <p>Two-tier FL over the HTC infrastructure, with two regional aggregators
 * (one per cluster) and a higher-tier coordinator that periodically synthesises
 * a global model from their regional models and pushes it back. Layout:</p>
 *
 * <pre>
 *               +-------------------------------+
 *               |    HierarchicalSupervisor     |   (every K edge rounds)
 *               |    - read getGlobalModel()    |
 *               |    - average both             |
 *               |    - replaceGlobalModel() x2  |
 *               +---------+----------+----------+
 *                         |          |
 *                  reads / writes back
 *                         |          |
 *               +---------+-+      +-+---------+
 *               | Edge-Agg-A|      | Edge-Agg-B|
 *               | AI Cluster|      |Extreme Edg|
 *               | (5 devs)  |      | (5 devs)  |
 *               +-----------+      +-----------+
 * </pre>
 *
 * <p><b>Cluster A (AI Cluster, 5 cloud-class devices):</b>
 * 2 CPU-only nodes, 2 P2000-GPU nodes, 1 A40-GPU node — low latency, high bandwidth.</p>
 *
 * <p><b>Cluster B (Extreme Edge, 5 edge-class devices):</b>
 * 4 Raspberry-Pi-5 nodes + 1 Jetson Orin Nano — higher latency, lower bandwidth.</p>
 *
 * <h3>How the hierarchy is implemented</h3>
 * <ul>
 *   <li>Each cluster has its own {@link FLAggregator} (own IaaS, own
 *       {@code EnergyDataCollectorFL}, own CSV/PNG exports under {@code _edgeA}/{@code _edgeB} suffixes)
 *       and its own {@link FLOrchestrator} (so the two clusters run their FL rounds in parallel
 *       on the shared {@link Timed} event loop).</li>
 *   <li>The {@link HierarchicalSupervisor} inner class is itself a {@code Timed} subscriber that
 *       fires every {@code EDGE_ROUNDS_PER_GLOBAL × roundInterval} ticks. On each firing it
 *       reads both regional models, computes the uniform mean, and calls
 *       {@link FLAggregator#replaceGlobalModel(double[])} on each — the next regional round
 *       starts from the synthesised global model.</li>
 *   <li>The two regional aggregators share a {@code finishedCallback} that counts
 *       down: whichever finishes second triggers final cleanup (energy CSV/PNG,
 *       {@code clearAll()}, and exit).</li>
 * </ul>
 *
 * <h3>What you'll see in the outputs</h3>
 * <ul>
 *   <li>{@code fl_telemetry_edgeA.csv/png} and {@code fl_telemetry_edgeB.csv/png} —
 *       per-cluster round-level telemetry. The "external replace" log lines mark
 *       where the supervisor injected an averaged model.</li>
 *   <li>{@code fl_energy_edgeA.csv/png} and {@code fl_energy_edgeB.csv/png} —
 *       per-cluster energy.</li>
 *   <li>{@code energy.csv} / {@code energy.png} — combined wall-clock energy
 *       trace across both aggregators and all ten device PMs.</li>
 *   <li>Terminal log: each {@code [Hierarchical] Global aggregation #k}
 *       line reports the L2 norms of both regional models and the average,
 *       showing how the two regions diverge between syncs and converge after.</li>
 * </ul>
 */
public class FLHierarchicalDemo {

    // ---------------- Configuration knobs ----------------
    /** How long each FL round takes (cool-down between rounds). */
    private static final long ROUND_INTERVAL_TICKS = 60_000L;     // 60 s
    /** How many FL rounds each <i>regional</i> aggregator runs. */
    private static final int  EDGE_ROUNDS = 6;
    /** How often the higher tier averages the regional models, in regional-round units. */
    private static final int  EDGE_ROUNDS_PER_GLOBAL = 2;          // → 3 global syncs over the run
    /** Model dimension (kept small so the demo runs fast). */
    private static final int  MODEL_DIM = 1_000;
    /** Random seed for reproducibility. */
    private static final long SEED = 42L;

    // Geographic locations for the two regional clusters
    private static final GeoLocation LOC_CLUSTER_A = new GeoLocation(47.4979, 19.0402); // Budapest
    private static final GeoLocation LOC_CLUSTER_B = new GeoLocation(51.4545, -2.5879); // Bristol

    public static void main(String[] args) throws Exception {
        // ---------- 1) Global setup ----------
        SimRandom.setSeed(SEED);
        Random rng = SimRandom.get();
        FLAggregator.setTimeoutRatio(0.80);
        GlobalModelBroadcastEvent.setEpochMultiplier(2.0);

        // Both regional aggregators start from the same initial model so the
        // hierarchical averaging is meaningful from round 0 onwards.
        double[] initWeights = new double[MODEL_DIM];
        for (int i = 0; i < MODEL_DIM; i++) initWeights[i] = rng.nextGaussian() * 0.01;

        final String cloudfile = ScenarioBase.resourcePath + "LPDS_FL_Holographic.xml";
        final String outDir    = ScenarioBase.resultDirectory;
        new java.io.File(outDir).mkdirs();

        // ---------- 2) Build the two regional aggregators ----------
        // CloudLoader.loadNodes() returns a fresh IaaSService per call, and FLAggregator's
        // cloudfile constructor renames its primary repo to "<base>-<id>", so the two
        // aggregators are fully independent (no shared Repository, no shared IaaS).
        System.setProperty("fl.serverRepoId", "ceph-Holographic-Aggregator");

        FLAggregator aggA = new FLAggregator("Edge-A", cloudfile, LOC_CLUSTER_A, 0, initWeights.clone());
        FLAggregator aggB = new FLAggregator("Edge-B", cloudfile, LOC_CLUSTER_B, 0, initWeights.clone());

        for (FLAggregator agg : new FLAggregator[]{aggA, aggB}) {
            agg.enableEnergyFallbackEstimator(false);
            agg.setEnergyCountFailedUploads(true);
            agg.setNativeTransferMeteringEnabled(true);
        }

        FLScenarioBase.attachAggregatorEnergyCollector(aggA, "agg-A");
        FLScenarioBase.attachAggregatorEnergyCollector(aggB, "agg-B");

        aggA.setExportPaths(outDir + "/fl_telemetry_edgeA.csv", outDir + "/fl_telemetry_edgeA.png");
        aggA.setEnergyExportPaths(outDir + "/fl_energy_edgeA.csv", outDir + "/fl_energy_edgeA.png");
        aggB.setExportPaths(outDir + "/fl_telemetry_edgeB.csv", outDir + "/fl_telemetry_edgeB.png");
        aggB.setEnergyExportPaths(outDir + "/fl_energy_edgeB.csv", outDir + "/fl_energy_edgeB.png");

        final String serverRepoA = aggA.getServerRepositoryId();
        final String serverRepoB = aggB.getServerRepositoryId();
        System.out.println("[Hierarchical] Edge-A server repo: " + serverRepoA);
        System.out.println("[Hierarchical] Edge-B server repo: " + serverRepoB);

        // ---------- 3) Build the two device clusters ----------
        // Cluster A: AI Cluster — 2 CPU-only servers + 2 P2000 GPU + 1 A40 GPU
        List<FLEdgeDevice> devicesA = buildClusterA(rng, serverRepoA);
        // Cluster B: Extreme Edge — 4 Raspberry-Pi-5 + 1 Jetson Orin Nano
        List<FLEdgeDevice> devicesB = buildClusterB(rng, serverRepoB);

        System.out.println("[Hierarchical] Cluster A (AI Cluster): " + devicesA.size() + " devices.");
        System.out.println("[Hierarchical] Cluster B (Extreme Edge): " + devicesB.size() + " devices.");

        // ---------- 4) Hierarchical supervisor ----------
        // Constructed before the orchestrators so we have a reference for the
        // shared finishedCallback (used to force one final post-run sync).
        int globalRounds = EDGE_ROUNDS / EDGE_ROUNDS_PER_GLOBAL;          // 3
        HierarchicalSupervisor supervisor = new HierarchicalSupervisor(
                aggA, aggB,
                EDGE_ROUNDS_PER_GLOBAL * ROUND_INTERVAL_TICKS,            // fire interval
                globalRounds);

        // ---------- 5) Shared finishedCallback: cleanup when both edges are done ----------
        AtomicInteger doneCount = new AtomicInteger(0);
        Runnable onEdgeFinish = () -> {
            int done = doneCount.incrementAndGet();
            System.out.println("[Hierarchical] An edge aggregator finished (" + done + "/2).");
            if (done == 2) {
                // Force one last global sync so the final exported global state reflects
                // the post-last-round average of both regional models.
                supervisor.forceFinalSync();
                try {
                    EnergyDataCollectorFL.writeToFile(outDir);
                } catch (Throwable t) {
                    System.out.println("EnergyDataCollectorFL write failed: " + t.getMessage());
                }
                FLTelemetry.plotEnergyTimeseries(
                        outDir + java.io.File.separator + "energy.csv",
                        outDir + java.io.File.separator + "energy.png",
                        "Hierarchical");
                EnergyDataCollectorFL.clearAll();
                System.out.println("[Hierarchical] Demo complete. Outputs in: " + outDir);
                System.out.flush(); System.err.flush();
                System.exit(0);
            }
        };
        aggA.setFinishedCallback(onEdgeFinish);
        aggB.setFinishedCallback(onEdgeFinish);

        // ---------- 6) Two regional orchestrators ----------
        // Both schedule themselves on the shared Timed loop; they run in parallel.
        new FLOrchestrator(
                ROUND_INTERVAL_TICKS, EDGE_ROUNDS, devicesA, aggA,
                /* samplingFraction      */ 0.8,
                /* dropoutProbability    */ 0.1,
                /* preUploadFailureProb  */ 0.0,
                /* inTransitFailureProb  */ 0.05,
                /* minCompletionRate     */ 0.75,
                /* secureAggregationEn   */ false,
                /* secureExtraBytes      */ 0L,
                /* dlCompressionFactor   */ 1.0,
                /* ulCompressionFactor   */ 1.0,
                /* dpNoiseStd            */ 0.0,
                /* fixedCadence          */ false,
                /* broadcastSelectedOnly */ true);
        new FLOrchestrator(
                ROUND_INTERVAL_TICKS, EDGE_ROUNDS, devicesB, aggB,
                0.8, 0.1, 0.0, 0.05, 0.75,
                false, 0L, 1.0, 1.0, 0.0,
                false, true);

        System.out.println("[Hierarchical] Starting simulation: "
                + EDGE_ROUNDS + " regional rounds per aggregator, "
                + globalRounds + " global syncs (every " + EDGE_ROUNDS_PER_GLOBAL + " edge rounds).");

        // ---------- 7) Run the simulation ----------
        Timed.simulateUntilLastEvent();
        System.out.println("[Hierarchical] Timed loop drained — should have exited via finishedCallback.");
    }

    /**
     * AI Cluster — 5 high-end cloud-class devices.
     * Mirrors the AI tier of the HTC scenario: low latency, high bandwidth, GPU acceleration.
     */
    private static List<FLEdgeDevice> buildClusterA(Random rng, String serverRepoId) throws Exception {
        List<FLEdgeDevice> devices = new ArrayList<>();
        final double AI_MIPS = 0.003;
        // worker1: 8-core, no GPU
        devices.add(FLScenarioBase.createFLDevice("A-worker1-cpu", 8, AI_MIPS, 16 * FLScenarioBase.GB,
                "repoA-w1", serverRepoId, LOC_CLUSTER_A, rng,
                5.0e-6, (50 + rng.nextInt(51)) * FLScenarioBase.MB,
                10, 50000 + rng.nextInt(50001)));
        // worker2: 15-core, P2000
        devices.add(FLScenarioBase.createFLDevice("A-worker2-p2000", 15, AI_MIPS * 2.0, 30 * FLScenarioBase.GB,
                "repoA-w2", serverRepoId, LOC_CLUSTER_A, rng,
                1.0e-6, (100 + rng.nextInt(101)) * FLScenarioBase.MB,
                12, 100000 + rng.nextInt(50001)));
        // worker3: 15-core, P2000
        devices.add(FLScenarioBase.createFLDevice("A-worker3-p2000", 15, AI_MIPS * 2.0, 30 * FLScenarioBase.GB,
                "repoA-w3", serverRepoId, LOC_CLUSTER_A, rng,
                1.0e-6, (100 + rng.nextInt(101)) * FLScenarioBase.MB,
                11, 100000 + rng.nextInt(50001)));
        // worker4: 8-core, no GPU
        devices.add(FLScenarioBase.createFLDevice("A-worker4-cpu", 8, AI_MIPS, 16 * FLScenarioBase.GB,
                "repoA-w4", serverRepoId, LOC_CLUSTER_A, rng,
                5.0e-6, (50 + rng.nextInt(51)) * FLScenarioBase.MB,
                10, 50000 + rng.nextInt(50001)));
        // worker5: 12-core, A40
        devices.add(FLScenarioBase.createFLDevice("A-worker5-a40", 12, AI_MIPS * 3.0, 16 * FLScenarioBase.GB,
                "repoA-w5", serverRepoId, LOC_CLUSTER_A, rng,
                5.0e-7, (150 + rng.nextInt(101)) * FLScenarioBase.MB,
                8, 150000 + rng.nextInt(50001)));
        return devices;
    }

    /**
     * Extreme Edge — 5 resource-constrained edge devices.
     * Mirrors the Extreme Edge tier of the HTC scenario: higher latency, lower bandwidth, weak compute.
     */
    private static List<FLEdgeDevice> buildClusterB(Random rng, String serverRepoId) throws Exception {
        List<FLEdgeDevice> devices = new ArrayList<>();
        final double RPI_MIPS    = 0.001;
        final double JETSON_MIPS = 0.0015;
        for (int i = 1; i <= 4; i++) {
            devices.add(FLScenarioBase.createFLDevice("B-rpi-" + i, 4, RPI_MIPS, 8 * FLScenarioBase.GB,
                    "repoB-rpi" + i, serverRepoId, LOC_CLUSTER_B, rng,
                    8.0e-6, (20 + rng.nextInt(11)) * FLScenarioBase.MB,
                    25 + (i - 1), 20000 + rng.nextInt(30001)));
        }
        devices.add(FLScenarioBase.createFLDevice("B-jetson", 6, JETSON_MIPS * 2.5, 8 * FLScenarioBase.GB,
                "repoB-jet", serverRepoId, LOC_CLUSTER_B, rng,
                3.0e-6, (40 + rng.nextInt(21)) * FLScenarioBase.MB,
                22, 40000 + rng.nextInt(30001)));
        return devices;
    }

    // ============================================================================
    // HierarchicalSupervisor — kept as a thin shim around the promoted class
    // ============================================================================
    //
    // The original {@code static final class HierarchicalSupervisor extends Timed}
    // lived here. It has been lifted to the top-level
    // {@link FLHierarchicalSupervisor} so it can be reused by the gossip
    // scenario runner (S0 baseline). To keep this demo's call sites unchanged
    // (and its stdout byte-identical), {@code HierarchicalSupervisor} is now a
    // trivial subclass with the same 4-arg constructor.

    /** Backwards-compatible shim around {@link FLHierarchicalSupervisor}.
     *  Existing demo code constructs and uses this class exactly as before;
     *  log line format is preserved by the promoted class for the 2-region case. */
    static final class HierarchicalSupervisor extends FLHierarchicalSupervisor {
        HierarchicalSupervisor(FLAggregator a, FLAggregator b, long interval, int maxGlobalRounds) {
            super(a, b, interval, maxGlobalRounds);
        }
    }
}
