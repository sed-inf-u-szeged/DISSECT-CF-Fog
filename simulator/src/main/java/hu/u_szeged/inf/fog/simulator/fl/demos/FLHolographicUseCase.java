package hu.u_szeged.inf.fog.simulator.fl.demos;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.fl.*;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;

import java.util.*;

/**
 * A real-world Federated Learning use case simulation for "Holographic-Type Communications"
 * based on the infrastructure (AI Cluster, Extreme Edge).
 *
 * This scenario models:
 * 1.  A central Aggregator: Representing the "cloud1/master" (A100 GPU) from the AI Cluster.
 * 2.  10 Heterogeneous Client Devices: Representing the 5 workers from the "AI Cluster"
 *     (with/without GPUs) and the 5 workers from the "Extreme Edge" cluster (RPi 5, Jetson).
 *
 * Heterogeneity is modeled in:
 * -   Compute: Cores, MIPS (throughput), and compute cost (instrPerByte). Nodes with
 *     GPUs (A40, P2000, Jetson) are given higher throughput and lower compute cost.
 * -   Network: Varied latency and bandwidth for each device.
 * -   Data: Varied local dataset sizes (fileSize).
 *
 * Parameterized via {@link Config}. The {@code FLHolographicUseCase_S0..S4} sibling classes
 * are thin {@code main} entry points that construct a {@link Config}, override the fields
 * that differ for their scenario, and call {@link #run(Config)}.
 */
public class FLHolographicUseCase {

    // Define geographic location (e.g., Bristol, UK)
    private static final double SCENARIO_LAT = 51.4545;
    private static final double SCENARIO_LON = -2.5879;

    // Helper constants — sourced from {@link FLScenarioBase} so device factories,
    // size formatters and any future FL demo all use the same definitions.
    private static final long GB = FLScenarioBase.GB;
    private static final long MB = FLScenarioBase.MB;

    /** Initial-weight initialization scheme for the global model. */
    public enum InitScheme {
        /** Gaussian(0, 0.01) per element — paper baseline. */
        GAUSSIAN_0_01,
        /** Constant fill at 0.001 — fast init used by the scalability scenario. */
        FILL_0_001
    }

    /**
     * Mutable configuration driving a single holographic-use-case run.
     * All fields default to the baseline scenario; variant {@code main}s
     * override only the fields that differ.
     */
    public static class Config {
        // Logging / output naming
        public String label              = "HolographicUseCase";  // prefix for System.out logs
        public String tagPrefix          = "HolographicUseCase";  // prefix of the file tag
        public String outputSuffix       = "";                    // e.g. "_S2" -> fl_telemetry_S2__...

        // FL round parameters
        public int    rounds             = 20;
        public long   roundInterval      = 60_000L;               // 60s per round
        public double timeoutRatio       = 0.80;
        public long   seed               = 123L;

        // Model & data
        public int    modelDimensionW    = 1_000_000;
        public InitScheme initScheme     = InitScheme.GAUSSIAN_0_01;

        // Client selection / failure
        public double samplingFraction   = 0.5;
        public double dropoutProbability = 0.1;
        public double preUploadFail      = 0.0;
        public double inTransitFail      = 0.05;
        public double minCompletionRate  = 0.75;

        // Device fleet: 1 = original 10 descriptively-named devices;
        // N>=2 = N replicas of the 10-device pattern with short names (used by S1).
        public int    deviceReplicationFactor = 1;

        // Privacy & compression (OFF by default in this baseline)
        public boolean secureAgg                  = false;
        public long    secureExtraBytesPerClient  = 0L;
        public double  dlCompressionFactor        = 1.0;
        public double  ulCompressionFactor        = 1.0;
        public double  serverDpNoiseStd           = 0.0;
        public double  clientClipNorm             = 0.0;
        public double  clientDpSigma              = 0.0;

        // Pacing
        public boolean fixedCadence              = false;        // false = Cooldown_After_Finish
        public boolean broadcastSelectedOnly     = true;

