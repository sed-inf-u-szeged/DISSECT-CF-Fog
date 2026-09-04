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

import java.io.File;
import java.util.*;

public class FLSimulationExample_S2 {

    // ---- Helpers to read system properties with defaults ----
    private static int    I(String k, int d)    { return Integer.getInteger(k, d); }
    private static long   L(String k, long d)   { return Long.getLong(k, d); }
    private static double D(String k, double d) {
        String v = System.getProperty(k);
        if (v == null) return d;
        try { return Double.parseDouble(v); } catch (Exception e) { return d; }
    }
    private static boolean B(String k, boolean d) {
        String v = System.getProperty(k);
        if (v == null) return d;
        return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("1") || v.equalsIgnoreCase("yes");
    }

    public static void main(String[] args) throws Exception {
        // =======================
        // 0) Scenario parameters
        // =======================
        final long   seed          = L("seed", 48L);
        final int    N             = I("N", 50);             // number of edge devices
        final int    modelSize     = I("modelSize", 3);      // |w|
        final int    rounds        = I("rounds", 10);        // 0..rounds-1
        final long   roundInterval = L("roundInterval", 22_000L); // REQUIRED by S2
        final double rmin          = D("rmin", 0.60);        // ρ_min (minCompletionRate)
        final double rto           = D("rto", 0.5);         // ρ_to  (timeout ratio)
        final double samplingFrac  = D("samplingFraction", 1.0);
        final double dropoutProb   = D("dropoutProbability", 0.5);
        final double preUF         = D("preUploadFail", 0.10);
        final double inTransF      = D("inTransitFail", 0.05);

        // Security/Privacy/Compression knobs (kept constant for S2 unless you override)
        final boolean secureAgg    = B("secureAgg", true);
        final long    secBytes     = L("secureBytes", 256L);
        final double  dlComp       = D("dlComp", 0.5);
        final double  ulComp       = D("ulComp", 0.5);
        final double  serverDP     = D("serverSigma", 0.01); // aggregator DP σ
        final double  clientClip   = D("clientClip", 0.10);
        final double  clientSigma  = D("clientSigma", 0.02);

        // Pacing/broadcast
        final boolean fixedCadence = B("fixedCadence", false);            // cooldown by default for S2
        final boolean bcastSelOnly = B("broadcastSelectedOnly", true);
        final boolean useFixedK    = B("useFixedK", false);
        final int     fixedK       = I("fixedK", 3);

        // Compute epoch multiplier (affects local compute time only):
        GlobalModelBroadcastEvent.setEpochMultiplier(D("epochMult", 1.0));

        // Random & reproducibility
        SimRandom.setSeed(seed);
        Random rng = SimRandom.get();

        System.out.println("=== S2 Aggregation Policies Experiment ===");
        System.out.println("seed=" + seed + ", N=" + N + ", modelSize=" + modelSize
                + ", rounds=" + rounds + ", roundInterval=" + roundInterval
                + ", rmin=" + rmin + ", rto=" + rto);
        System.out.println("samplingFraction=" + samplingFrac + ", dropoutProb=" + dropoutProb
                + ", preUF=" + preUF + ", inTransF=" + inTransF);
        System.out.println("secureAgg=" + secureAgg + " (+" + secBytes + "B), dlComp=" + dlComp
                + ", ulComp=" + ulComp + ", serverDP=" + serverDP
                + ", clientClip=" + clientClip + ", clientSigma=" + clientSigma);
        System.out.println("fixedCadence=" + fixedCadence + ", broadcastSelectedOnly=" + bcastSelOnly);

        // Apply timeout ratio (ρ_to)
        FLAggregator.setTimeoutRatio(rto);

        // Prepare result subfolder tagged by config
        String tag = String.format(Locale.US, "S2_rmin%.2f_rto%.2f_seed%d", rmin, rto, seed);
        String outDir = ScenarioBase.resultDirectory + File.separator + tag;
        new File(outDir).mkdirs();

        // =======================
        // 1) Aggregator setup
        // =======================
        final String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";
        final GeoLocation aggLoc = new GeoLocation(47.4979, 19.0402); // Budapest
        double[] initW = new double[modelSize];
        for (int i = 0; i < initW.length; i++) initW[i] = rng.nextGaussian() * 0.01;

        FLAggregator aggregator = new FLAggregator("FL-Aggregator-S2", cloudfile, aggLoc, 0, initW);

        // Energy choices
        aggregator.enableEnergyFallbackEstimator(true); // keep native if available
        aggregator.setEnergyCountFailedUploads(true);

        // IaaS-level energy metering for server
        new EnergyDataCollectorFL("aggregator", aggregator.iaas, true);

        // Export paths (telemetry + energy) into the tagged folder
        aggregator.setExportPaths(outDir + "/fl_telemetry.csv", outDir + "/fl_telemetry.png");
        aggregator.setEnergyExportPaths(outDir + "/fl_energy.csv", outDir + "/fl_energy.png");

        // On finish, flush EnergyDataCollectorFL CSVs/PNGs once and exit
        aggregator.setFinishedCallback(() -> {
            try {
                EnergyDataCollectorFL.writeToFile(outDir);
            } catch (Throwable t) {
                System.out.println("EnergyDataCollectorFL: failed to write results: " + t.getMessage());
            }
            // Plot the time-series energy.csv (cumulative kWh per collector). Complements
            // fl_energy.png (per-round deltas) with a wall-clock view of energy growth.
            FLTelemetry.plotEnergyTimeseries(
                    outDir + java.io.File.separator + "energy.csv",
                    outDir + java.io.File.separator + "energy.png",
                    "FL-Aggregator-S2");
            // HIGH-2 follow-up: release native meters and clear static state so repeated
            // runs in the same JVM (parameter sweeps, test suites) don't leak meters.
            EnergyDataCollectorFL.clearAll();
            System.out.println("S2 run complete → " + outDir);
            System.out.flush();
            System.err.flush();
            System.exit(0);
        });

        // =======================
        // 2) Device pool (Centralized FL; Budapest ±0.5°)
        // =======================
        List<FLEdgeDevice> devices = new ArrayList<>(N);

        // Power transitions (dummy) shared per device creation
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> trans =
                PowerTransitionGenerator.generateTransitions(2.5, 10, 1.0, 3, 3);
        Map<String, PowerState> stT  = trans.get(PowerTransitionGenerator.PowerStateKind.storage);
        Map<String, PowerState> nwT  = trans.get(PowerTransitionGenerator.PowerStateKind.network);
        Map<String, PowerState> cpuT = trans.get(PowerTransitionGenerator.PowerStateKind.host);

        for (int i = 0; i < N; i++) {
            try {
                long start = 0L, stop = 1L * 60 * 60 * 1000;  // 1h
                long fileSize = 50 + rng.nextInt(151);        // 50–200 B
                long freqMs   = 30_000 + rng.nextInt(90_001); // 30–120 s

                // Geo within ~±0.5° box around Budapest
                double baseLat = 47.4979, baseLon = 19.0402;
                double lat = baseLat + (rng.nextDouble() - 0.5) * 1.0; // [-0.5, +0.5]
                double lon = baseLon + (rng.nextDouble() - 0.5) * 1.0;
                GeoLocation loc = new GeoLocation(lat, lon);

                RandomWalkMobilityStrategy mobility =
                        new RandomWalkMobilityStrategy(loc, 0.0027, 0.0055, 10_000);
                RandomDeviceStrategy devStrat = new RandomDeviceStrategy();

                // Repository per device
                Repository repo = new Repository(
                        4_294_967_296L,
                        "dev-repo-" + i,
                        3250, 3250, 3250,
                        new HashMap<>(),
                        stT, nwT
                );

                // Compute heterogeneity
                int cores = 1 + rng.nextInt(4);                    // 1–4
                double mipsPerPE = 0.001 + rng.nextDouble() * 0.004; // 0.001–0.005
                PhysicalMachine pm = new PhysicalMachine(cores, mipsPerPE, 2_147_483_648L, repo, 0, 0, cpuT);

                // Network heterogeneity
                long bandwidth = 50 + rng.nextInt(151); // 50–200 B/tick
                int  latency   = 30 + rng.nextInt(71);  // 30–100 ticks

                // Local training parameters
                double instrPerByte = 0.05 + rng.nextDouble() * 0.10; // 0.05–0.15
                double throughput = cores * mipsPerPE;                // instr/tick

                FLEdgeDevice dev = new FLEdgeDevice(
                        start, stop, fileSize, freqMs,
                        mobility, devStrat, pm,
                        instrPerByte, latency, bandwidth, throughput,
                        clientClip, clientSigma,
                        true
                );
                devices.add(dev);

                // Meter each device's local PM
                new EnergyDataCollectorFL("device-" + i, pm, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // =======================
        // 3) Launch the FL process
        // =======================
        // minCompletionRate = ρ_min
        final double minCompletionRate = rmin;

        // Orchestrator 0 (round 0); next rounds scheduled according to pacing
        new FLOrchestrator(
                roundInterval,
                rounds,
                devices,
                aggregator,
                // sampling & failure knobs
                samplingFrac,
                dropoutProb,
                preUF,
                inTransF,
                minCompletionRate,
                // privacy/security/codec
                secureAgg,
                secBytes,
                dlComp,
                ulComp,
                serverDP,
                // pacing & broadcast
                fixedCadence,
                bcastSelOnly,
                // optional fixed-k sampling
                useFixedK,
                fixedK
        );

        // =======================
        // 4) Run
        // =======================
        Timed.simulateUntilLastEvent();
        System.out.println("FL S2 simulation finished (no events left).");
    }
}
