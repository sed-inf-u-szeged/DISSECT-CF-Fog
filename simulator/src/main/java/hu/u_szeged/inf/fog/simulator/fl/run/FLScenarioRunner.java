package hu.u_szeged.inf.fog.simulator.fl.run;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.u_szeged.inf.fog.simulator.fl.cosim.ComputeDelayModel;
import hu.u_szeged.inf.fog.simulator.fl.cosim.LearningOutcome;
import hu.u_szeged.inf.fog.simulator.fl.cosim.Pass1Main;
import hu.u_szeged.inf.fog.simulator.fl.cosim.SystemTraceWriter;
import hu.u_szeged.inf.fog.simulator.fl.cosim.TidRecorder;
import hu.u_szeged.inf.fog.simulator.fl.cosim.TraceProvider;
import hu.u_szeged.inf.fog.simulator.fl.cosim.TraceReader;
import hu.u_szeged.inf.fog.simulator.fl.gossip.FLGossipNode;
import hu.u_szeged.inf.fog.simulator.fl.gossip.FLGossipOrchestrator;
import hu.u_szeged.inf.fog.simulator.fl.gossip.GossipPlots;
import hu.u_szeged.inf.fog.simulator.fl.merge.DriftSuppressed;
import hu.u_szeged.inf.fog.simulator.fl.merge.FixedUniform;
import hu.u_szeged.inf.fog.simulator.fl.merge.MergeRule;
import hu.u_szeged.inf.fog.simulator.fl.merge.MetropolisHastings;
import hu.u_szeged.inf.fog.simulator.fl.merge.SampleWeighted;
import hu.u_szeged.inf.fog.simulator.fl.merge.UniformAvg;
import hu.u_szeged.inf.fog.simulator.fl.selection.AllNeighbors;
import hu.u_szeged.inf.fog.simulator.fl.selection.CompositeScore;
import hu.u_szeged.inf.fog.simulator.fl.selection.DegreeBased;
import hu.u_szeged.inf.fog.simulator.fl.selection.GammaSchedule;
import hu.u_szeged.inf.fog.simulator.fl.selection.PeerSelectionPolicy;
import hu.u_szeged.inf.fog.simulator.fl.selection.RandomK;
import hu.u_szeged.inf.fog.simulator.fl.selection.SingleFactorBandwidth;
import hu.u_szeged.inf.fog.simulator.fl.selection.SingleFactorDivergence;
import hu.u_szeged.inf.fog.simulator.fl.selection.SingleFactorLatency;
import hu.u_szeged.inf.fog.simulator.fl.selection.SingleFactorLoad;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/**
 * YAML-driven campaign runner (§9: cell V plus S1--S6). Expands a scenario file
 * into {@code cells × seeds}, then per cell runs Pass 1 (Java) → Pass 2 (the
 * Python harness) → Pass 3 (Java TRACE replay), writing the released artefact
 * tree {@code results/<campaign>/<scenario>/<cellId>/seed_<s>/{pass1,pass2,pass3}}.
 *
 * <ul>
 *   <li><b>Never invents cells</b>: expansion is the cross-product of the
 *       declared list-valued factors within each {@code group} (the §9 S2
 *       pruning rules are encoded as explicit groups in {@code S2.yaml}).</li>
 *   <li><b>CRN</b>: cells compared at the same {@code seed} share the seed and
 *       therefore the derived per-decision streams (partitions, dynamic
 *       schedule, load profiles, failures) — common random numbers by
 *       construction.</li>
 *   <li><b>Resume-on-crash</b>: a cell whose {@code pass3/run_metadata.json}
 *       already exists is skipped.</li>
 *   <li><b>{@code --dry-run}</b>: prints the exact {@code cell × seed} expansion
 *       before any compute is spent.</li>
 * </ul>
 *
 * <p>Pass 2 is wired for all three coordination modes: the decentralized
 * (gossip) scenarios and the centralized/hierarchical baseline legs, which
 * reuse the same harness through its {@code --mode} dispatch.</p>
 */
public final class FLScenarioRunner {

    private static final List<String> LIST_FACTORS =
            List.of("topology", "n", "policy", "merge", "gamma", "k", "ws_k_ring", "ws_beta");