        // Sweep-mode controls
        // outputDirOverride: when non-null, all CSV/PNG outputs (fl_telemetry__*, fl_energy__*,
        // energy.csv, energy.png) are written to this directory instead of
        // {@link ScenarioBase#resultDirectory}. Useful for parameter sweeps so each
        // iteration's outputs land in their own folder.
        // exitOnFinish: when false, the finishedCallback skips the {@code System.exit(0)}
        // that normally terminates the JVM, allowing multiple back-to-back runs in
        // one process (S1 and S2 sweeps rely on this).
        public String  outputDirOverride         = null;
        public boolean exitOnFinish              = true;
    }

    public static void main(String[] args) throws Exception {
        run(new Config());
    }

    /** Executes one holographic use-case run with the given config. */
    public static void run(Config cfg) throws Exception {
        // ---------- 0) Simulation Parameters ----------
        // Resource file for the Aggregator's IaaS
        final String cloudfile = ScenarioBase.resourcePath + "LPDS_FL_Holographic.xml";

        // Unique ID for the Aggregator (must match the repo name in the XML)
        // XML repo name: "ceph-Holographic-Aggregator"
        final String AGGREGATOR_ID = "Holographic-Aggregator";

        // Define the expected repo ID to match the XML exactly.
        System.setProperty("fl.serverRepoId", "ceph-Holographic-Aggregator");

        // Tag for exported files
        final String tag = String.format("%s_R%d_W%d_seed%d",
                cfg.tagPrefix, cfg.rounds, cfg.modelDimensionW, cfg.seed);

        System.out.println("[" + cfg.label + "] Starting run with "
                + "Rounds=" + cfg.rounds
                + ", |w|=" + cfg.modelDimensionW
                + ", seed=" + cfg.seed
                + ", roundInterval=" + cfg.roundInterval
                + ", sampling=" + cfg.samplingFraction);

        // ---------- 1) Setup Simulation Environment ----------
        SimRandom.setSeed(cfg.seed);
        Random rng = SimRandom.get();
        FLAggregator.setTimeoutRatio(cfg.timeoutRatio);

        // Set epoch multiplier (from GlobalModelBroadcastEvent)
        GlobalModelBroadcastEvent.setEpochMultiplier(2.0);

        // ---------- 2) Initial global weights ----------
        double[] initWeights = new double[cfg.modelDimensionW];
        switch (cfg.initScheme) {
            case FILL_0_001:
                Arrays.fill(initWeights, 0.001);
                break;
            case GAUSSIAN_0_01:
            default:
                for (int i = 0; i < initWeights.length; i++) {
                    initWeights[i] = rng.nextGaussian() * 0.01;
                }
                break;
        }

        // ---------- 3) Create Aggregator (Cloud Master) ----------
        GeoLocation aggLoc = new GeoLocation(SCENARIO_LAT, SCENARIO_LON);
        FLAggregator aggregator = new FLAggregator(AGGREGATOR_ID, cloudfile, aggLoc, 0, initWeights);

        // The aggregator's repo ID match the one in the XML for wiring
        final String SERVER_REPO_ID = aggregator.getServerRepositoryId();
        System.out.println("[" + cfg.label + "] Aggregator created, server repo ID: " + SERVER_REPO_ID);

        // Configure energy metering and CSV/PNG export paths
        aggregator.enableEnergyFallbackEstimator(false);
        aggregator.setNativeTransferMeteringEnabled(true);
        FLScenarioBase.attachAggregatorEnergyCollector(aggregator, "aggregator");

        // Resolve output directory: use the explicit override (used by S1/S2 sweep
        // mains so each iteration writes to its own subdirectory) or fall back to
        // the global ScenarioBase.resultDirectory. The directory is created if it
        // does not yet exist so callers don't have to mkdirs themselves.
        final String outDir = (cfg.outputDirOverride != null && !cfg.outputDirOverride.isEmpty())
                ? cfg.outputDirOverride
                : ScenarioBase.resultDirectory;
        new java.io.File(outDir).mkdirs();

        final String telemetryBase = "fl_telemetry" + cfg.outputSuffix + "__";
        final String energyBase    = "fl_energy"    + cfg.outputSuffix + "__";

        aggregator.setExportPaths(
                outDir + "/" + telemetryBase + tag + ".csv",
                outDir + "/" + telemetryBase + tag + ".png"
        );
        aggregator.setEnergyExportPaths(
                outDir + "/" + energyBase + tag + ".csv",
                outDir + "/" + energyBase + tag + ".png"
        );

        // Setup shutdown hook
        final String finalTag = tag;
        final String finalLabel = cfg.label;
        final String finalOutDir = outDir;
        final boolean finalExitOnFinish = cfg.exitOnFinish;
        aggregator.setFinishedCallback(() -> {
            try {
                EnergyDataCollectorFL.writeToFile(finalOutDir);
            } catch (Throwable t) {
                System.out.println("EnergyDataCollectorFL write failed: " + t.getMessage());
            }
            // Plot the time-series energy.csv (one row per 60 s sampling tick, cumulative
            // kWh per collector — aggregator + each device PM). Complements fl_energy.png
            // (per-round deltas) with a wall-clock view of energy growth.
            FLTelemetry.plotEnergyTimeseries(
                    finalOutDir + java.io.File.separator + "energy.csv",
                    finalOutDir + java.io.File.separator + "energy.png",
                    finalLabel);
            // HIGH-2 follow-up: release native meters and clear static state so repeated
            // runs in the same JVM (parameter sweeps, test suites) don't leak meters.
            EnergyDataCollectorFL.clearAll();
            System.out.println("[" + finalLabel + "] Finished run " + finalTag + ".");
            System.out.flush(); System.err.flush();
            // Skip System.exit when the caller is driving a multi-run sweep (S1/S2).
            // The caller is responsible for the final exit after the loop completes.
            if (finalExitOnFinish) {
                System.exit(0);
            }
        });

        // ---------- 4) Create Heterogeneous FL Devices (Workers) ----------
        List<FLEdgeDevice> flDevices = createDevices(cfg, aggLoc, SERVER_REPO_ID, rng);
        System.out.println("[" + cfg.label + "] Created " + flDevices.size() + " heterogeneous devices.");

        // ---------- 5) Kick off round 0 ----------
        System.out.println("[" + cfg.label + "] Starting FLOrchestrator for " + cfg.rounds + " rounds...");
        new FLOrchestrator(
                cfg.roundInterval,
                cfg.rounds,
                flDevices,
                aggregator,
                cfg.samplingFraction,
                cfg.dropoutProbability,
                cfg.preUploadFail,
                cfg.inTransitFail,
                cfg.minCompletionRate,
                cfg.secureAgg,
                cfg.secureExtraBytesPerClient,
                cfg.dlCompressionFactor,
                cfg.ulCompressionFactor,
                cfg.serverDpNoiseStd,
                cfg.fixedCadence,
                cfg.broadcastSelectedOnly,
                false,  // useFixedKSampling
                0       // fixedK
        );

        // ---------- 6) Run simulation ----------
        Timed.simulateUntilLastEvent();
        System.out.println("[" + cfg.label + "] Simulation finished for " + tag);
    }

