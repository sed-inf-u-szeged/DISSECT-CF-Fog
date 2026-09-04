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

public class FLSimulationExample_S3 {

    /* ===========================
     * System properties (all optional)
     *   -Ds3.seed=42
     *   -Ds3.N=30
     *   -Ds3.modelSize=3
     *   -Ds3.rounds=10
     *   -Ds3.roundInterval=22000
     *   -Ds3.fixedCadence=true|false
     *   -Ds3.rmin=0.80
     *   -Ds3.rto=0.70
     *   -Ds3.sf=1.0
     *   -Ds3.drop=0.5
     *   -Ds3.preFail=0.10
     *   -Ds3.inFail=0.05
     *   -Ds3.broadcastSelectedOnly=true
     *   -Ds3.secureAgg=true
     *   -Ds3.secBytes=256
     *   -Ds3.dlComp=0.5
     *   -Ds3.ulComp=0.5
     *   -Ds3.serverSigma=0.01
     *   -Ds3.clientClip=0.10
     *   -Ds3.clientSigma=0.02
     *   -Ds3.useFixedK=false
     *   -Ds3.fixedK=3
     *   -Ds3.epoch=1.0
     *   -Ds3.countFailedULEnergy=true
     *   -Ds3.useEnergyEstimator=false
     * =========================== */

