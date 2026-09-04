package hu.u_szeged.inf.fog.simulator.fl.cosim;

import hu.u_szeged.inf.fog.simulator.fl.gossip.LoadProfile;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import hu.u_szeged.inf.fog.simulator.fl.topology.GeoCostModel;
import hu.u_szeged.inf.fog.simulator.fl.topology.Lambda2;
import hu.u_szeged.inf.fog.simulator.fl.topology.LinkCostModel;
import hu.u_szeged.inf.fog.simulator.fl.topology.TopologyFactory;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pass-1 system pre-run (§8.4 Pass 1): instantiates a scenario (topology + node
 * descriptors + per-node load profiles + hyper/model) and writes
 * {@code system_trace.json} for the Python learning harness. Campaign runs go
 * through {@link hu.u_szeged.inf.fog.simulator.fl.run.FLScenarioRunner}; this
 * class provides the clustered base-graph scenario used by the co-simulation
 * canary, plus a minimal CLI for building one scenario by hand.
 */
public final class Pass1Main {

    private Pass1Main() {
    }

    /** Knobs for a scenario's Pass-1 system pre-run. */
    public static final class Config {
        public String scenarioId = "S_clustered";
        /** Topology archetype: clustered | ring | mesh | scale_free | dynamic. */
        public String topologyType = "clustered";
        public int n = 6;                          // for ring / mesh / scale_free / dynamic
        public int[] clusterSizes = { 3, 3 };      // for clustered
        public int bridges = 2;                    // for clustered
        public int baM = 2;                        // for scale_free (BA edges per node)
        public double pLink = 0.9;                 // for dynamic
        public int wsKRing = 4;                    // for small_world (lattice degree, even)
        public double wsBeta = 0.1;                // for small_world (rewiring probability)
        public long seed = 42L;
        public int k = 2;
        public int rounds = 3;
        public int localEpochs = 1;
        public String gammaSchedule = "EXPLORE_THEN_EXPLOIT";
        public String mergeRule = "DRIFT_SUPPRESSED";
        public String policy = "COMPOSITE";
        public double dirichletRho = 0.5;
        public int signatureDim = 64;
        public long signatureSeed = 1234L;
        public String modelName = "lenet5";
        public int numClasses = 10;
        public int inChannels = 1;
        /**
         * Model parameter count. Left {@code <= 0} it is <b>derived</b> from
         * {@code modelName}, {@code numClasses}, and {@code inChannels} by
         * {@link #deriveParamCount(Config)} so the metered payload always
         * matches the model the harness actually trains (§8.4). Set explicitly
         * only to pin a legacy value.
         */
        public long paramCount = -1L;
        /** Payload bytes; derived as {@code 4 × paramCount} when {@code <= 0}. */
        public long payloadBytesFloat32 = -1L;
        /**
         * Per-link cost source (§8.1). {@link CostSource#HETEROGENEOUS} is the
         * §9 default: per-node access-link latency/bandwidth from the device
         * tier plus great-circle propagation, so the {@code L̃} and {@code B̃}
         * terms of Eq. score are non-degenerate. {@link CostSource#UNIFORM}
         * restores the flat generator defaults (identical cost on every edge),
         * which collapses those terms onto the normaliser's homogeneous guard.
         * The exported edges — and the topology hash — reflect whichever source
         * was used; there is one source of truth.
         */
        public CostSource costSource = CostSource.HETEROGENEOUS;
        /**
         * Multiplier on every tier's link-capacity band (§9 constrained-bandwidth
         * cell). {@code 1.0} is the default deployment; {@code 0.1} makes links
         * ten times slower, which is what moves communication from ~2% of a
         * round into the binding constraint and gives the {@code B̃} selection
         * term a lever it otherwise does not have. Latency is not scaled.
         */
        public double linkBandwidthScale = 1.0;
        /** Static per-tier background-load floor for {@code C_j} (near, far). */
        public double[] loadBaseByTier = { 0.15, 0.35 };
        /** Amplitude of the seeded background-load component for {@code C_j}. */
        public double loadAmplitude = 0.30;
    }

    /** Where per-edge {@code (L, B)} come from (§8.1). */
    public enum CostSource {
        /** Flat generator defaults — identical cost on every edge. */
        UNIFORM,
        /** Per-node access links (device tier) + great-circle propagation. */
        HETEROGENEOUS
    }

