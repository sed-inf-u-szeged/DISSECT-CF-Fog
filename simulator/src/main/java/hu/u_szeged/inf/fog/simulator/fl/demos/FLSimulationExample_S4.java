package hu.u_szeged.inf.fog.simulator.fl.demos;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.fl.FLAggregator;
import hu.u_szeged.inf.fog.simulator.fl.FLEdgeDevice;
import hu.u_szeged.inf.fog.simulator.fl.FLOrchestrator;
import hu.u_szeged.inf.fog.simulator.fl.FLTelemetry;
import hu.u_szeged.inf.fog.simulator.fl.GlobalModelBroadcastEvent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.RandomDeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;

import java.util.*;

public class FLSimulationExample_S4 {

    /** Read a double system property with default */
    private static double dprop(String k, double def) {
        try { String v = System.getProperty(k); return (v==null)? def : Double.parseDouble(v); }
        catch (Exception ignore) { return def; }
    }
    /** Read an int system property with default */
    private static int iprop(String k, int def) {
        try { String v = System.getProperty(k); return (v==null)? def : Integer.parseInt(v); }
        catch (Exception ignore) { return def; }
    }
    /** Read a long system property with default */
    private static long lprop(String k, long def) {
        try { String v = System.getProperty(k); return (v==null)? def : Long.parseLong(v); }
        catch (Exception ignore) { return def; }
    }
    /** Read a boolean system property with default */
    private static boolean bprop(String k, boolean def) {
        try { String v = System.getProperty(k); return (v==null)? def : Boolean.parseBoolean(v); }
        catch (Exception ignore) { return def; }
    }
    /** Read a string system property with default */
    private static String sprop(String k, String def) {
        String v = System.getProperty(k);
        return (v==null || v.isEmpty()) ? def : v;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting FL simulation (S4: Privacy & Compression)...");

        // ---------- S4 DEFAULTS (overridable with -D flags) ----------
        final long   seed             = lprop("seed", 42L);
        final int    N                = iprop("N", 50);                // device count
        final int    MODEL            = iprop("model", 100_000);       // |w| doubles (~800 kB); override if needed
        final int    desiredRounds    = iprop("desiredRounds", 10);
        final long   RoundInterval    = lprop("roundInterval", 22_000L); // generous heartbeat per spec
        final double timeoutRatio     = dprop("timeoutRatio", 0.70);     // ρ_to
        final double minCR            = dprop("minCR", 0.80);            // ρ_min
        final boolean cooldown        = bprop("cooldown", true);         // false = fixed cadence; true = cooldown
        final boolean broadcastSel    = bprop("broadcastSelOnly", true); // broadcast to participants only
        final double sf               = dprop("sf", 1.0);                // sampling fraction
        final double dropout          = dprop("dropout", 0.0);           // keep 0.0 to isolate S4
        final double preUF            = dprop("preUF", 0.0);             // pre-upload fail
        final double inTransF         = dprop("inTransF", 0.0);          // in-transit fail

        // Privacy & compression knobs (S4):
        final boolean secureAgg       = bprop("secureAgg", false);
        final long    extraBytes      = lprop("extraBytes", 0L);         // 0, 256, 1024
        final double  dlCompFactor    = dprop("dl", 1.0);                // 1.0, 0.5, 0.2
        final double  ulCompFactor    = dprop("ul", 1.0);                // 1.0, 0.5, 0.2
        final double  dpServerSigma   = dprop("dpServerSigma", 0.02);    // server-side DP σ

        // Client DP (kept as in demo; doesn’t affect bytes)
        final double clientClipNorm   = dprop("clientClip", 0.10);
        final double clientDP_Sigma   = dprop("clientDPSigma", 0.02);

        // Optional: increase compute intensity (epochs)
        final double epochMult        = dprop("epoch", 1.0);

        // Energy fallback estimator (if native metering is too coarse)
        final boolean energyFallback  = bprop("energyFallback", false);

        // Export tag to disambiguate outputs per run
        final String exportTag        = sprop("export", String.format(
            "S4-sec%s_eb%d_dl%s_ul%s_sigma%s_N%d_model%d",
            String.valueOf(secureAgg), extraBytes,
            String.valueOf(dlCompFactor), String.valueOf(ulCompFactor),
            String.valueOf(dpServerSigma), N, MODEL));

        // ---------- Reproducibility ----------
        SimRandom.setSeed(seed);
        Random rng = SimRandom.get();

        // ---------- Model init ----------
        double[] initWeights = new double[Math.max(1, MODEL)];
        for (int i=0; i<initWeights.length; i++) initWeights[i] = rng.nextGaussian() * 0.01;

        // ---------- Aggregator (with IaaS for energy) ----------
        final String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";
        final GeoLocation aggLoc = new GeoLocation(47.4979, 19.0402); // Budapest
        FLAggregator.setTimeoutRatio(timeoutRatio);
        FLAggregator aggregator = new FLAggregator("FL-Aggregator-S4", cloudfile, aggLoc, 0, initWeights);

        // energy options
        aggregator.enableEnergyFallbackEstimator(energyFallback);
        aggregator.setEnergyCountFailedUploads(true);

        // Capture energy for aggregator
        new EnergyDataCollectorFL("aggregator", aggregator.iaas, true);

        // Export paths include tag so runs don’t overwrite each other
        aggregator.setExportPaths(
                ScenarioBase.resultDirectory + "/fl_telemetry_" + exportTag + ".csv",
                ScenarioBase.resultDirectory + "/fl_telemetry_" + exportTag + ".png");
        aggregator.setEnergyExportPaths(
                ScenarioBase.resultDirectory + "/fl_energy_" + exportTag + ".csv",
                ScenarioBase.resultDirectory + "/fl_energy_" + exportTag + ".png");

        // Finish callback: write energy once, then exit
        aggregator.setFinishedCallback(() -> {
            try {
                EnergyDataCollectorFL.writeToFile(ScenarioBase.resultDirectory);
            } catch (Throwable t) {
                System.out.println("EnergyDataCollectorFL write failed: " + t.getMessage());
            }
            // Plot the time-series energy.csv (cumulative kWh per collector). Complements
            // fl_energy.png (per-round deltas) with a wall-clock view of energy growth.
            FLTelemetry.plotEnergyTimeseries(
                    ScenarioBase.resultDirectory + java.io.File.separator + "energy.csv",
                    ScenarioBase.resultDirectory + java.io.File.separator + "energy.png",
                    "FL-Aggregator-S4");
            // HIGH-2 follow-up: release native meters and clear static state so repeated
            // runs in the same JVM (parameter sweeps, test suites) don't leak meters.
            EnergyDataCollectorFL.clearAll();
            System.out.println("FL finished – S4 run complete. Exiting.");
            System.out.flush();
            System.err.flush();
            System.exit(0);
        });

        // ---------- Devices (heterogeneous; Budapest region) ----------
        List<FLEdgeDevice> flDevices = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            try {
                long startTime = 0;
                long stopTime  = 1 * 60 * 60 * 1000;            // 1h horizon
                long fileSize  = 50 + rng.nextInt(151);         // 50–200 B (synthetic, used for compute)
                long freqMs    = 30_000 + rng.nextInt(90_001);  // 30–120 s (unused by FL path)
                GeoLocation location = new GeoLocation(47.0 + (i * 0.01), 19.0 + (i * 0.01));
                RandomWalkMobilityStrategy mobility = new RandomWalkMobilityStrategy(location, 0.0027, 0.0055, 10_000);
                RandomDeviceStrategy devStrategy = new RandomDeviceStrategy();

                EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                        PowerTransitionGenerator.generateTransitions(2.5, 10, 1.0, 3, 3);
                Map<String, PowerState> stT  = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
                Map<String, PowerState> nwT  = transitions.get(PowerTransitionGenerator.PowerStateKind.network);
                Map<String, PowerState> cpuT = transitions.get(PowerTransitionGenerator.PowerStateKind.host);

                Repository repo = new Repository(
                        4_294_967_296L, "dummy-repo-" + i,
                        3250, 3250, 3250, new HashMap<>(), stT, nwT);

                int cores = 1 + rng.nextInt(4);                   // 1–4
                double mipsPerPE = 0.001 + rng.nextDouble()*0.004;// 0.001–0.005 instr/tick/core
                PhysicalMachine pm = new PhysicalMachine(cores, mipsPerPE, 2_147_483_648L, repo, 0, 0, cpuT);

                long bandwidth = 50 + rng.nextInt(151); // 50–200 B/tick
                int  latency   = 30 + rng.nextInt(71);  // 30–100 ticks

                double instrPerByte = 0.05 + rng.nextDouble()*0.10; // 0.05–0.15
                double throughput   = cores * mipsPerPE;            // instr/tick

                FLEdgeDevice dev = new FLEdgeDevice(
                        startTime, stopTime, fileSize, freqMs,
                        mobility, devStrategy, pm,
                        instrPerByte, latency, bandwidth, throughput,
                        clientClipNorm, clientDP_Sigma, true);
                flDevices.add(dev);
                new EnergyDataCollectorFL("device-" + i, pm, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // ---------- Configure compute intensity (epochs) ----------
        GlobalModelBroadcastEvent.setEpochMultiplier(epochMult);

        // ---------- Orchestrate S4 run ----------
        final boolean fixedCadence = !cooldown; // true=start-to-start, false=cool-down
        final boolean useFixedKSampling = false;
        final int fixedK = 0;

        // Informative header
        System.out.println(
            "\n--- S4 CONFIG ---\n" +
            "N=" + N + ", model=" + MODEL + ", rounds=" + desiredRounds + ", roundInterval=" + RoundInterval + "\n" +
            "secureAgg=" + secureAgg + ", extraBytes=" + extraBytes + ", dl=" + dlCompFactor + ", ul=" + ulCompFactor + "\n" +
            "dpServerSigma=" + dpServerSigma + ", epochMultiplier=" + epochMult + "\n" +
            "sf=" + sf + ", dropout=" + dropout + ", preUF=" + preUF + ", inTransF=" + inTransF + "\n" +
            "minCR=" + minCR + ", timeoutRatio=" + timeoutRatio + ", pacing=" + (fixedCadence ? "fixed" : "cooldown") + "\n" +
            "exportTag=" + exportTag + "\n------------------\n"
        );

        new FLOrchestrator(
                RoundInterval,
                desiredRounds,
                flDevices,
                aggregator,
                sf,
                dropout,
                preUF,
                inTransF,
                minCR,
                secureAgg,
                extraBytes,
                dlCompFactor,
                ulCompFactor,
                dpServerSigma,
                fixedCadence,
                broadcastSel,
                useFixedKSampling,
                fixedK
        );

        // ---------- Simulate ----------
        Timed.simulateUntilLastEvent();
        System.out.println("FL simulation (S4) finished.");
    }
}