    /** Coordination legs a scenario may request, in execution order. */
    private static final List<String> ALL_MODES = List.of("decentralized", "centralized", "hierarchical");

    private final Map<String, Object> spec;
    private final String python;
    private final boolean pyLauncher;
    private final Path harnessDir;
    private final double lr;
    private final int batch;
    // Workload config — read from the scenario file, not from CLI flags, so the
    // grid the runner executes is exactly the grid the scenario declares.
    private final String dataset;
    private final int samples;
    private final int evalSamples;
    private final int evalEvery;
    private final int numClasses;
    private final int inChannels;
    private final int globalEvery;
    private final List<String> modes;
    private final Path calibration;

    /**
     * Creates a runner for one scenario spec.
     *
     * @param spec the parsed scenario YAML.
     * @param python the Python executable (or launcher) for Pass 2.
     * @param pyLauncher whether {@code python} is the Windows {@code py} launcher
     *                   (adds the {@code -3.12} version argument).
     * @param harnessDir absolute path to the {@code harness/} directory.
     */
    @SuppressWarnings("unchecked")
    public FLScenarioRunner(Map<String, Object> spec, String python, boolean pyLauncher, Path harnessDir) {
        this.spec = spec;
        this.python = python;
        this.pyLauncher = pyLauncher;
        this.harnessDir = harnessDir;
        // Pass-2 trainer hyperparameters and workload from the scenario spec
        // (frozen once per campaign, shared by all modes — D6/§9). Reading the
        // workload here rather than from CLI flags is what makes the scenario
        // file the single pre-registration: previously the runner hardcoded a
        // synthetic dataset, so the MNIST cells could not be produced from a
        // scenario file at all and had to be driven by hand.
        this.lr = ((Number) spec.getOrDefault("lr", 0.05)).doubleValue();
        this.batch = ((Number) spec.getOrDefault("batch", 16)).intValue();
        this.dataset = String.valueOf(spec.getOrDefault("dataset", "mnist"));
        this.samples = ((Number) spec.getOrDefault("samples", 0)).intValue();
        this.evalSamples = ((Number) spec.getOrDefault("eval_samples", 0)).intValue();
        this.evalEvery = ((Number) spec.getOrDefault("eval_every", 1)).intValue();
        this.numClasses = ((Number) spec.getOrDefault("num_classes", 10)).intValue();
        this.inChannels = ((Number) spec.getOrDefault("in_channels", 1)).intValue();
        this.globalEvery = ((Number) spec.getOrDefault("global_every", 5)).intValue();
        Object m = spec.get("modes");
        this.modes = (m instanceof List)
                ? ((List<Object>) m).stream().map(String::valueOf).collect(java.util.stream.Collectors.toList())
                : List.of("decentralized");
        Object cal = spec.get("calibration");
        this.calibration = cal == null ? null : Paths.get(String.valueOf(cal));
        for (String mode : this.modes) {
            if (!ALL_MODES.contains(mode)) {
                throw new IllegalArgumentException("unknown coordination mode '" + mode
                        + "' (expected one of " + ALL_MODES + ")");
            }
        }
        if (!this.modes.contains("decentralized")) {
            throw new IllegalArgumentException(
                    "every scenario must include the 'decentralized' leg: it produces the "
                    + "Pass-2 trace that the Pass-3 system replay consumes");
        }
    }

    // ------------------------------------------------------------------
    // Expansion.
    // ------------------------------------------------------------------

    /** A resolved cell: factor map + a stable cell id. */
    public static final class Cell {
        public final Map<String, Object> factors;
        public final String cellId;

        Cell(Map<String, Object> factors, String cellId) {
            this.factors = factors;
            this.cellId = cellId;
        }
    }

