package hu.u_szeged.inf.fog.simulator.fl.demos;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.fl.*;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;

import java.util.*;

/**
 * S1 scalability runner: single configuration per JVM.
 * Parameters via -D flags (with safe defaults):
 *
 *   -DN=<int>            number of devices (default: 100)
 *   -DW=<int>            model dimension |w| (default: 5000)
 *   -DROUNDS=<int>       number of FL rounds (default: 10)
 *   -Dseed=<long>        RNG seed (default: 42)
 *   -DroundInterval=<long>  round interval ticks (default: 22000)
 *
 * Exports CSV/PNG tagged with S1_N{N}_W{W}_seed{seed}.
 *
 * This program is intentionally "one-run" to avoid global simulator reset issues.
 */
public class FLSimulationExample_S1 {

    // Budapest center for S1
    private static final double BUD_LAT = 47.4979;
    private static final double BUD_LON = 19.0402;
    
    public static void main(String[] args) throws Exception {
        // ---------- 0) Parse parameters ----------
        final int    N               = Integer.getInteger("N", 100);
        final int    W               = Integer.getInteger("W", 5000);
        final int    ROUNDS          = Integer.getInteger("ROUNDS", 10);
        final long   SEED            = Long.getLong("seed", 42L);
        final long   ROUND_INTERVAL  = Long.getLong("roundInterval", 22_000L);
        
        // Repositories and Energy Switches
        final boolean ENABLE_REPO_WIRING      = Boolean.parseBoolean(System.getProperty("enableRepoWiring", "true"));
        final boolean ENABLE_NATIVE_METERING  = Boolean.parseBoolean(System.getProperty("nativeMetering", "true"));
        final boolean ENABLE_ENERGY_FALLBACK  = Boolean.parseBoolean(System.getProperty("energyFallback", "false"));
        final int     REPO_POOL               = Math.max(1, Integer.getInteger("repoPool", 10));
        
        System.out.println("[S1] Starting run with N=" + N + ", |w|=" + W
                + ", rounds=" + ROUNDS + ", seed=" + SEED
                + ", roundInterval=" + ROUND_INTERVAL
                + " | enableRepoWiring=" + ENABLE_REPO_WIRING
                + ", nativeMetering=" + ENABLE_NATIVE_METERING
                + ", energyFallback=" + ENABLE_ENERGY_FALLBACK
                + ", repoPool=" + REPO_POOL + ")");

        // Reproducibility
        SimRandom.setSeed(SEED);
        Random rng = SimRandom.get();

        // Timeout policy (70% of round interval)
        FLAggregator.setTimeoutRatio(0.70);

        // ---------- 1) Initial global weights ----------
        double[] initWeights = new double[Math.max(1, W)];
        for (int i = 0; i < initWeights.length; i++) {
            initWeights[i] = rng.nextGaussian() * 0.01;
        }

        // Cloud IaaS backing for aggregator (energy metering)
        final String cloudfile = ScenarioBase.resourcePath + "LPDS_FL_original.xml";
        final GeoLocation aggLoc = new GeoLocation(BUD_LAT, BUD_LON);

        // ---------- 2) Create aggregator ----------
        FLAggregator aggregator = new FLAggregator("FL-Aggregator-S1", cloudfile, aggLoc, 0, initWeights);
        // Make Energy configurable
        aggregator.enableEnergyFallbackEstimator(ENABLE_ENERGY_FALLBACK);
        aggregator.setEnergyCountFailedUploads(true);
        aggregator.setNativeTransferMeteringEnabled(ENABLE_NATIVE_METERING);

        // Energy collector for the aggregator's IaaS
        new EnergyDataCollectorFL("aggregator", aggregator.iaas, true);

        // File suffix for exports
        String tag = String.format("S1_N%d_W%d_seed%d", N, W, SEED);

        aggregator.setExportPaths(
                ScenarioBase.resultDirectory + "/fl_telemetry__" + tag + ".csv",
                ScenarioBase.resultDirectory + "/fl_telemetry__" + tag + ".png"
        );
        aggregator.setEnergyExportPaths(
                ScenarioBase.resultDirectory + "/fl_energy__" + tag + ".csv",
                ScenarioBase.resultDirectory + "/fl_energy__" + tag + ".png"
        );

        // Stop the JVM cleanly after the last export
        aggregator.setFinishedCallback(() -> {
            try {
                EnergyDataCollectorFL.writeToFile(ScenarioBase.resultDirectory);
            } catch (Throwable t) {
                System.out.println("EnergyDataCollectorFL write failed: " + t.getMessage());
            }
            // Plot the time-series energy.csv (one row per 60 s sampling tick, cumulative
            // kWh per collector). Complements fl_energy.png (per-round deltas).
            FLTelemetry.plotEnergyTimeseries(
                    ScenarioBase.resultDirectory + java.io.File.separator + "energy.csv",
                    ScenarioBase.resultDirectory + java.io.File.separator + "energy.png",
                    "FL-Aggregator-S1");
            // HIGH-2 follow-up: release native meters and clear static state so repeated
            // runs in the same JVM (parameter sweeps, test suites) don't leak meters.
            EnergyDataCollectorFL.clearAll();
            System.out.println("[S1] Finished run " + tag + ". Exiting.");
            System.out.flush();
            System.err.flush();
            System.exit(0);
        });

        // ---------- 3) Create N heterogeneous devices within 0.5° radius ----------
        List<FLEdgeDevice> flDevices = new ArrayList<>(N);
        
        // Server repo for wiring
        final Repository serverRepo = aggregator.getServerRepository(); // may be null if IaaS absent
        
        //We know the XML id pattern used for the FL aggregator repo: <repository id="ceph-FL-Aggregator-S1" .../>
        final String SERVER_REPO_ID_FOR_XML = aggregator.getServerRepositoryId();
        
        for (int i = 0; i < N; i++) {
            try {
                // Activate for a long enough window
                long startTime = 0L;
                long stopTime  = 2L * 60 * 60 * 1000; // 2h ticks window

                // Synthetic data / sensing schedule (not critical to S1)
                long fileSize = 50 + rng.nextInt(151);        // 50–200 B
                long freq     = 30_000 + rng.nextInt(90_001); // 30–120 s

                // Geo sampling in a circle of radius <= 0.5 degrees (approx)
                double rDeg  = 0.5 * Math.sqrt(rng.nextDouble()); // area-uniform
                double theta = 2 * Math.PI * rng.nextDouble();
                double lat   = BUD_LAT + rDeg * Math.cos(theta);
                double lon   = BUD_LON + rDeg * Math.sin(theta);

                GeoLocation location = new GeoLocation(lat, lon);
                RandomWalkMobilityStrategy mobility = new RandomWalkMobilityStrategy(
                        location, 0.0027, 0.0055, 10_000);

                RandomDeviceStrategy deviceStrategy = new RandomDeviceStrategy();

                // Dummy repo + power transitions
                HashMap<String, Integer> latencyMap = new HashMap<>();
                if (ENABLE_REPO_WIRING && serverRepo != null) {
                    // device -> server (reverse direction). The server -> device direction is already in the XML.
                    int latencyToServer = 30 + rng.nextInt(71); // will match the device's own latency below
                    latencyMap.put(SERVER_REPO_ID_FOR_XML, Math.max(1, latencyToServer));
                }
                
                EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                        PowerTransitionGenerator.generateTransitions(2.5, 10, 1.0, 3, 3);
                Map<String, PowerState> diskT = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
                Map<String, PowerState> netT  = transitions.get(PowerTransitionGenerator.PowerStateKind.network);
                Map<String, PowerState> cpuT  = transitions.get(PowerTransitionGenerator.PowerStateKind.host);

                final String repoName = (ENABLE_REPO_WIRING && serverRepo != null)
                        ? ("dummy-repo-" + (i % REPO_POOL))
                        : ("dummy-repo-" + i); // fallback identical to prior behavior
                
                long capBytes = 128L * 1024 * 1024 * 1024; // 128 GB para holgura
                long repoReadEgress  = 20_000_000L;        // ~5 MB/tick (ajustar según S, K, T)
                long repoWriteIngress= 5_000_000L;        // ~1 MB/tick (publicaciones al repo)
                long repoNetCap      = 20_000_000L;        // igual que egress

                Repository repo = new Repository(
                    capBytes,
                    repoName,
                    repoReadEgress, repoWriteIngress, repoNetCap,
                    latencyMap, diskT, netT
                );

                // Compute heterogeneity
                int cores        = 1 + rng.nextInt(4);                 // 1–4
                double mipsPerPE = 0.001 + rng.nextDouble() * 0.004;   // 0.001–0.005 instr/tick/core
                PhysicalMachine pm = new PhysicalMachine(
                        cores, mipsPerPE, 2_147_483_648L, repo, 0, 0, cpuT);

                // Network heterogeneity
                long bandwidth = 50 + rng.nextInt(151);  // 50–200 B/tick
                int  latency   = 30 + rng.nextInt(71);   // 30–100 ticks
                
                // (Optional) align the map value to the actual per-device latency:
                if (ENABLE_REPO_WIRING && serverRepo != null) {
                    latencyMap.put(SERVER_REPO_ID_FOR_XML, Math.max(1, latency));
                }
                
                // Local training cost & throughput
                double instrPerByte = 0.05 + rng.nextDouble() * 0.10; // 0.05–0.15
                double throughput   = cores * mipsPerPE;               // instr/tick

                // **Client DP OFF for S1** (pure scalability)
                double clientClipNorm = 0.0;
                double clientDP_Sigma = 0.0;

                FLEdgeDevice dev = new FLEdgeDevice(
                        startTime, stopTime, fileSize, freq,
                        mobility, deviceStrategy, pm,
                        instrPerByte, latency, bandwidth, throughput,
                        clientClipNorm, clientDP_Sigma,
                        true
                );
                flDevices.add(dev);
                
                // Energy metering for each device
                new EnergyDataCollectorFL("device-" + i, pm, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // ---------- 4) FL hyper-parameters for S1 (pure scalability) ----------
        int    desiredRounds      = Math.max(1, ROUNDS);
        double samplingFraction   = 1.0;     // sample everyone
        double dropoutProbability = 0.0;     // no dropouts
        double preUploadFail      = 0.0;     // no pre-send loss
        double inTransitFail      = 0.0;     // no in-flight loss

        boolean fixedCadence = false;        // next round starts after finish (cool-down)
        boolean broadcastSelectedOnly = true;

        // Privacy/Compression OFF
        boolean secureAgg    = false;
        long    extraBytes   = 0L;           // no overhead
        double  dlCompFactor = 1.0;          // no compression
        double  ulCompFactor = 1.0;          // no compression
        double  serverDP     = 0.0;          // DP off at server

        // All updates should arrive before aggregation
        double  minCompletionRate = 1.0;

        // Epoch multiplier = 1.0 (leave as default)
        GlobalModelBroadcastEvent.setEpochMultiplier(1.0);

        // ---------- 5) Kick off round 0 ----------
        new FLOrchestrator(
                ROUND_INTERVAL,
                desiredRounds,
                flDevices,
                aggregator,
                samplingFraction,
                dropoutProbability,
                preUploadFail,
                inTransitFail,
                minCompletionRate,
                secureAgg,
                extraBytes,
                dlCompFactor,
                ulCompFactor,
                serverDP,
                fixedCadence,
                broadcastSelectedOnly,
                false,         // useFixedKSampling
                0              // fixedK
        );

        // ---------- 6) Run simulation ----------
        Timed.simulateUntilLastEvent();
        System.out.println("[S1] Simulation finished for " + tag);
    }
}
