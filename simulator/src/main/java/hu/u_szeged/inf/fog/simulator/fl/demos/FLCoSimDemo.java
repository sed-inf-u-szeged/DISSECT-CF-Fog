package hu.u_szeged.inf.fog.simulator.fl.demos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import hu.u_szeged.inf.fog.simulator.fl.run.FLScenarioRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <h2>Co-simulation bridge — one-click demo (§8.4 Track A, real PyTorch training)</h2>
 *
 * <p>Runs the complete three-pass protocol from a single Java entry point, with
 * no arguments and no configuration files:</p>
 * <ol>
 *   <li><b>Pass 1</b> (Java) — builds the §9 two-cluster base graph (2×3 nodes,
 *       2 bridges) and exports {@code system_trace.json};</li>
 *   <li><b>Pass 2</b> (PyTorch, launched as a subprocess) — <i>really trains</i>
 *       LeNet-5 on Dirichlet-partitioned synthetic data in gossip mode,
 *       recording accuracy, peer sets, merge weights, and model signatures;</li>
 *   <li><b>Pass 3</b> (Java) — replays the trace on the discrete-event timeline
 *       with full physics (timed exchanges, both-endpoint energy, idle time,
 *       TID) and reports the Java↔Python {@code selection_agreement_rate}.</li>
 * </ol>
 *
 * <p><b>How to run in Eclipse:</b> right-click this file →
 * <i>Run As → Java Application</i>. The only prerequisite beyond Java is a
 * Python 3 with PyTorch on the PATH (see {@code docs/GOSSIP_FL_GUIDE.md} §8);
 * the demo detects {@code py -3.12} / {@code python3} / {@code python} and, if
 * none can import torch, prints setup instructions and exits cleanly instead of
 * failing. A successful run ends with "{@code [cosim-demo] DONE}".</p>
 *
 * <p>Output goes to {@code <cwd>/cosim_demo_output/} — the released artefact
 * tree ({@code pass1/}, {@code pass2/}, {@code pass3/}) plus three PNG figures.
 * For the synthetic (no-Python) gossip demo, see {@link FLGossipDemo}; for
 * campaigns, {@code fl/run/FLScenarioRunner}.</p>
 */
public final class FLCoSimDemo {

    /**
     * Pass-2 sizing: LeNet-5 (4 classes), 480 synthetic samples, 12 rounds of
     * 2 local epochs — big enough that the accuracy curve visibly rises
     * (nodes start from different inits, so early rounds go to aligning
     * models; by the end the averaged model w̄ typically beats every
     * individual node — the §8.3 consensus story), yet still ~1 minute.
     */
    private static final int NUM_CLASSES = 4;
    private static final int IN_CHANNELS = 1;
    private static final int SAMPLES = 480;
    private static final int ROUNDS = 12;
    private static final int LOCAL_EPOCHS = 2;
    private static final long SEED = 42L;

    private FLCoSimDemo() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================================");
        System.out.println(" Co-simulation bridge — one-click demo (Track A, real training)");
        System.out.println("==================================================================");
        System.out.println(" Pass 1 (Java)    : export scenario  → system_trace.json");
        System.out.println(" Pass 2 (PyTorch) : real gossip training → learning_trace.csv + signatures");
        System.out.println(" Pass 3 (Java)    : replay with physics  → energy/idle/TID + agreement rate");
        System.out.println("------------------------------------------------------------------");

        // 0) Find a Python that can import torch (py -3.12 → python3 → python).
        List<String> launcher = detectPython();
        if (launcher == null) {
            System.out.println(" No Python with PyTorch found — Pass 2 needs it. One-time setup:");
            System.out.println("   py -3.12 -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu");
            System.out.println("   py -3.12 -m pip install numpy scipy matplotlib pandas pytest");
            System.out.println(" Then re-run this demo. (The pure-Java gossip demo needs no Python:");
            System.out.println(" see FLGossipDemo.)");
            System.out.println(" [cosim-demo] SKIPPED — Python/PyTorch not available");
            return;
        }
        System.out.println(" python           = " + String.join(" ", launcher));

        // 0b) Locate the harness (walk up from the working directory).
        Path harness = findHarness();
        if (harness == null) {
            System.out.println(" [cosim-demo] SKIPPED — could not locate harness/run.py above "
                    + Paths.get("").toAbsolutePath());
            return;
        }
        System.out.println(" harness          = " + harness);