    /**
     * Parameter count of the configured model, so the metered payload matches
     * the model the harness trains (a mismatch silently biases every traffic and
     * energy figure).
     *
     * <p>LeNet-5 for {@code C×28×28}: {@code 60856 + 300·(inChannels−1) + 85·numClasses}
     * (conv 156/2416 at 1 channel, fc 48120/10164, head {@code 85·numClasses}).</p>
     *
     * @param c the scenario config.
     * @return the parameter count.
     */
    public static long deriveParamCount(Config c) {
        if ("cifar10_cnn_v1".equals(c.modelName)) {
            // 3×32×32 conv stack + 8192→128→numClasses head.
            return 896L + 18_496L + 73_856L + 1_048_704L + 129L * c.numClasses;
        }
        // lenet5
        return 60_856L + 300L * (c.inChannels - 1L) + 85L * c.numClasses;
    }

    /** Builds the topology for the configured archetype. */
    private static FLTopology buildTopology(Config c) {
        switch (c.topologyType) {
            case "ring":
                return TopologyFactory.ring(c.n);
            case "mesh":
                return TopologyFactory.fullMesh(c.n);
            case "scale_free":
                return TopologyFactory.scaleFreeBA(c.n, c.baM, SimRandom.derive(c.seed, 0, 0));
            case "small_world":
                // Distinct derived stream from the BA generator, so the two
                // one-shot graph constructions can never share draws.
                return TopologyFactory.wattsStrogatz(c.n, c.wsKRing, c.wsBeta,
                        SimRandom.derive(c.seed, 0, 1));
            case "dynamic":
                // Dynamic over a full-mesh base (most connected ⇒ widest λ₂ range).
                return TopologyFactory.dynamic(TopologyFactory.fullMesh(c.n), c.pLink);
            case "clustered":
            default:
                return TopologyFactory.clustered(c.clusterSizes, c.bridges);
        }
    }

    /**
     * Per-cluster metro centres for the exported node geography (§9): a
     * two-region Hungarian fog deployment, ≈160 km apart. Nodes are placed on a
     * deterministic grid around their cluster's centre, so geography is a stable
     * property of the deployment rather than a per-seed random variable.
     */
    private static final double[][] CLUSTER_CENTRES = {
        { 46.253, 20.148 },  // cluster 0 — near-edge region
        { 47.498, 19.040 },  // cluster 1 — far-edge region
    };

    /** Grid pitch (degrees ≈ 11 km) used to spread nodes within a cluster. */
    private static final double GRID_PITCH_DEG = 0.10;
    /** Grid width; nodes wrap into rows of this many columns. */
    private static final int GRID_COLS = 5;

    /**
     * Deterministic location of node {@code i}, placed on a {@code GRID_COLS}-wide
     * grid around its cluster's metro centre.
     *
     * @param i node id.
     * @param indexInCluster the node's ordinal within its cluster.
     * @param cluster the node's cluster label.
     * @return the node's position.
     */
    private static GeoLocation location(int i, int indexInCluster, int cluster) {
        double[] centre = CLUSTER_CENTRES[cluster % CLUSTER_CENTRES.length];
        int row = indexInCluster / GRID_COLS;
        int col = indexInCluster % GRID_COLS;
        return new GeoLocation(centre[0] + (row - 1) * GRID_PITCH_DEG,
                centre[1] + (col - 2) * GRID_PITCH_DEG);
    }

