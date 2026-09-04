package hu.u_szeged.inf.fog.simulator.fl.demos;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.fl.cosim.TidRecorder;
import hu.u_szeged.inf.fog.simulator.fl.gossip.FLGossipNode;
import hu.u_szeged.inf.fog.simulator.fl.gossip.FLGossipOrchestrator;
import hu.u_szeged.inf.fog.simulator.fl.gossip.GossipPlots;
import hu.u_szeged.inf.fog.simulator.fl.run.GossipDeviceFactory;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import hu.u_szeged.inf.fog.simulator.fl.topology.TopologyFactory;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * Decentralized gossip FL — beginner demo (Java execution).
 *
 * <p>The simplest way to see the new gossip extension run end to end. It builds
 * a small ring topology, attaches real DISSECT-CF physical machines/repositories
 * to each node, and runs the {@link FLGossipOrchestrator} for a handful of
 * synchronous gossip rounds in <b>SYNTHETIC</b> learning mode (no PyTorch / no
 * co-simulation bridge needed). It prints the per-round telemetry and the
 * Topology-Induced Divergence (TID) series, <b>saves the CSV artefacts</b>, and
 * (if Python + matplotlib are available) renders <b>PNG figures</b> for energy,
 * TID, and traffic/idle.</p>
 *
 * <p><b>How to run in Eclipse:</b> right-click this file →
 * <i>Run As → Java Application</i>. No program arguments, no config files. The
 * simulation itself needs only Java; PNG plotting is optional and degrades
 * gracefully (the CSVs are always written). A successful run ends with
 * "{@code [gossip-demo] DONE}".</p>
 *
 * <p>Output goes to {@code <project>/gossip_demo_output/}. To drive the same
 * machinery with <i>real</i> PyTorch training, use the co-simulation pipeline
 * ({@code FLScenarioRunner}) — see {@code docs/GOSSIP_FL_GUIDE.md}.</p>
 */
public final class FLGossipDemo {

    private FLGossipDemo() {
    }

    /** Runs the demo; takes no arguments (edit the constants below instead). */
    public static void main(String[] args) {
        final int n = 6;            // ring of 6 nodes
        final int rounds = 8;       // gossip rounds
        final int d = 32;           // model-signature dimension
        final long seed = 42L;

        // 1) Reproducible RNG.
        SimRandom.setSeed(seed);

        // 2) Communication graph: a ring (every node has exactly two neighbours).
        FLTopology topology = TopologyFactory.ring(n);

        System.out.println("==================================================================");
        System.out.println(" Decentralized Gossip FL — demo");
        System.out.println("==================================================================");
        System.out.println(" nodes         = " + n + "  (ring topology)");
        System.out.println(" rounds        = " + rounds);
        System.out.println(" selector      = COMPOSITE (explore-then-exploit)");
        System.out.println(" merge rule    = DRIFT_SUPPRESSED");
        System.out.println(" lambda2 (λ₂)  = " + String.format(Locale.US, "%.6f", topology.lambda2()));
        System.out.println(" topology hash = " + topology.topologyHash().substring(0, 16) + "…");
        System.out.println("------------------------------------------------------------------");

        // 3) Per-node initial model signatures (deterministic, slightly different
        //    per node so the divergence-driven selection has something to work with).
        float[][] initialSignatures = new float[n][d];
        int[] sampleCounts = new int[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                initialSignatures[i][j] = 0.01f * (i + 1) * (j + 1);
            }
            sampleCounts[i] = 10 + i;   // heterogeneous local dataset sizes
        }

        // 4) Build the gossip nodes (each gets a real PM + repository + energy meter).
        List<FLGossipNode> nodes = GossipDeviceFactory.build(topology, initialSignatures, sampleCounts);

        // 5) The orchestrator drives Algorithm 1 each round (SYNTHETIC mode).
        //    The Builder's defaults are the paper's §9 choices (COMPOSITE selector
        //    with explore-then-exploit, drift-suppressed merge, k = 2); override
        //    any of them fluently, e.g. .k(1), .mergeRule(new UniformAvg()),
        //    .policy(new RandomK()).
        FLGossipOrchestrator orchestrator = new FLGossipOrchestrator.Builder(topology, nodes)
                .rounds(rounds)
                .paramCount(1000L)   // small synthetic model ⇒ 4 KB payload per exchange
                .seed(seed)
                .build();
        orchestrator.setScenarioId("gossip-demo");
        TidRecorder tid = new TidRecorder(topology);
        orchestrator.setTidRecorder(tid);

        final boolean[] finished = { false };
        orchestrator.setFinishedCallback(() -> finished[0] = true);

        // 6) Run the discrete-event simulation. We use a bounded horizon (the
        //    per-node energy meters are perpetual Timed subscribers, so
        //    simulateUntilLastEvent would never return). 59_000 ticks comfortably
        //    covers 8 short rounds.
        Timed.simulateUntil(59_000L);

        // 7) Report to the console.
        System.out.println("\n--- Per-round telemetry (round, node, peers, bytes, idle/busy ticks) ---");
        System.out.print(orchestrator.telemetryCsv());

        System.out.println("\n--- Topology-Induced Divergence (TID), per round ---");
        System.out.print(tid.toCsv());

        System.out.println("\n--- Per-round node energy (round, node, ulBytes, dlBytes, energy mJ) ---");
        for (double[] r : orchestrator.perRoundEnergyRows()) {
            System.out.printf(Locale.US, "%d,%d,%d,%d,%.3f%n",
                    (long) r[0], (long) r[1], (long) r[2], (long) r[3], r[4]);
        }

        // 8) Save the CSV artefacts and (optionally) render PNG figures.
        Path outDir = Paths.get(System.getProperty("user.dir"), "gossip_demo_output");
        try {
            orchestrator.exportAll(outDir); // full CSV bundle (incl. energy_per_round.csv)
            System.out.println("\n--- Artefacts (CSV) written to: " + outDir.toAbsolutePath() + " ---");
            System.out.println("  tid_timeseries.csv · gossip_telemetry.csv · per_exchange.csv");
            System.out.println("  idle_time.csv · gossip_round.csv · energy_per_round.csv · run_metadata.json");

            // PNG figures (graceful: if Python/matplotlib is missing, the CSVs remain).
            System.out.println("\n--- Figures (PNG; needs Python + matplotlib) ---");
            GossipPlots.plotTid(outDir.resolve("tid_timeseries.csv"), outDir.resolve("gossip_tid.png"));
            GossipPlots.plotEnergy(outDir.resolve("energy_per_round.csv"), outDir.resolve("gossip_energy.png"));
            GossipPlots.plotTrafficIdle(outDir.resolve("gossip_telemetry.csv"),
                    outDir.resolve("gossip_traffic_idle.png"));
            System.out.println("  (no accuracy_vs_round.png here: SYNTHETIC mode has no learner —");
            System.out.println("   run FLCoSimDemo for the real-training accuracy figure)");
        } catch (Exception e) {
            System.out.println("[gossip-demo] artefact/figure export skipped: " + e.getMessage());
        }

        System.out.println("\n------------------------------------------------------------------");
        System.out.println(" finished cleanly : " + finished[0]);
        System.out.println(" [gossip-demo] DONE");
        System.out.println("==================================================================");

        // Tidy up the static energy registry so repeated runs in one JVM are clean.
        EnergyDataCollectorFL.clearAll();
    }

}