    /**
     * Creates the FL edge device fleet for the HTC scenario.
     *
     * <ul>
     *   <li>{@code deviceReplicationFactor == 1}: produces the original 10 descriptively-named
     *       devices (worker1-cloud..worker-jetson). Used by base and S0/S2/S3/S4.</li>
     *   <li>{@code deviceReplicationFactor >= 2}: produces N replicas of the 10-device pattern
     *       using short names (w1..jet) with a {@code -setK} suffix. Used by S1 (scalability).</li>
     * </ul>
     */
    private static List<FLEdgeDevice> createDevices(Config cfg,
                                                    GeoLocation aggLoc,
                                                    String serverRepoId,
                                                    Random rng) throws Exception {
        // Realistic bandwidth: 50,000-150,000 B/tick (50-150 MB/s)
        // Realistic compute cost: 1e-7 to 5e-6 instr/Byte
        final double AI_CLUSTER_CORE_MIPS = 0.003;
        final double RPI_CORE_MIPS        = 0.001;
        final double JETSON_CORE_MIPS     = 0.0015;

        List<FLEdgeDevice> devices = new ArrayList<>();

        if (cfg.deviceReplicationFactor <= 1) {
            // --- AI Cluster Devices ---

            // worker1: 8 core, 16GB RAM, no GPU
            devices.add(FLScenarioBase.createFLDevice(
                    "worker1-cloud", 8, AI_CLUSTER_CORE_MIPS, 16 * GB,
                    "repo-worker-cloud-1", serverRepoId, aggLoc, rng,
                    5.0e-6,                          // instrPerByte (standard cost)
                    (50 + rng.nextInt(51)) * MB,     // fileSize (50-100MB)
                    10, 50000 + rng.nextInt(50001)   // latency (10ms), bandwidth (50-100 MB/s)
            ));

            // worker2: 15 core, 30GB RAM, Quadro P2000
            devices.add(FLScenarioBase.createFLDevice(
                    "worker2-cloud-p2000", 15, AI_CLUSTER_CORE_MIPS * 2.0, 30 * GB,  // Double MIPS for GPU
                    "repo-worker-cloud-2", serverRepoId, aggLoc, rng,
                    1.0e-6,                          // instrPerByte (low cost due to GPU)
                    (100 + rng.nextInt(101)) * MB,   // fileSize (100-200MB)
                    12, 100000 + rng.nextInt(50001)  // latency (12ms), bandwidth (100-150 MB/s)
            ));

            // worker3: 15 core, 30GB RAM, Quadro P2000
            devices.add(FLScenarioBase.createFLDevice(
                    "worker3-cloud-p2000", 15, AI_CLUSTER_CORE_MIPS * 2.0, 30 * GB,
                    "repo-worker-cloud-3", serverRepoId, aggLoc, rng,
                    1.0e-6,
                    (100 + rng.nextInt(101)) * MB,
                    11, 100000 + rng.nextInt(50001)
            ));

            // worker4: 8 core, 16GB RAM, no GPU
            devices.add(FLScenarioBase.createFLDevice(
                    "worker4-cloud", 8, AI_CLUSTER_CORE_MIPS, 16 * GB,
                    "repo-worker-cloud-4", serverRepoId, aggLoc, rng,
                    5.0e-6,
                    (50 + rng.nextInt(51)) * MB,
                    10, 50000 + rng.nextInt(50001)
            ));

            // worker5: 12 core, 16GB RAM, A40
            devices.add(FLScenarioBase.createFLDevice(
                    "worker5-cloud-a40", 12, AI_CLUSTER_CORE_MIPS * 3.0, 16 * GB,    // Triple MIPS for A40
                    "repo-worker-cloud-5", serverRepoId, aggLoc, rng,
                    5.0e-7,                          // instrPerByte (very low cost due to A40)
                    (150 + rng.nextInt(101)) * MB,   // fileSize (150-250MB)
                    8, 150000 + rng.nextInt(50001)   // latency (8ms), bandwidth (150-200 MB/s)
            ));

            // --- Extreme Edge Devices ---

            // worker-rpi-1 (4 core @ 2.4Ghz, 8GB RAM)
            devices.add(FLScenarioBase.createFLDevice(
                    "worker-rpi-1", 4, RPI_CORE_MIPS, 8 * GB,
                    "repo-worker-rpi-1", serverRepoId, aggLoc, rng,
                    8.0e-6,                          // instrPerByte (high cost, slow CPU)
                    (20 + rng.nextInt(11)) * MB,     // fileSize (20-30MB)
                    25, 20000 + rng.nextInt(30001)   // latency (25ms), bandwidth (20-50 MB/s)
            ));

            // worker-rpi-2
            devices.add(FLScenarioBase.createFLDevice(
                    "worker-rpi-2", 4, RPI_CORE_MIPS, 8 * GB,
                    "repo-worker-rpi-2", serverRepoId, aggLoc, rng,
                    8.0e-6,
                    (20 + rng.nextInt(11)) * MB,
                    28, 20000 + rng.nextInt(30001)
            ));

            // worker-rpi-3
            devices.add(FLScenarioBase.createFLDevice(
                    "worker-rpi-3", 4, RPI_CORE_MIPS, 8 * GB,
                    "repo-worker-rpi-3", serverRepoId, aggLoc, rng,
                    8.0e-6,
                    (20 + rng.nextInt(11)) * MB,
                    26, 20000 + rng.nextInt(30001)
            ));

            // worker-rpi-4
            devices.add(FLScenarioBase.createFLDevice(
                    "worker-rpi-4", 4, RPI_CORE_MIPS, 8 * GB,
                    "repo-worker-rpi-4", serverRepoId, aggLoc, rng,
                    8.0e-6,
                    (20 + rng.nextInt(11)) * MB,
                    30, 20000 + rng.nextInt(30001)
            ));

            // worker-jetson (6 core @ 1.5Ghz, 8GB RAM, Ampere-1024)
            devices.add(FLScenarioBase.createFLDevice(
                    "worker-jetson", 6, JETSON_CORE_MIPS * 2.5, 8 * GB,              // CPU + GPU boost
                    "repo-worker-jetson", serverRepoId, aggLoc, rng,
                    3.0e-6,                          // instrPerByte (medium-low cost due to GPU)
                    (40 + rng.nextInt(21)) * MB,     // fileSize (40-60MB)
                    22, 40000 + rng.nextInt(30001)   // latency (22ms), bandwidth (40-70 MB/s)
            ));
        } else {
            // Scalability scenario (S1): repeat the 10-device pattern N times with short names.
            final int N = cfg.deviceReplicationFactor;
            for (int i = 0; i < N; i++) {
                String suffix = "-set" + i;
                devices.add(FLScenarioBase.createFLDevice("w1" + suffix,  8,  AI_CLUSTER_CORE_MIPS,        16 * GB, "r-w1" + suffix, serverRepoId, aggLoc, rng, 5e-6, (50  + rng.nextInt(51))  * MB, 10, 50000  + rng.nextInt(50001)));
                devices.add(FLScenarioBase.createFLDevice("w2" + suffix,  15, AI_CLUSTER_CORE_MIPS * 2.0,  30 * GB, "r-w2" + suffix, serverRepoId, aggLoc, rng, 1e-6, (100 + rng.nextInt(101)) * MB, 12, 100000 + rng.nextInt(50001)));
                devices.add(FLScenarioBase.createFLDevice("w3" + suffix,  15, AI_CLUSTER_CORE_MIPS * 2.0,  30 * GB, "r-w3" + suffix, serverRepoId, aggLoc, rng, 1e-6, (100 + rng.nextInt(101)) * MB, 11, 100000 + rng.nextInt(50001)));
                devices.add(FLScenarioBase.createFLDevice("w4" + suffix,  8,  AI_CLUSTER_CORE_MIPS,        16 * GB, "r-w4" + suffix, serverRepoId, aggLoc, rng, 5e-6, (50  + rng.nextInt(51))  * MB, 10, 50000  + rng.nextInt(50001)));
                devices.add(FLScenarioBase.createFLDevice("w5" + suffix,  12, AI_CLUSTER_CORE_MIPS * 3.0,  16 * GB, "r-w5" + suffix, serverRepoId, aggLoc, rng, 5e-7, (150 + rng.nextInt(101)) * MB,  8, 150000 + rng.nextInt(50001)));
                devices.add(FLScenarioBase.createFLDevice("rp1" + suffix, 4,  RPI_CORE_MIPS,                8 * GB, "r-p1" + suffix, serverRepoId, aggLoc, rng, 8e-6, (20  + rng.nextInt(11))  * MB, 25, 20000  + rng.nextInt(30001)));
                devices.add(FLScenarioBase.createFLDevice("rp2" + suffix, 4,  RPI_CORE_MIPS,                8 * GB, "r-p2" + suffix, serverRepoId, aggLoc, rng, 8e-6, (20  + rng.nextInt(11))  * MB, 28, 20000  + rng.nextInt(30001)));
                devices.add(FLScenarioBase.createFLDevice("rp3" + suffix, 4,  RPI_CORE_MIPS,                8 * GB, "r-p3" + suffix, serverRepoId, aggLoc, rng, 8e-6, (20  + rng.nextInt(11))  * MB, 26, 20000  + rng.nextInt(30001)));
                devices.add(FLScenarioBase.createFLDevice("rp4" + suffix, 4,  RPI_CORE_MIPS,                8 * GB, "r-p4" + suffix, serverRepoId, aggLoc, rng, 8e-6, (20  + rng.nextInt(11))  * MB, 30, 20000  + rng.nextInt(30001)));
                devices.add(FLScenarioBase.createFLDevice("jet" + suffix, 6,  JETSON_CORE_MIPS * 2.5,       8 * GB, "r-jt" + suffix, serverRepoId, aggLoc, rng, 3e-6, (40  + rng.nextInt(21))  * MB, 22, 40000  + rng.nextInt(30001)));
            }
        }

        return devices;
    }

}