        // 1–3) One S1-shaped decentralized cell through the campaign runner
        // (the §9 base graph: two clusters of three, two bridges).
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("scenario", "cosim_demo");
        spec.put("mode", "decentralized");
        spec.put("topology", "clustered");
        spec.put("n", 6);
        spec.put("policy", "COMPOSITE");
        spec.put("merge", "DRIFT_SUPPRESSED");
        spec.put("gamma", "EXPLORE_THEN_EXPLOIT");
        spec.put("k", 2);
        spec.put("rounds", ROUNDS);
        spec.put("local_epochs", LOCAL_EPOCHS);
        spec.put("lr", 0.08);   // demo-sized: visible learning within 12 short rounds
        spec.put("dirichlet_rho", 0.5);
        spec.put("signature_dim", 64);
        spec.put("model", "lenet5");
        // Workload now travels in the scenario spec (the runner no longer takes
        // dataset flags), so the demo declares the same hermetic synthetic set.
        spec.put("dataset", "synthetic");
        spec.put("samples", SAMPLES);
        spec.put("num_classes", NUM_CLASSES);
        spec.put("in_channels", IN_CHANNELS);
        spec.put("modes", Arrays.asList("decentralized"));
        spec.put("seeds", Arrays.asList(SEED));

        boolean pyLauncher = "py".equals(launcher.get(0));
        FLScenarioRunner runner = new FLScenarioRunner(spec, launcher.get(0), pyLauncher, harness);
        Path outRoot = Paths.get("cosim_demo_output");
        List<FLScenarioRunner.Run> runs = runner.expandRuns(outRoot, "demo");
        FLScenarioRunner.Run run = runs.get(0);
        System.out.println(" output           = " + run.dir.toAbsolutePath());
        System.out.println("------------------------------------------------------------------");
        runner.runCell(run);

        // 4) Read the artefacts back and explain them.
        System.out.println("\n------------------------------------------------------------------");
        System.out.println(" RESULTS — how to read them");
        System.out.println("------------------------------------------------------------------");
        summarize(run.dir);