    /** Expands the spec into the deduplicated set of cells (no seeds yet). */
    @SuppressWarnings("unchecked")
    public List<Cell> expandCells() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : spec.entrySet()) {
            if (!"groups".equals(e.getKey()) && !"seeds".equals(e.getKey())) {
                defaults.put(e.getKey(), e.getValue());
            }
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        if (spec.containsKey("groups")) {
            for (Object g : (List<Object>) spec.get("groups")) {
                groups.add((Map<String, Object>) g);
            }
        } else {
            groups.add(new LinkedHashMap<>());
        }

        // Resolve each group against defaults, then cross-product its list factors.
        List<Map<String, Object>> resolved = new ArrayList<>();
        Set<String> signatures = new LinkedHashSet<>();
        for (Map<String, Object> group : groups) {
            Map<String, Object> base = new LinkedHashMap<>(defaults);
            base.putAll(group);
            for (Map<String, Object> combo : crossProduct(base)) {
                String sig = canonicalSignature(combo);
                if (signatures.add(sig)) {
                    resolved.add(combo);
                }
            }
        }

        // Which factors actually vary across the expanded set → the cell id.
        List<String> varied = variedKeys(resolved);
        List<Cell> cells = new ArrayList<>();
        for (Map<String, Object> combo : resolved) {
            cells.add(new Cell(combo, cellId(combo, varied)));
        }
        return cells;
    }

    private static List<Map<String, Object>> crossProduct(Map<String, Object> base) {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(new LinkedHashMap<>());
        for (Map.Entry<String, Object> e : base.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            List<Object> options = new ArrayList<>();
            if (LIST_FACTORS.contains(key) && val instanceof List) {
                options.addAll((List<Object>) castList(val));
            } else {
                options.add(val);
            }
            List<Map<String, Object>> next = new ArrayList<>();
            for (Map<String, Object> partial : out) {
                for (Object opt : options) {
                    Map<String, Object> m = new LinkedHashMap<>(partial);
                    m.put(key, opt);
                    next.add(m);
                }
            }
            out = next;
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object v) {
        return (List<Object>) v;
    }

    private static String canonicalSignature(Map<String, Object> combo) {
        StringBuilder sb = new StringBuilder();
        new java.util.TreeMap<>(combo).forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        return sb.toString();
    }

    private static List<String> variedKeys(List<Map<String, Object>> cells) {
        List<String> varied = new ArrayList<>();
        for (String key : LIST_FACTORS) {
            Set<Object> distinct = new LinkedHashSet<>();
            for (Map<String, Object> c : cells) {
                if (c.containsKey(key)) {
                    distinct.add(c.get(key));
                }
            }
            if (distinct.size() > 1) {
                varied.add(key);
            }
        }
        return varied;
    }

    private static String cellId(Map<String, Object> combo, List<String> varied) {
        if (varied.isEmpty()) {
            return "cell";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : varied) {
            // A factor that only applies to some groups (e.g. ws_beta, which is
            // meaningless outside small_world) is simply absent from the others;
            // emitting "…=null" for those would put a nonsense token in the
            // artefact path.
            Object value = combo.get(key);
            if (value == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('_');
            }
            sb.append(shortKey(key)).append('=').append(value);
        }
        return sb.length() == 0 ? "cell" : sb.toString();
    }

    private static String shortKey(String key) {
        switch (key) {
            case "topology": return "topo";
            case "policy": return "pol";
            case "merge": return "mrg";
            case "gamma": return "gam";
            case "ws_k_ring": return "deg";
            case "ws_beta": return "beta";
            default: return key;
        }
    }

    /** A single run: a cell at a seed, with its output directory. */
    public static final class Run {
        public final Cell cell;
        public final long seed;
        public final Path dir;

        Run(Cell cell, long seed, Path dir) {
            this.cell = cell;
            this.seed = seed;
            this.dir = dir;
        }
    }

    /** The coordination legs this scenario runs, in execution order. */
    public List<String> modes() {
        return modes;
    }

    /** Expands cells × seeds into runs under {@code <out>/<campaign>/<scenario>/<cellId>/seed_<s>}. */
    @SuppressWarnings("unchecked")
    public List<Run> expandRuns(Path out, String campaign) {
        // Absolutize once: Pass-2 runs run.py with harnessDir as its working
        // directory, so a relative results path (the CLI default) would not
        // resolve from there.
        out = out.toAbsolutePath().normalize();
        String scenario = String.valueOf(spec.getOrDefault("scenario", "scenario"));
        List<Long> seeds = new ArrayList<>();
        for (Object s : (List<Object>) spec.getOrDefault("seeds", List.of(42))) {
            seeds.add(((Number) s).longValue());
        }
        List<Run> runs = new ArrayList<>();
        for (Cell cell : expandCells()) {
            for (long seed : seeds) {
                Path dir = out.resolve(campaign).resolve(scenario).resolve(cell.cellId)
                        .resolve("seed_" + seed);
                runs.add(new Run(cell, seed, dir));
            }
        }
        return runs;
    }

    // ------------------------------------------------------------------
    // Per-cell config + execution.
    // ------------------------------------------------------------------

    private Pass1Main.Config toConfig(Run run) {
        Map<String, Object> f = run.cell.factors;
        Pass1Main.Config c = new Pass1Main.Config();
        c.scenarioId = String.valueOf(spec.getOrDefault("scenario", "scenario")) + "/" + run.cell.cellId;
        c.seed = run.seed;
        c.topologyType = String.valueOf(f.getOrDefault("topology", "clustered"));
        c.n = ((Number) f.getOrDefault("n", 6)).intValue();
        if ("clustered".equals(c.topologyType)) {
            c.clusterSizes = new int[] { c.n / 2, c.n - c.n / 2 };
            c.bridges = 2;
        }
        c.baM = ((Number) f.getOrDefault("ba_m", 2)).intValue();
        c.wsKRing = ((Number) f.getOrDefault("ws_k_ring", 4)).intValue();
        c.wsBeta = ((Number) f.getOrDefault("ws_beta", 0.1)).doubleValue();
        if (f.get("dynamic") instanceof Map) {
            Object pl = ((Map<String, Object>) f.get("dynamic")).get("p_link");
            if (pl != null) {
                c.pLink = ((Number) pl).doubleValue();
            }
        }
        c.k = ((Number) f.getOrDefault("k", 2)).intValue();
        c.rounds = ((Number) f.getOrDefault("rounds", 3)).intValue();
        c.localEpochs = ((Number) f.getOrDefault("local_epochs", 1)).intValue();
        c.gammaSchedule = String.valueOf(f.getOrDefault("gamma", "EXPLORE_THEN_EXPLOIT"));
        c.mergeRule = String.valueOf(f.getOrDefault("merge", "DRIFT_SUPPRESSED"));
        c.policy = String.valueOf(f.getOrDefault("policy", "COMPOSITE"));
        c.dirichletRho = ((Number) f.getOrDefault("dirichlet_rho", 0.5)).doubleValue();
        c.signatureDim = ((Number) f.getOrDefault("signature_dim", 64)).intValue();
        c.modelName = String.valueOf(f.getOrDefault("model", "lenet5"));
        // The metered payload is derived from the model the harness will build,
        // so it can never drift from the model actually trained.
        c.numClasses = numClasses;
        c.inChannels = inChannels;
        c.paramCount = -1L;
        c.payloadBytesFloat32 = -1L;
        c.linkBandwidthScale = ((Number) spec.getOrDefault("link_bandwidth_scale", 1.0)).doubleValue();
        if (spec.containsKey("cost_source")) {
            c.costSource = Pass1Main.CostSource.valueOf(
                    String.valueOf(spec.get("cost_source")).toUpperCase(Locale.ROOT));
        }
        return c;
    }

    private static PeerSelectionPolicy policy(String id, String gamma) {
        switch (id) {
            case "RANDOM": return new RandomK();
            case "DEGREE": return new DegreeBased();
            case "SINGLE_LATENCY": return new SingleFactorLatency();
            case "SINGLE_LOAD": return new SingleFactorLoad();
            case "SINGLE_DIVERGENCE": return new SingleFactorDivergence();
            case "SINGLE_BANDWIDTH": return new SingleFactorBandwidth();
            case "ALL_NEIGHBORS": return new AllNeighbors();
            case "COMPOSITE":
            default: return new CompositeScore(GammaSchedule.valueOf(gamma));
        }
    }

    private static MergeRule mergeRule(String id) {
        switch (id) {
            case "UNIFORM": return new UniformAvg();
            case "SAMPLE_WEIGHTED": return new SampleWeighted();
            case "METROPOLIS_HASTINGS": return new MetropolisHastings();
            case "FIXED_UNIFORM": return new FixedUniform();
            case "DRIFT_SUPPRESSED":
            default: return new DriftSuppressed();
        }
    }

    /**
     * Runs one cell end-to-end: Pass 1 → Pass 2 (every requested coordination
     * leg) → Pass 3 (system replay of the decentralized leg) → the TID gap.
     *
     * <p>The decentralized leg writes {@code pass2/}; the baselines write
     * {@code pass2_centralized/} and {@code pass2_hierarchical/}. Pass 3 replays
     * {@code pass2/} — the same trace whose accuracy is reported for this cell,
     * so a cell's learning results and its system results always come from one
     * run on one workload.</p>
     */
    public void runCell(Run run) throws Exception {
        Path pass1 = run.dir.resolve("pass1");
        Path pass2 = run.dir.resolve("pass2");
        Path pass3 = run.dir.resolve("pass3");
        if (Files.exists(pass3.resolve("run_metadata.json"))) {
            System.out.println("[runner] resume: skipping completed cell " + run.dir);
            return;
        }
        Files.createDirectories(pass1);

        Pass1Main.Config cfg = toConfig(run);
        Pass1Main.Built built = Pass1Main.build(cfg);
        SystemTraceWriter.write(pass1.resolve("system_trace.json"), built.trace);
        Path systemTrace = pass1.resolve("system_trace.json");

        for (String mode : modes) {
            Path out = "decentralized".equals(mode) ? pass2 : run.dir.resolve("pass2_" + mode);
            if (Files.exists(out.resolve("summary.json"))) {
                System.out.println("[runner] resume: leg " + mode + " already present");
                continue;
            }
            System.out.println("[runner] pass2 " + mode + " -> " + out.getFileName());
            runPass2(systemTrace, out, cfg, mode);
        }

        runPass3(built, pass2, pass3, cfg);

        // TID gap (Eq. tid-gap) whenever the centralized reference leg was run.
        writeTidGap(run.dir, pass2, run.dir.resolve("pass2_centralized"));

        // PNG figures for the cell (graceful — skipped without Python/matplotlib;
        // the CSV artefacts above are the released data either way). Written to
        // the cell root so the pass1/2/3 dirs stay exactly the released tree.
        System.out.println("[runner] figures for " + run.dir.getFileName() + ":");
        GossipPlots.plotAccuracy(pass2.resolve("learning_trace.csv"),
                run.dir.resolve("accuracy_vs_round.png"));
        GossipPlots.plotTid(pass3.resolve("tid_timeseries.csv"),
                run.dir.resolve("gossip_tid.png"));
        GossipPlots.plotTrafficIdle(pass3.resolve("gossip_telemetry.csv"),
                run.dir.resolve("gossip_traffic_idle.png"));
        GossipPlots.plotEnergy(pass3.resolve("energy_per_round.csv"),
                run.dir.resolve("gossip_energy.png"));

        System.out.println("[runner] done cell " + run.dir);
    }

    /** Maps a scenario coordination mode onto the harness's {@code --mode} value. */
    private static String harnessMode(String mode) {
        return "decentralized".equals(mode) ? "gossip" : mode;
    }

    private void runPass2(Path systemTrace, Path pass2, Pass1Main.Config cfg, String mode)
            throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(python);
        if (pyLauncher) {
            cmd.add("-3.12");
        }
        cmd.add(harnessDir.resolve("run.py").toString());
        cmd.add("--system-trace");
        cmd.add(systemTrace.toString());
        cmd.add("--mode");
        cmd.add(harnessMode(mode));
        cmd.add("--out");
        cmd.add(pass2.toString());
        cmd.add("--dataset");
        cmd.add(dataset);
        cmd.add("--samples");
        cmd.add(Integer.toString(samples));
        cmd.add("--eval-samples");
        cmd.add(Integer.toString(evalSamples));
        cmd.add("--eval-every");
        cmd.add(Integer.toString(evalEvery));
        cmd.add("--model");
        cmd.add(cfg.modelName);
        cmd.add("--num-classes");
        cmd.add(Integer.toString(numClasses));
        cmd.add("--in-channels");
        cmd.add(Integer.toString(inChannels));
        cmd.add("--epochs");
        cmd.add(Integer.toString(cfg.localEpochs));
        cmd.add("--lr");
        cmd.add(Double.toString(lr));
        cmd.add("--batch");
        cmd.add(Integer.toString(batch));
        if ("hierarchical".equals(mode)) {
            cmd.add("--global-every");
            cmd.add(Integer.toString(globalEvery));
        }
        Process p = new ProcessBuilder(cmd).directory(harnessDir.toFile()).redirectErrorStream(true).start();
        StringBuilder log = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                log.append(line).append('\n');
            }
        }
        if (!p.waitFor(30, java.util.concurrent.TimeUnit.MINUTES) || p.exitValue() != 0) {
            throw new IllegalStateException("Pass-2 failed for " + systemTrace + ":\n" + log);
        }
    }

    private void runPass3(Pass1Main.Built built, Path pass2, Path pass3, Pass1Main.Config cfg) throws Exception {
        Timed.resetTimed();
        EnergyDataCollectorFL.clearAll();
        hu.u_szeged.inf.fog.simulator.iot.Device.allDevices.clear();
        SimRandom.setSeed(cfg.seed);

        TraceReader reader = TraceReader.load(pass2);
        reader.validateAgainst(built.topology);
        TraceProvider provider = new TraceProvider(reader);

        FLTopology topo = built.topology;
        int n = topo.size();
        float[][] initSig = new float[n][];
        int[] sampleCounts = new int[n];
        for (int i = 0; i < n; i++) {
            initSig[i] = reader.signature(i, 0);
            LearningOutcome o0 = reader.metrics(i, 0);
            sampleCounts[i] = o0 == null ? 1 : o0.nSamples;
        }
        List<FLGossipNode> nodes = GossipDeviceFactory.build(topo, initSig, sampleCounts);

        boolean dynamic = "dynamic".equals(cfg.topologyType);
        // The load profile MUST be the instance Pass 1 embedded in the trace.
        // Passing null here would zero the C̃ term on the Java side while the
        // Python harness reads the real schedule out of the trace, silently
        // breaking selection agreement.
        FLGossipOrchestrator orch = new FLGossipOrchestrator(
                topo, nodes, policy(cfg.policy, cfg.gammaSchedule), mergeRule(cfg.mergeRule),
                cfg.k, cfg.rounds, 1000L, 0.0, 1.0, built.loadProfile, built.paramCount,
                dynamic, cfg.pLink, cfg.seed);
        orch.setScenarioId(cfg.scenarioId);
        orch.enableTraceReplay(provider, "warn");
        orch.setTidRecorder(new TidRecorder(topo));

        // Analytic compute delay: a calibrated per-tier fit when one is supplied,
        // so the modelled device tier drives round duration and barrier idle.
        String[] tiers = new String[n];
        for (int i = 0; i < n; i++) {
            tiers[i] = built.trace.topology.nodes.get(i).profile;
        }
        ComputeDelayModel delay = ComputeDelayModel.identity();
        if (calibration != null && Files.exists(calibration)) {
            delay = ComputeDelayModel.load(calibration);
            System.out.println("[runner] compute delay: calibrated fit from " + calibration);
        } else if (calibration != null) {
            System.out.println("[runner] WARNING: calibration file not found (" + calibration
                    + "); falling back to measured host wall-clock time");
        }
        orch.setComputeDelayModel(delay, tiers);
        if (dynamic) {
            orch.setLambda2Metadata(built.trace.topology.lambda2Expected, built.trace.topology.lambda2Union);
        }
        boolean[] finished = { false };
        orch.setFinishedCallback(() -> finished[0] = true);

        long bound = (cfg.rounds + 5L) * 1000L + 500_000L;
        Timed.simulateUntil(bound);
        if (!finished[0]) {
            throw new IllegalStateException("Pass-3 replay did not finish within " + bound + " ticks");
        }
        orch.exportAll(pass3);
    }

    /**
     * Writes {@code tid_gap.json} (Eq. tid-gap) for a cell that ran both the
     * decentralized and the centralized leg: {@code TID_gap = A_cen − A_dec}, on
     * the held-out pool, at a matched local computation budget and the same
     * seed. Silently skipped when the centralized reference leg was not part of
     * this scenario.
     *
     * @param cellDir the cell's output directory.
     * @param decPass2 the decentralized leg's Pass-2 directory.
     * @param cenPass2 the centralized leg's Pass-2 directory.
     */
    private void writeTidGap(Path cellDir, Path decPass2, Path cenPass2) throws IOException {
        Path decSummary = decPass2.resolve("summary.json");
        Path cenSummary = cenPass2.resolve("summary.json");
        if (!Files.exists(decSummary) || !Files.exists(cenSummary)) {
            return;
        }
        ObjectMapper om = new ObjectMapper();
        JsonNode dec = om.readTree(decSummary.toFile());
        JsonNode cen = om.readTree(cenSummary.toFile());
        double aCen = cen.path("acc_global_model").asDouble();
        double aDec = dec.path("acc_mean_over_nodes").asDouble();
        Map<String, Object> gap = new LinkedHashMap<>();
        gap.put("eval_split", dec.path("eval_split").asText("heldout"));
        gap.put("eval_samples", dec.path("eval_samples").asInt());
        gap.put("a_cen", aCen);
        gap.put("a_dec_mean_over_nodes", aDec);
        gap.put("tid_gap", aCen - aDec);
        if (dec.has("acc_mean_model")) {
            double aBar = dec.path("acc_mean_model").asDouble();
            gap.put("a_dec_mean_model", aBar);
            gap.put("tid_gap_mean_model", aCen - aBar);
        }
        if (dec.has("acc_worst_node")) {
            gap.put("a_dec_worst_node", dec.path("acc_worst_node").asDouble());
        }
        om.writerWithDefaultPrettyPrinter().writeValue(cellDir.resolve("tid_gap.json").toFile(), gap);
        System.out.printf(Locale.US, "[runner] TID gap = %.4f (A_cen %.4f − A_dec %.4f)%n",
                aCen - aDec, aCen, aDec);
    }

    // ------------------------------------------------------------------
    // CLI.
    // ------------------------------------------------------------------

    /** CLI entry point; see the usage line below for the accepted flags. */
    public static void main(String[] args) throws Exception {
        Path config = null;
        Path out = Paths.get("results");
        String campaign = "campaign";
        boolean dryRun = false;
        String python = System.getProperty("harness.python", "py");
        Path harnessDir = Paths.get("..", "harness").toAbsolutePath().normalize();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config":
                    config = Paths.get(args[++i]);
                    break;
                case "--out":
                    out = Paths.get(args[++i]);
                    break;
                case "--campaign":
                    campaign = args[++i];
                    break;
                case "--dry-run":
                    dryRun = true;
                    break;
                case "--python":
                    python = args[++i];
                    break;
                case "--harness":
                    harnessDir = Paths.get(args[++i]).toAbsolutePath().normalize();
                    break;
                default:
                    break;
            }
        }
        if (config == null) {
            System.err.println("usage: FLScenarioRunner --config <scenario.yaml> [--dry-run] [--out dir] [--campaign name]");
            System.exit(2);
        }
        Map<String, Object> spec = loadYaml(config);
        boolean pyLauncher = "py".equals(python);
        FLScenarioRunner runner = new FLScenarioRunner(spec, python, pyLauncher, harnessDir);
        List<Run> runs = runner.expandRuns(out, campaign);

        if (dryRun) {
            System.out.println("[dry-run] scenario=" + spec.getOrDefault("scenario", "?")
                    + " erq=" + spec.getOrDefault("erq", "?")
                    + " cells=" + runner.expandCells().size()
                    + " runs=" + runs.size()
                    + " modes=" + runner.modes
                    + " dataset=" + runner.dataset
                    + " samples=" + runner.samples
                    + " pass2_legs=" + (runs.size() * runner.modes.size()));
            for (Run r : runs) {
                System.out.println("  " + r.cell.cellId + "  seed=" + r.seed + "  -> " + r.dir);
            }
            return;
        }
        for (Run r : runs) {
            runner.runCell(r);
        }
        System.out.println("[runner] campaign complete: " + runs.size() + " runs.");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> loadYaml(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return new Yaml().loadAs(in, Map.class);
        }
    }
}