    /**
     * Builds the scenario's topology and assembles the full {@link SystemTrace}:
     * node descriptors with geography and device tier, per-edge cost tables from
     * the configured {@link CostSource}, and the per-node background-load
     * schedule {@code C_j}. For a dynamic topology the per-round inactive
     * schedule and λ̄₂ / union-λ₂ metadata are included.
     *
     * <p><b>Cost heterogeneity matters.</b> Under {@link CostSource#UNIFORM}
     * every edge carries an identical latency and bandwidth and every node an
     * identical load, so the min–max normaliser's homogeneous guard drives
     * {@code L̃ = C̃ = B̃ = 0} and the composite selector degenerates to its
     * divergence term. {@link CostSource#HETEROGENEOUS} (the §9 default) makes
     * all four terms of Eq. score live.</p>
     *
     * @param c the scenario config.
     * @return a pair: the live topology and the assembled trace.
     */
    public static Built build(Config c) {
        FLTopology topo = buildTopology(c);
        int n = topo.size();
        int[] labels = topo.clusterLabels();

        // Node geography: a deterministic grid around each cluster's metro centre.
        GeoLocation[] locations = new GeoLocation[n];
        int[] seenInCluster = new int[CLUSTER_CENTRES.length];
        int[] indexInCluster = new int[n];
        for (int i = 0; i < n; i++) {
            int cl = labels[i] % CLUSTER_CENTRES.length;
            indexInCluster[i] = seenInCluster[cl]++;
            locations[i] = location(i, indexInCluster[i], cl);
        }

        // Device tiers follow the cluster: near-edge (cluster 0) vs far-edge.
        LinkCostModel.Tier near = LinkCostModel.NEAR_EDGE.withBandwidthScale(c.linkBandwidthScale);
        LinkCostModel.Tier far = LinkCostModel.FAR_EDGE.withBandwidthScale(c.linkBandwidthScale);
        LinkCostModel.Tier[] tiers = new LinkCostModel.Tier[n];
        for (int i = 0; i < n; i++) {
            tiers[i] = labels[i] == 0 ? near : far;
        }
        LinkCostModel linkCosts = new LinkCostModel(tiers, new GeoCostModel(), c.seed);

        // §8.1: rebuild the edge cost tables before hashing/serialisation, so the
        // exported graph and its hash reflect the costs actually used.
        if (c.costSource == CostSource.HETEROGENEOUS) {
            topo = TopologyFactory.withLinkCosts(topo, locations, linkCosts);
        }

        List<SystemTrace.NodeJson> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            GeoLocation loc = locations[i];
            SystemTrace.GeoJson geo = new SystemTrace.GeoJson(loc.latitude, loc.longitude);
            String profile = labels[i] == 0 ? "near_edge" : "far_edge";
            int cores = labels[i] == 0 ? 8 : 4;
            double mips = labels[i] == 0 ? 0.004 : 0.001;
            double ram = labels[i] == 0 ? 16 : 8;
            nodes.add(new SystemTrace.NodeJson(i, profile, cores, mips, ram, geo, labels[i]));
        }

        // C_j (§8.4 Pass 1): a static per-tier floor plus a seeded background
        // component, embedded so Java and Python read identical loads. Under
        // UNIFORM the schedule is flattened to zero (the legacy neutral load).
        double[] baseLoad = new double[n];
        double amplitude = 0.0;
        if (c.costSource == CostSource.HETEROGENEOUS) {
            for (int i = 0; i < n; i++) {
                int tierIdx = Math.min(labels[i], c.loadBaseByTier.length - 1);
                baseLoad[i] = c.loadBaseByTier[tierIdx];
            }
            amplitude = c.loadAmplitude;
        }
        LoadProfile lp = new LoadProfile(baseLoad, amplitude, c.seed);
        Map<String, double[]> loadProfiles = lp.toLoadProfilesMap(c.rounds);

        SystemTrace.Hyper hyper = new SystemTrace.Hyper();
        hyper.k = c.k;
        hyper.rounds = c.rounds;
        hyper.localEpochs = c.localEpochs;
        hyper.gammaSchedule = c.gammaSchedule;
        hyper.mergeRule = c.mergeRule;
        hyper.policy = c.policy;
        hyper.dirichletRho = c.dirichletRho;
        hyper.signatureDim = c.signatureDim;
        hyper.signatureSeed = c.signatureSeed;

        // Payload must match the model the harness actually trains, otherwise
        // every traffic and energy figure is silently biased (§8.4).
        long paramCount = c.paramCount > 0 ? c.paramCount : deriveParamCount(c);
        long payloadBytes = c.payloadBytesFloat32 > 0 ? c.payloadBytesFloat32 : paramCount * 4L;