        System.out.println("\n finished cleanly : true");
        System.out.println(" [cosim-demo] DONE");
        System.out.println("==================================================================");
    }

    // ------------------------------------------------------------------
    // Result summary (reads the released artefact tree back).
    // ------------------------------------------------------------------
    private static void summarize(Path cellDir) throws IOException {
        ObjectMapper om = new ObjectMapper();

        // Cross-language determinism: the §8.4 "agree by construction" claim.
        Path meta = cellDir.resolve("pass3/run_metadata.json");
        if (Files.exists(meta)) {
            JsonNode m = om.readTree(meta.toFile());
            System.out.printf(Locale.US,
                    " selection_agreement_rate = %.4f   (1.0 = Java re-derived exactly the%n"
                    + "                                    peer choices PyTorch made — §8.4)%n",
                    m.path("selection_agreement_rate").asDouble());
            System.out.printf(Locale.US,
                    " lambda2                  = %.4f   (algebraic connectivity of the 2×3 base graph)%n",
                    m.path("lambda2").asDouble());
        }

        // Real learning: accuracy from the Pass-2 trace (mean over nodes).
        double[] firstLast = accFirstLast(cellDir.resolve("pass2/learning_trace.csv"));
        if (firstLast != null) {
            System.out.printf(Locale.US,
                    " accuracy (mean/node)     = %.3f → %.3f over %d rounds  (REAL training, not synthetic)%n",
                    firstLast[0], firstLast[1], ROUNDS);
        }

        // §8.3 summary: A_dec + the w̄ secondary view.
        Path summary = cellDir.resolve("pass2/summary.json");
        if (Files.exists(summary)) {
            JsonNode s = om.readTree(summary.toFile());
            System.out.printf(Locale.US,
                    " final A_dec              = %.3f mean / %.3f worst node; averaged model w̄ = %.3f%n",
                    s.path("acc_mean_over_nodes").asDouble(),
                    s.path("acc_worst_node").asDouble(),
                    s.path("acc_mean_model").asDouble());
            System.out.println("                            (train a centralized cell with run.py --mode centralized,");
            System.out.println("                             then analysis/tid_gap.py gives Eq. tid-gap)");
        }

        // TID mixing (falling = the topology is spreading knowledge).
        double[] tid = tidFirstLast(cellDir.resolve("pass3/tid_timeseries.csv"));
        if (tid != null) {
            System.out.printf(Locale.US,
                    " TID global               = %.3f → %.3f   (falling = models mixing through gossip)%n",
                    tid[0], tid[1]);
        }

        // Both-endpoint energy from the Pass-3 replay (same meters as FLGossipDemo).
        double energyMj = totalEnergyMj(cellDir.resolve("pass3/energy_per_round.csv"));
        if (energyMj > 0) {
            System.out.printf(Locale.US,
                    " total node energy        = %.1f J over %d rounds  (both-endpoint metering, Pass 3)%n",
                    energyMj / 1000.0, ROUNDS);
        }

        System.out.println("\n Files: pass1/system_trace.json · pass2/learning_trace.csv + signatures.bin");
        System.out.println("        + summary.json · pass3/*.csv (incl. energy_per_round.csv) + run_metadata.json");
        System.out.println(" Figures: accuracy_vs_round.png · gossip_tid.png · gossip_traffic_idle.png · gossip_energy.png");
    }

    /** Sum of the non-NaN per-round-per-node energy samples (mJ). */
    private static double totalEnergyMj(Path energyCsv) throws IOException {
        if (!Files.exists(energyCsv)) {
            return 0;
        }
        double sum = 0;
        List<String> lines = Files.readAllLines(energyCsv, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split(",", -1);
            double v = Double.parseDouble(c[4]);
            if (!Double.isNaN(v)) {
                sum += v;
            }
        }
        return sum;
    }

    /** Mean accuracy over nodes at the first and last round of the trace CSV. */
    private static double[] accFirstLast(Path traceCsv) throws IOException {
        if (!Files.exists(traceCsv)) {
            return null;
        }
        List<String> lines = Files.readAllLines(traceCsv, StandardCharsets.UTF_8);
        double sumFirst = 0;
        double sumLast = 0;
        int nFirst = 0;
        int nLast = 0;
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split(",", -1); // round=idx 8, acc=idx 9
            int round = Integer.parseInt(c[8]);
            double acc = Double.parseDouble(c[9]);
            if (round == 0) {
                sumFirst += acc;
                nFirst++;
            }
            if (round == ROUNDS - 1) {
                sumLast += acc;
                nLast++;
            }
        }
        return (nFirst == 0 || nLast == 0) ? null
                : new double[] { sumFirst / nFirst, sumLast / nLast };
    }

    /** First and last global-scope TID values. */
    private static double[] tidFirstLast(Path tidCsv) throws IOException {
        if (!Files.exists(tidCsv)) {
            return null;
        }
        Double first = null;
        Double last = null;
        for (String line : Files.readAllLines(tidCsv, StandardCharsets.UTF_8)) {
            String[] c = line.split(",", -1);
            if (c.length >= 3 && "global".equals(c[1])) {
                double v = Double.parseDouble(c[2]);
                if (first == null) {
                    first = v;
                }
                last = v;
            }
        }
        return first == null ? null : new double[] { first, last };
    }

    // ------------------------------------------------------------------
    // Environment detection (graceful, never throws).
    // ------------------------------------------------------------------

    /** First launcher that can {@code import torch}, or null. */
    private static List<String> detectPython() {
        List<List<String>> candidates = Arrays.asList(
                Arrays.asList("py", "-3.12"),
                Arrays.asList("python3"),
                Arrays.asList("python"));
        for (List<String> c : candidates) {
            List<String> cmd = new ArrayList<>(c);
            cmd.add("-c");
            cmd.add("import torch, numpy");
            try {
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                try (InputStream in = p.getInputStream()) {
                    byte[] buf = new byte[4096];
                    while (in.read(buf) != -1) {
                        // drain
                    }
                }
                if (p.waitFor(90, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return c;
                }
            } catch (IOException e) {
                // launcher not present — try the next one
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** Walks up from the CWD looking for {@code harness/run.py}. */
    private static Path findHarness() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int up = 0; up < 5 && dir != null; up++, dir = dir.getParent()) {
            Path candidate = dir.resolve("harness");
            if (Files.exists(candidate.resolve("run.py"))) {
                return candidate;
            }
        }
        return null;
    }
}