    private static int geti(String k, int def) {
        String v = System.getProperty(k);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (Exception ignore) { return def; }
    }
    private static long getl(String k, long def) {
        String v = System.getProperty(k);
        if (v == null) return def;
        try { return Long.parseLong(v.trim()); } catch (Exception ignore) { return def; }
    }
    private static double getd(String k, double def) {
        String v = System.getProperty(k);
        if (v == null) return def;
        try { return Double.parseDouble(v.trim()); } catch (Exception ignore) { return def; }
    }
    private static boolean getb(String k, boolean def) {
        String v = System.getProperty(k);
        if (v == null) return def;
        return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("1") || v.equalsIgnoreCase("yes");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting FL simulation (S3 pacing evaluation)...");
        // ---- Read S3 params
        final long   seed           = getl("s3.seed", 42L);
        final int    N              = geti("s3.N", 10);
        final int    MODEL          = geti("s3.modelSize", 3);
        final int    desiredRounds  = geti("s3.rounds", 10);
        final long   RoundInterval  = getl("s3.roundInterval", 22_000L);
        final boolean fixedCadence  = getb("s3.fixedCadence", false);     // toggle pacing
        final double rmin           = getd("s3.rmin", 0.80);             // early aggregation threshold
        final double rto            = getd("s3.rto", 0.70);              // timeout ratio
        final double sf             = getd("s3.sf", 1.0);
        final double drop           = getd("s3.drop", 0.5);
        final double preFail        = getd("s3.preFail", 0.10);
        final double inFail         = getd("s3.inFail", 0.05);
        final boolean broadcastSelectedOnly = getb("s3.broadcastSelectedOnly", true);
        final boolean secureAgg     = getb("s3.secureAgg", true);
        final long    secBytes      = getl("s3.secBytes", 256L);
        final double  dlComp        = getd("s3.dlComp", 0.5);
        final double  ulComp        = getd("s3.ulComp", 0.5);
        final double  serverSigma   = getd("s3.serverSigma", 0.01);
        final double  clientClip    = getd("s3.clientClip", 0.10);
        final double  clientSigma   = getd("s3.clientSigma", 0.02);
        final boolean useFixedK     = getb("s3.useFixedK", false);
        final int     fixedK        = geti("s3.fixedK", 3);
        final double  epoch         = getd("s3.epoch", 1.0);
        final boolean countFailedULEnergy = getb("s3.countFailedULEnergy", true);
        final boolean useEnergyEstimator  = getb("s3.useEnergyEstimator", false);

        // Determinism
        SimRandom.setSeed(seed);
        Random rng = SimRandom.get();

        // Configure pacing-related global
        FLAggregator.setTimeoutRatio(rto);
        GlobalModelBroadcastEvent.setEpochMultiplier(epoch);

        // Init random tiny model
        double[] initWeights = new double[MODEL];
        for (int i = 0; i < MODEL; i++) initWeights[i] = rng.nextGaussian() * 0.01;

        // Aggregator (with IaaS for energy metering)
        final String cloudfile = ScenarioBase.resourcePath + "LPDS_original.xml";
        final GeoLocation budapest = new GeoLocation(47.4979, 19.0402);

        FLAggregator aggregator = new FLAggregator("FL-Aggregator-S3", cloudfile, budapest, 0, initWeights);
        aggregator.enableEnergyFallbackEstimator(useEnergyEstimator);
        aggregator.setEnergyCountFailedUploads(countFailedULEnergy);

        // Energy metering
        new EnergyDataCollectorFL("aggregator", aggregator.iaas, true);

        // Export filenames tagged by pacing mode/seed/rmin/rto
        String mode = fixedCadence ? "fixed" : "cooldown";
        String tag  = String.format(Locale.US, "S3_%s_seed%d_rmin%.2f_rto%.2f", mode, seed, rmin, rto);
        aggregator.setExportPaths(
                ScenarioBase.resultDirectory + "/fl_telemetry_" + tag + ".csv",
                ScenarioBase.resultDirectory + "/fl_telemetry_" + tag + ".png"
        );
        aggregator.setEnergyExportPaths(
                ScenarioBase.resultDirectory + "/fl_energy_" + tag + ".csv",
                ScenarioBase.resultDirectory + "/fl_energy_" + tag + ".png"
        );

        // On finish, write energy CSV/PNGs then exit (single-run executable)
        aggregator.setFinishedCallback(() -> {
            try {
                EnergyDataCollectorFL.writeToFile(ScenarioBase.resultDirectory);
            } catch (Throwable t) {
                System.out.println("EnergyDataCollectorFL: failed to write results: " + t.getMessage());
            }
            // Plot the time-series energy.csv (cumulative kWh per collector). Complements
            // fl_energy.png (per-round deltas) with a wall-clock view of energy growth.
            FLTelemetry.plotEnergyTimeseries(
                    ScenarioBase.resultDirectory + java.io.File.separator + "energy.csv",
                    ScenarioBase.resultDirectory + java.io.File.separator + "energy.png",
                    "FL-Aggregator-S3");
            // HIGH-2 follow-up: release native meters and clear static state so repeated
            // runs in the same JVM (parameter sweeps, test suites) don't leak meters.
            EnergyDataCollectorFL.clearAll();
            System.out.println("FL finished – end-of-run callback. Exiting.");
            System.out.flush();
            System.err.flush();
            System.exit(0);
        });

        // Build N heterogeneous devices
        List<FLEdgeDevice> devices = new ArrayList<>(N);

        // Device resource models for energy
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> tr =
                PowerTransitionGenerator.generateTransitions(2.5, 10, 1.0, 3, 3);
        Map<String, PowerState> diskStates = tr.get(PowerTransitionGenerator.PowerStateKind.storage);
        Map<String, PowerState> netStates  = tr.get(PowerTransitionGenerator.PowerStateKind.network);
        Map<String, PowerState> cpuStates  = tr.get(PowerTransitionGenerator.PowerStateKind.host);

        for (int i = 0; i < N; i++) {
            try {
                long startTime = 0L;
                long stopTime  = 1L * 60 * 60 * 1000; // 1 hour

                // Synthetic sensing
                long fileSize = 50 + rng.nextInt(151);      // 50–200 B
                long freq     = 30_000 + rng.nextInt(90_001); // 30–120 s

                // Budapest-centered random location inside 0.5° radius
                double Rdeg = 0.5;
                double rUni = Math.sqrt(rng.nextDouble()) * Rdeg; // uniform in disk
                double theta = 2 * Math.PI * rng.nextDouble();
                double lat = 47.4979 + rUni * Math.cos(theta);
                double lon = 19.0402 + rUni * Math.sin(theta);

                GeoLocation loc = new GeoLocation(lat, lon);
                RandomWalkMobilityStrategy mobility = new RandomWalkMobilityStrategy(loc, 0.0027, 0.0055, 10_000);
                RandomDeviceStrategy devStrategy = new RandomDeviceStrategy();

                // Repo for device + latency map
                HashMap<String, Integer> latMap = new HashMap<>();
                Repository repo = new Repository(
                        4_294_967_296L,
                        "dev-repo-" + i,
                        3250, 3250, 3250,
                        latMap,
                        diskStates,
                        netStates
                );

                // Compute heterogeneity
                int cores = 1 + rng.nextInt(4); // 1–4
                double mipsPerPE = 0.001 + rng.nextDouble() * 0.004; // 0.001–0.005 instr/tick/core
                PhysicalMachine pm = new PhysicalMachine(cores, mipsPerPE, 2_147_483_648L, repo, 0, 0, cpuStates);

                // Network heterogeneity
                long bandwidth = 50 + rng.nextInt(151); // 50–200 B/tick
                int latency    = 30 + rng.nextInt(71);  // 30–100 ticks

                // Local training cost
                double instrPerByte = 0.05 + rng.nextDouble() * 0.10; // 0.05–0.15
                double throughput   = cores * mipsPerPE;

                FLEdgeDevice dev = new FLEdgeDevice(
                        startTime, stopTime,
                        fileSize, freq,
                        mobility, devStrategy,
                        pm,
                        instrPerByte,
                        latency,
                        bandwidth,
                        throughput,
                        clientClip,
                        clientSigma,
                        true
                );
                devices.add(dev);
                new EnergyDataCollectorFL("device-" + i, pm, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // (Optional) print a concise scenario header
        System.out.println(String.format(Locale.US,
                "S3 Scenario | N=%d, rounds=%d, roundInterval=%d, fixedCadence=%s, rmin=%.2f, rto=%.2f, sf=%.2f, drop=%.2f",
                N, desiredRounds, RoundInterval, String.valueOf(fixedCadence), rmin, rto, sf, drop));

        // Schedule first round via orchestrator
        new FLOrchestrator(
                RoundInterval,
                desiredRounds,
                devices,
                aggregator,
                sf,
                drop,
                preFail,
                inFail,
                rmin,
                secureAgg,
                secBytes,
                dlComp,
                ulComp,
                serverSigma,
                fixedCadence,
                broadcastSelectedOnly,
                useFixedK,
                fixedK
        );

        // Run
        Timed.simulateUntilLastEvent();
        System.out.println("FL simulation finished (S3).");
    }
}