        SystemTrace.Model model = new SystemTrace.Model();
        model.name = c.modelName;
        model.paramCount = paramCount;
        model.payloadBytesFloat32 = payloadBytes;

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("n", n);
        Double lambda2Expected = null;
        Double lambda2Union = null;
        List<SystemTrace.DynamicRoundJson> schedule = null;
        switch (c.topologyType) {
            case "clustered":
                params.put("clusterSizes", c.clusterSizes);
                params.put("bridges", c.bridges);
                break;
            case "scale_free":
                params.put("m", c.baM);
                break;
            case "small_world":
                params.put("k_ring", c.wsKRing);
                params.put("beta", c.wsBeta);
                break;
            case "dynamic":
                params.put("p_link", c.pLink);
                schedule = SystemTraceWriter.buildDynamicSchedule(topo, c.seed, c.pLink, c.rounds);
                lambda2Expected = Lambda2.expectedLambda2(topo, c.pLink, c.rounds, c.seed);
                lambda2Union = Lambda2.unionLambda2(topo, c.pLink, c.rounds, c.seed);
                break;
            default:
                break;
        }

        SystemTrace trace = SystemTraceWriter.build(c.scenarioId, c.seed, topo, c.topologyType,
                params, nodes, hyper, model, loadProfiles, lambda2Expected, lambda2Union, schedule);
        return new Built(topo, trace, lp, linkCosts, paramCount, payloadBytes);
    }

    /** Result of {@link #build(Config)}. */
    public static final class Built {
        public final FLTopology topology;
        public final SystemTrace trace;
        /**
         * The exact {@code C_j} schedule embedded in the trace. Pass 3 must
         * drive the selection policy from <i>this</i> instance, not from
         * {@code null}: a null load profile silently zeroes the {@code C̃} term
         * and desynchronises the Java replay from the Python harness, which
         * reads the schedule out of the trace.
         */
        public final LoadProfile loadProfile;
        /** The per-node access-link draws behind the exported edge costs. */
        public final LinkCostModel linkCosts;
        /** Derived model parameter count (drives the metered payload). */
        public final long paramCount;
        /** Derived float32 payload size in bytes. */
        public final long payloadBytesFloat32;

        Built(FLTopology topology, SystemTrace trace, LoadProfile loadProfile,
              LinkCostModel linkCosts, long paramCount, long payloadBytesFloat32) {
            this.topology = topology;
            this.trace = trace;
            this.loadProfile = loadProfile;
            this.linkCosts = linkCosts;
            this.paramCount = paramCount;
            this.payloadBytesFloat32 = payloadBytesFloat32;
        }
    }

    /**
     * CLI: {@code Pass1Main --out <file> [--seed s] [--rounds r] [--k k]
     * [--scenario id] [--num-classes c] [--in-channels ch] [--uniform-costs]}.
     */
    public static void main(String[] args) throws Exception {
        Config c = new Config();
        Path out = Paths.get("system_trace.json");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out":
                    out = Paths.get(args[++i]);
                    break;
                case "--seed":
                    c.seed = Long.parseLong(args[++i]);
                    break;
                case "--rounds":
                    c.rounds = Integer.parseInt(args[++i]);
                    break;
                case "--k":
                    c.k = Integer.parseInt(args[++i]);
                    break;
                case "--scenario":
                    c.scenarioId = args[++i];
                    break;
                case "--topology":
                    c.topologyType = args[++i];
                    break;
                case "--n":
                    c.n = Integer.parseInt(args[++i]);
                    c.clusterSizes = new int[] { c.n / 2, c.n - c.n / 2 };
                    break;
                case "--ws-k":
                    c.wsKRing = Integer.parseInt(args[++i]);
                    break;
                case "--ws-beta":
                    c.wsBeta = Double.parseDouble(args[++i]);
                    break;
                case "--num-classes":
                    c.numClasses = Integer.parseInt(args[++i]);
                    break;
                case "--in-channels":
                    c.inChannels = Integer.parseInt(args[++i]);
                    break;
                case "--uniform-costs":
                    c.costSource = CostSource.UNIFORM;
                    break;
                default:
                    break;
            }
        }
        Built built = build(c);
        SystemTraceWriter.write(out, built.trace);
        System.out.println("[pass1] wrote " + out.toAbsolutePath()
                + " (topology=" + c.topologyType + " n=" + built.topology.size()
                + " edges=" + built.topology.edges().size()
                + " lambda2=" + String.format(java.util.Locale.US, "%.4f", built.topology.lambda2())
                + " topology_hash=" + built.topology.topologyHash() + ")");
    }
}
