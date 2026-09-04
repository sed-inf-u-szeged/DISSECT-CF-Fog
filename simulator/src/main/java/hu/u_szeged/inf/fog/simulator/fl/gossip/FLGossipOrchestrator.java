package hu.u_szeged.inf.fog.simulator.fl.gossip;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.fl.cosim.ComputeDelayModel;
import hu.u_szeged.inf.fog.simulator.fl.cosim.LearningProvider;
import hu.u_szeged.inf.fog.simulator.fl.cosim.TidRecorder;
import hu.u_szeged.inf.fog.simulator.fl.gossip.FLGossipNode.ReceivedModel;
import hu.u_szeged.inf.fog.simulator.fl.merge.MergeRule;
import hu.u_szeged.inf.fog.simulator.fl.selection.PeerSelectionPolicy;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology.Edge;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Drives synchronous decentralized gossip rounds (§8.1 Gossip Orchestrator,
 * Alg. 1), in parallel with the existing central orchestrator and in the manner
 * of the hierarchical supervisor: a periodic {@link Timed} coordinator that
 * performs <i>no aggregation of its own</i>. Each node maintains only its own
 * model; there is no global state.
 *
 * <h2>Round state machine (Alg. 1)</h2>
 * <pre>
 *   tick → openRound:  SELECTING  (build N_i, score, top-k)         [lines 1–3]
 *                      EXCHANGING (schedule timed peer-exchange events) [line 4]
 *   on all arrivals →  MERGING    (drift-suppressed merge on signatures) [line 5]
 *                      TRAINING   (synthetic/calibrated local-train delay) [line 6]
 *   barrier →          DONE, record telemetry, open next round       [line 7]
 * </pre>
 * Rounds are synchronous: the next round opens only after every node reaches the
 * barrier. Round duration is set by the slowest participant; the resulting
 * per-node idle time is recorded as a first-class metric.
 *
 * <h2>Determinism</h2>
 * Peer selection draws from {@code SimRandom.derive(seed, round, node)};
 * in-transit failures use the legacy shared stream; exchange timing and energy
 * are computed by DISSECT-CF deterministically. Two runs with the same seed
 * produce byte-identical telemetry.
 *
 * <p>This phase runs in SYNTHETIC learning mode (no trace): payload bytes come
 * from {@code paramCount × 4 × compressionFactor}, and a deterministic synthetic
 * signature perturbation stands in for real training. The TRACE/ONLINE learning
 * providers are wired in Phase 3.</p>
 */
public final class FLGossipOrchestrator extends Timed implements FLPeerExchangeEvent.ExchangeListener {

    private final FLTopology topology;
    private final List<FLGossipNode> nodes;
    private final PeerSelectionPolicy policy;
    private final MergeRule mergeRule;
    private final int k;
    private final int maxRounds;
    private final long roundInterval;
    private final double exchangeFailureProb;
    private final double exchangeCompressionFactor;
    private final LoadProfile loadProfile;
    private final long paramCount;
    private final boolean dynamic;
    private final double pLink;
    private final long seed;

    // ---- per-round state ----
    private int currentRound;
    private long roundOpenTick;
    private int pending;
    private final Map<Integer, List<Integer>> peerSets = new LinkedHashMap<>();
    private final long[] exchangeSpan; // per-node max exchange delay this round

    // ---- telemetry ----
    private final List<long[]> roundRows = new ArrayList<>(); // [round,node,ul,dl,busy,train,roundDur,idle]
    private final List<String> peerCol = new ArrayList<>();   // aligned with roundRows: ";"-joined peers
    private Path exportPath;
    private Runnable finishedCallback;

    // ---- TRACE mode (Pass-3 replay; §8.4) ----
    /** Non-null ⇒ TRACE replay: the trace is authoritative for peers/payload/signatures. */
    private LearningProvider learningProvider;
    /** "warn" (default) logs a sub-100% selection-agreement rate; "abort" throws (CI canary). */
    private String agreementMode = "warn";
    /** Optional TID instrumentation, recorded at each round barrier. */
    private TidRecorder tidRecorder;
    /** Calibrated analytic compute delay (§8.4); identity ⇒ raw measured ms. */
    private ComputeDelayModel computeDelay = ComputeDelayModel.identity();
    /** Per-node device tier label, for the compute-delay lookup. */
    private String[] nodeTier;
    private int agreementMatches;
    private int agreementTotal;
    /** Replayed per-(node,round) accuracy read from the trace (TRACE mode). */
    private final Map<Long, Double> replayedAcc = new LinkedHashMap<>();

    // ---- per-round energy capture at the (quiescent) round barrier (feeds P5.1) ----
    /** Per-node cumulative energy baseline (mJ) from the previous barrier snapshot. */
    private double[] lastEnergyMj;
    /** Rows: {round, node, ulBytes, dlBytes, energyMj} — the per-round node energy. */
    private final List<double[]> perRoundEnergyRows = new ArrayList<>();
    /** One-time guard so a transient-load energy skip is logged at most once. */
    private Boolean energyWarned;

    // ---- per-exchange records (feeds P5.1 per_exchange.csv) ----
    /** Rows: {round, src, dst, bytes, delayTicks, failed}; failed set on a lost transfer. */
    private final List<long[]> perExchange = new ArrayList<>();
    private final Map<Long, Integer> exchangeIndex = new LinkedHashMap<>();
    private int dropoutsThisRound;
    /** Rows: {round, p50DelayTicks, p95DelayTicks, dropouts}. */
    private final List<long[]> gossipRoundRows = new ArrayList<>();
    /** Per-round exchange delays for the p50/p95 summary. */
    private List<Long> roundDelays = new ArrayList<>();

    private String scenarioId = "gossip";
    private Double lambda2Expected;
    private Double lambda2Union;

    /** Synthetic-training perturbation stream key (synthetic mode only). */
    private static final long TRAIN_STREAM_KEY_MULT = 1_000_003L;
    private static final long TRAIN_STREAM_KEY_ADD = 13L;

    /**
     * Builds and subscribes the orchestrator (full parameter form; see
     * {@link Builder} for the §9 defaults).
     *
     * @param topology the communication graph (mutated per round if {@code dynamic}).
     * @param nodes the gossip nodes (index = node id).
     * @param policy peer-selection policy.
     * @param mergeRule merge rule.
     * @param k peers exchanged per node per round.
     * @param maxRounds number of rounds.
     * @param roundInterval ticks between a round's barrier and the next round's open.
     * @param exchangeFailureProb in-transit loss probability per directed transfer.
     * @param exchangeCompressionFactor payload compression factor (default 1.0; hook only).
     * @param loadProfile per-node load schedule (may be null ⇒ load term = 0).
     * @param paramCount model parameter count (payload = paramCount × 4 × compression).
     * @param dynamic whether to toggle edges per round with {@code pLink}.
     * @param pLink per-round link-up probability (used iff {@code dynamic}).
     * @param seed campaign seed (drives derived per-decision streams).
     */
    public FLGossipOrchestrator(FLTopology topology, List<FLGossipNode> nodes,
                                PeerSelectionPolicy policy, MergeRule mergeRule,
                                int k, int maxRounds, long roundInterval,
                                double exchangeFailureProb, double exchangeCompressionFactor,
                                LoadProfile loadProfile, long paramCount,
                                boolean dynamic, double pLink, long seed) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must be non-empty");
        }
        if (nodes.size() != topology.size()) {
            throw new IllegalArgumentException("nodes count (" + nodes.size()
                    + ") must equal topology size (" + topology.size() + ")");
        }
        this.topology = topology;
        this.nodes = new ArrayList<>(nodes);
        this.policy = policy;
        this.mergeRule = mergeRule;
        this.k = k;
        this.maxRounds = maxRounds;
        this.roundInterval = Math.max(1L, roundInterval);
        this.exchangeFailureProb = exchangeFailureProb;
        this.exchangeCompressionFactor = exchangeCompressionFactor;
        this.loadProfile = loadProfile;
        this.paramCount = paramCount;
        this.dynamic = dynamic;
        this.pLink = pLink;
        this.seed = seed;
        this.exchangeSpan = new long[nodes.size()];
        this.currentRound = 0;
        subscribe(this.roundInterval); // open round 0 after one interval
    }

    /**
     * Fluent builder over the full constructor, with the paper's §9 defaults:
     * COMPOSITE selector (explore-then-exploit), drift-suppressed merge,
     * {@code k = 2}, no exchange failures, no compression, static topology,
     * LeNet-5 payload ({@code paramCount = 61,196}). Only the topology and the
     * nodes are required; everything else is an override:
     *
     * <pre>
     *   FLGossipOrchestrator orch = new FLGossipOrchestrator.Builder(topology, nodes)
     *           .rounds(8).seed(42L).build();
     * </pre>
     *
     * <p>{@link #build()} constructs (and thereby subscribes) the orchestrator,
     * exactly like the constructor.</p>
     */
    public static final class Builder {
        private final FLTopology topology;
        private final List<FLGossipNode> nodes;
        private PeerSelectionPolicy policy =
                new hu.u_szeged.inf.fog.simulator.fl.selection.CompositeScore(
                        hu.u_szeged.inf.fog.simulator.fl.selection.GammaSchedule.EXPLORE_THEN_EXPLOIT);
        private MergeRule mergeRule = new hu.u_szeged.inf.fog.simulator.fl.merge.DriftSuppressed();
        private int k = 2;
        private int maxRounds = 10;
        private long roundInterval = 1000L;
        private double exchangeFailureProb = 0.0;
        private double exchangeCompressionFactor = 1.0;
        private LoadProfile loadProfile = null;
        private long paramCount = 61_196L; // LeNet-5 (4-class), matching Pass1Main's default
        private boolean dynamic = false;
        private double pLink = 1.0;
        private long seed = 42L;

        /**
         * Starts a builder for the given graph and node set.
         *
         * @param topology the communication graph.
         * @param nodes the gossip nodes (index = node id).
         */
        public Builder(FLTopology topology, List<FLGossipNode> nodes) {
            this.topology = topology;
            this.nodes = nodes;
        }

        public Builder policy(PeerSelectionPolicy p) {
            this.policy = p;
            return this;
        }

        public Builder mergeRule(MergeRule m) {
            this.mergeRule = m;
            return this;
        }

        public Builder k(int peersPerRound) {
            this.k = peersPerRound;
            return this;
        }

        public Builder rounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder roundInterval(long ticks) {
            this.roundInterval = ticks;
            return this;
        }

        public Builder exchangeFailureProb(double p) {
            this.exchangeFailureProb = p;
            return this;
        }

        public Builder compressionFactor(double f) {
            this.exchangeCompressionFactor = f;
            return this;
        }

        public Builder loadProfile(LoadProfile lp) {
            this.loadProfile = lp;
            return this;
        }

        public Builder paramCount(long params) {
            this.paramCount = params;
            return this;
        }

        /** Enables per-round edge toggling with the given link-up probability. */
        public Builder dynamic(double pLink) {
            this.dynamic = true;
            this.pLink = pLink;
            return this;
        }

        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /** Constructs (and thereby subscribes) the orchestrator. */
        public FLGossipOrchestrator build() {
            return new FLGossipOrchestrator(topology, nodes, policy, mergeRule, k, maxRounds,
                    roundInterval, exchangeFailureProb, exchangeCompressionFactor, loadProfile,
                    paramCount, dynamic, pLink, seed);
        }
    }

    /** Sets the telemetry CSV output path (written when the run finishes). */
    public void setExportPath(Path path) {
        this.exportPath = path;
    }

    /** Sets a callback invoked once all rounds complete. */
    public void setFinishedCallback(Runnable r) {
        this.finishedCallback = r;
    }

    /**
     * Enables TRACE (Pass-3) replay: the trace's recorded peer sets, payload
     * sizes, and signatures drive the physics (read-first, D4); the policy is
     * still invoked, but only to compute the reported {@code selection_agreement_rate}.
     * Must be called before the first round opens.
     *
     * <p>Signature convention: {@code provider.signature(node, t)} is the model
     * <i>entering</i> round {@code t} (slot 0 = initial model), and the
     * end-of-round-{@code t} model used for TID is slot {@code t+1}. The harness
     * writes {@code T+1} signature slots per node to match.</p>
     *
     * @param provider the trace provider.
     * @param agreementMode {@code "warn"} (default; a sub-100% rate is a measurement)
     *                      or {@code "abort"} (the determinism CI canary — the
     *                      contract must hold exactly).
     */
    public void enableTraceReplay(LearningProvider provider, String agreementMode) {
        // The Pass-2 harness merges over {i} ∪ P_i unconditionally — it has no
        // notion of a model lost in transit — whereas mergeNode() here merges
        // only peers that actually arrived. The two agree exactly when no
        // exchange can fail. Allowing in-transit loss under replay would
        // silently desynchronise the merge member sets (and hence the weights,
        // signatures, and TID) from the trace, so reject it up front rather
        // than produce a quietly wrong run.
        if (exchangeFailureProb > 0.0) {
            throw new IllegalStateException(
                    "trace replay requires lossless exchange (exchangeFailureProb="
                    + exchangeFailureProb + "): the Pass-2 harness merges over the full "
                    + "selected peer set, so a dropped model would desynchronise the "
                    + "merge from the trace. Model dropout belongs in SYNTHETIC mode, or "
                    + "the harness must be extended to consume a per-round loss schedule.");
        }
        this.learningProvider = provider;
        this.agreementMode = (agreementMode == null) ? "warn" : agreementMode;
    }

    /** Attaches a {@link TidRecorder}, recorded at each round barrier (§8.3). */
    public void setTidRecorder(TidRecorder recorder) {
        this.tidRecorder = recorder;
    }

    /**
     * Attaches the calibrated analytic compute-delay model and the per-node
     * device tiers it keys on (§8.4). Without this the TRACE replay falls back
     * to the harness's measured host wall-clock time, which makes the simulated
     * round duration a property of the calibration machine rather than of the
     * modelled fog device — the device tiers would then have no effect on round
     * duration or barrier idle time.
     *
     * @param model the delay model (never null; use {@link ComputeDelayModel#identity()}).
     * @param tiers per-node tier labels (index = node id).
     */
    public void setComputeDelayModel(ComputeDelayModel model, String[] tiers) {
        this.computeDelay = model == null ? ComputeDelayModel.identity() : model;
        this.nodeTier = tiers == null ? null : tiers.clone();
    }

    /** Whether a per-tier compute-delay fit is driving the replay's train delay. */
    public boolean hasCalibratedDelay() {
        return computeDelay.isCalibrated() && nodeTier != null;
    }

    private boolean traceMode() {
        return learningProvider != null
                && learningProvider.source() == hu.u_szeged.inf.fog.simulator.fl.FLLearningSource.TRACE;
    }

    /** The Java↔Python peer-selection agreement rate (§8.4), or 1.0 if nothing compared. */
    public double selectionAgreementRate() {
        return agreementTotal == 0 ? 1.0 : (double) agreementMatches / agreementTotal;
    }

    /** The accuracy replayed from the trace for {@code (node, round)} (TRACE mode), or NaN. */
    public double replayedAccuracy(int node, int round) {
        Double v = replayedAcc.get(((long) node << 32) | (round & 0xFFFFFFFFL));
        return v == null ? Double.NaN : v;
    }

    /** Synthetic payload size in bytes for one model exchange. */
    public long payloadBytes() {
        return (long) Math.ceil(paramCount * 4.0 * exchangeCompressionFactor);
    }

    // ------------------------------------------------------------------
    // Timed entry: open a round.
    // ------------------------------------------------------------------
    @Override
    public void tick(long fires) {
        unsubscribe(); // the round proceeds via exchange + barrier events
        openRound(currentRound);
    }

    private void openRound(int round) {
        roundOpenTick = Timed.getFireCount();
        peerSets.clear();
        dropoutsThisRound = 0;
        roundDelays = new ArrayList<>();
        java.util.Arrays.fill(exchangeSpan, 0L);
        for (FLGossipNode node : nodes) {
            node.beginRound();
        }
        if (dynamic) {
            topology.applyDynamicRound(round, seed, pLink);
        }

        // TRACE mode: adopt the recorded entry-model signatures (slot = round) before
        // selection, so the cached divergence and re-derivation use the same models the
        // harness used (read-first, D4).
        if (traceMode()) {
            for (FLGossipNode node : nodes) {
                node.setSignature(learningProvider.signature(node.nodeId(), round));
            }
        }

        // SELECTING (Alg. 1 lines 1–3): per node, score neighbours and pick top-k.
        long synthPayload = payloadBytes();
        for (FLGossipNode node : nodes) {
            int i = node.nodeId();
            List<Integer> neighbours = topology.neighbours(i);
            long payloadForCost = traceMode() ? learningProvider.outcome(i, round).payloadBytes : synthPayload;
            GossipCostView cv = new GossipCostView(topology, node, loadProfile, round, payloadForCost);
            SplitMix64 rng = SimRandom.derive(seed, round, i);
            List<Integer> derived = policy.selectPeers(i, round, maxRounds, neighbours, cv, rng, k);
            if (traceMode()) {
                // Recorded peers are authoritative; the derivation is only the agreement read-out.
                List<Integer> recorded = toList(learningProvider.outcome(i, round).peers);
                recordAgreement(derived, recorded);
                peerSets.put(i, recorded);
            } else {
                peerSets.put(i, derived);
            }
        }

        // EXCHANGING (Alg. 1 line 4): schedule the de-duplicated directed transfers.
        List<int[]> transfers = FLPeerExchangeEvent.planDirectedTransfers(peerSets);
        pending = transfers.size();
        if (pending == 0) {
            mergeTrainBarrier(round);
            return;
        }
        for (int[] t : transfers) {
            int s = t[0];
            int d = t[1];
            Edge e = topology.edge(s, d);
            long payload = traceMode() ? learningProvider.outcome(s, round).payloadBytes : synthPayload;
            long delay = e.latencyTicks + (long) Math.ceil((double) payload / (double) e.bandwidthBytesPerTick);
            FLGossipNode src = nodes.get(s);
            FLGossipNode dst = nodes.get(d);
            exchangeSpan[s] = Math.max(exchangeSpan[s], delay);
            exchangeSpan[d] = Math.max(exchangeSpan[d], delay);
            exchangeIndex.put(exKey(round, s, d), perExchange.size());
            perExchange.add(new long[] { round, s, d, payload, delay, 0L });
            roundDelays.add(delay);
            new FLPeerExchangeEvent(delay, round, src, dst, src.signature(), src.sampleCount(),
                    payload, e.latencyTicks, exchangeFailureProb, true, this);
        }
    }

    private static long exKey(int round, int src, int dst) {
        return ((long) round << 40) | ((long) src << 20) | (dst & 0xFFFFF);
    }

    private static List<Integer> toList(int[] arr) {
        List<Integer> out = new ArrayList<>(arr.length);
        for (int v : arr) {
            out.add(v);
        }
        return out;
    }

    /**
     * Compares a re-derived peer set against the trace's recorded one for the
     * {@code selection_agreement_rate} (§8.4 / P3.5). Sets are compared by
     * membership; in {@code abort} mode a mismatch throws (the determinism
     * canary), otherwise it is merely counted and reported.
     */
    private void recordAgreement(List<Integer> derived, List<Integer> recorded) {
        agreementTotal++;
        java.util.Set<Integer> a = new java.util.TreeSet<>(derived);
        java.util.Set<Integer> b = new java.util.TreeSet<>(recorded);
        if (a.equals(b)) {
            agreementMatches++;
        } else if ("abort".equals(agreementMode)) {
            throw new IllegalStateException("selection-agreement mismatch (abort mode): derived="
                    + a + " recorded=" + b);
        }
    }

    // ------------------------------------------------------------------
    // Exchange callbacks.
    // ------------------------------------------------------------------
    @Override
    public void onArrived(FLPeerExchangeEvent ev) {
        // NB: do NOT forceSample the energy meters here — sampling while other
        // transfers are still in flight hits a transient unbounded spreader load
        // in DISSECT-CF's power model ("out of range load: Infinity"). Energy is
        // captured at the round barrier (closeRound), which is quiescent.
        nodes.get(ev.srcNode()).addUploadBytes(ev.payloadBytes());
        nodes.get(ev.dstNode()).addDownloadBytes(ev.payloadBytes());
        if (--pending == 0) {
            mergeTrainBarrier(ev.round());
        }
    }

    @Override
    public void onFailed(FLPeerExchangeEvent ev) {
        dropoutsThisRound++;
        Integer idx = exchangeIndex.get(exKey(ev.round(), ev.srcNode(), ev.dstNode()));
        if (idx != null) {
            perExchange.get(idx)[5] = 1L; // mark failed
        }
        if (--pending == 0) {
            mergeTrainBarrier(ev.round());
        }
    }

    // ------------------------------------------------------------------
    // MERGING (line 5) + TRAINING (line 6) + BARRIER (line 7).
    // ------------------------------------------------------------------
    private void mergeTrainBarrier(int round) {
        for (FLGossipNode node : nodes) {
            mergeNode(node, round);
        }

        // TRAINING: round duration set by the slowest node. In TRACE mode the train
        // delay comes from the recorded train_time_ms and the post-round model is the
        // recorded end-of-round signature (slot round+1); in SYNTHETIC mode both are
        // computed deterministically.
        long trainSpanMax = 1L;
        for (FLGossipNode node : nodes) {
            long trainTicks = traceMode()
                    ? tracedTrainTicks(node.nodeId(), round)
                    : syntheticTrainTicks(node);
            node.addTrainTicks(trainTicks);
            node.addBusyTicks(exchangeSpan[node.nodeId()]); // exchange span + train span = busy
            trainSpanMax = Math.max(trainSpanMax, trainTicks);
            if (traceMode()) {
                // Replay the recorded HELD-OUT accuracy (read-first) and adopt the
                // recorded end-of-round model (slot round+1). The local-shard `acc`
                // column is a training diagnostic and must not be surfaced as the
                // evaluation result.
                replayedAcc.put(((long) node.nodeId() << 32) | (round & 0xFFFFFFFFL),
                        learningProvider.outcome(node.nodeId(), round).heldoutAcc);
                node.setSignature(learningProvider.signature(node.nodeId(), round + 1));
            } else {
                applySyntheticTraining(node, round);
            }
        }

        // BARRIER after the slowest node finishes training.
        final int closingRound = round;
        new DeferredEvent(trainSpanMax) {
            @Override
            protected void eventAction() {
                closeRound(closingRound);
            }
        };
    }

    /** Drift-suppressed (or configured) merge over {@code {i} ∪ received(P_i)} on signatures. */
    private void mergeNode(FLGossipNode node, int round) {
        int i = node.nodeId();
        List<Integer> selected = peerSets.get(i);
        // Members: self + selected peers whose model actually arrived this round.
        List<Integer> present = new ArrayList<>();
        for (int j : selected) {
            if (node.hasReceived(j)) {
                present.add(j);
            }
        }
        int m = present.size() + 1;
        double[] divergence = new double[m];
        int[] sampleCount = new int[m];
        int[] degree = new int[m];
        float[][] sigs = new float[m][];

        divergence[0] = 0.0;
        sampleCount[0] = node.sampleCount();
        degree[0] = topology.degree(i);
        sigs[0] = node.signature();

        for (int idx = 0; idx < present.size(); idx++) {
            int j = present.get(idx);
            ReceivedModel rm = node.received(j);
            int slot = idx + 1;
            divergence[slot] = FLGossipNode.l2(node.signature(), rm.signature);
            sampleCount[slot] = rm.sampleCount;
            degree[slot] = topology.degree(j);
            sigs[slot] = rm.signature;
        }

        double[] w;
        if (traceMode()) {
            // Replay on signatures using the recorded merge weights (D5). Weights
            // are aligned [self, recorded-peers…]. enableTraceReplay() rejects a
            // non-zero failure probability, so every selected peer is present and
            // the alignment always holds; a length mismatch therefore means the
            // trace and the live run disagree on the peer set, which must fail
            // loudly rather than be papered over by recomputing the weights.
            double[] recorded = learningProvider.outcome(i, round).mergeWeights;
            if (recorded.length != m) {
                throw new IllegalStateException("merge-weight arity mismatch at node " + i
                        + " round " + round + ": trace recorded " + recorded.length
                        + " members, replay assembled " + m
                        + " (selected=" + selected + ", arrived=" + present + ")");
            }
            w = recorded;
        } else {
            w = mergeRule.weights(divergence, sampleCount, degree);
        }
        int dim = node.signature().length;
        float[] merged = new float[dim];
        for (int slot = 0; slot < m; slot++) {
            float[] s = sigs[slot];
            double wm = w[slot];
            for (int dd = 0; dd < dim; dd++) {
                merged[dd] += (float) (wm * s[dd]);
            }
        }
        node.setSignature(merged);

        // Cache update at merge completion, only for selected peers that arrived (P0.3b).
        for (int j : present) {
            node.updateCacheAtMerge(j, node.received(j).signature, round);
        }
    }

    /**
     * TRACE-mode local-training delay (ticks). With a calibrated
     * {@link ComputeDelayModel} the delay is the per-tier affine prediction from
     * the node's shard size, so the modelled device tier — not the calibration
     * host — sets the round duration. Without one it degrades to the harness's
     * measured wall-clock time, which is reported as such rather than as a
     * device model.
     */
    private long tracedTrainTicks(int nodeId, int round) {
        var outcome = learningProvider.outcome(nodeId, round);
        if (hasCalibratedDelay()) {
            return computeDelay.ticks(nodeTier[nodeId], outcome.nSamples, outcome.trainTimeMs);
        }
        return Math.max(1L, (long) Math.ceil(outcome.trainTimeMs));
    }

    /** Deterministic synthetic compute delay (ticks), mirroring LocalTrainingEvent's basis. */
    private long syntheticTrainTicks(FLGossipNode node) {
        double work = node.device().getFileSize() * node.device().getInstructionPerByte();
        double tput = node.device().getThroughput();
        long ticks = (long) Math.ceil(work / Math.max(1e-9, tput));
        return Math.max(1L, ticks);
    }

    /** Deterministic synthetic signature perturbation (synthetic mode only). */
    private void applySyntheticTraining(FLGossipNode node, int round) {
        SplitMix64 rng = SimRandom.derive(seed * TRAIN_STREAM_KEY_MULT + TRAIN_STREAM_KEY_ADD, round, node.nodeId());
        float[] sig = node.signature().clone();
        for (int d = 0; d < sig.length; d++) {
            sig[d] += (float) ((rng.nextDouble() - 0.5) * 0.001);
        }
        node.setSignature(sig);
    }

    private void closeRound(int round) {
        long roundDuration = Timed.getFireCount() - roundOpenTick;
        for (FLGossipNode node : nodes) {
            long busy = node.busyTicks();
            long idle = Math.max(0L, roundDuration - busy);
            roundRows.add(new long[] {
                    round, node.nodeId(), node.exchangedBytesUl(), node.exchangedBytesDl(),
                    busy, node.trainTicks(), roundDuration, idle });
            peerCol.add(joinAscending(peerSets.get(node.nodeId())));
        }

        // Per-round energy snapshot at the (quiescent) barrier — all transfers and
        // training delays have completed, so the power model is in a steady state and
        // forceSample cannot hit a transient unbounded load (P5.1 round-barrier snapshot).
        if (lastEnergyMj == null) {
            lastEnergyMj = new double[nodes.size()];
        }
        for (FLGossipNode node : nodes) {
            double energyMj = Double.NaN;
            if (node.energyCollector() != null) {
                try {
                    double cur = node.energyCollector().forceSample();
                    energyMj = cur - lastEnergyMj[node.nodeId()];
                    lastEnergyMj[node.nodeId()] = cur;
                } catch (RuntimeException ex) {
                    // A metering transfer may still be in flight if the link bandwidth is
                    // slower than the analytic round (DISSECT-CF reports a transient "out of
                    // range load"); record NaN for this cell rather than aborting the run.
                    if (energyWarned == null) {
                        System.out.println("FLGossipOrchestrator: per-round energy sample skipped (transient load) – "
                                + ex.getMessage());
                        energyWarned = Boolean.TRUE;
                    }
                }
            }
            perRoundEnergyRows.add(new double[] {
                    round, node.nodeId(), node.exchangedBytesUl(), node.exchangedBytesDl(), energyMj });
        }

        // Per-round gossip summary: p50/p95 exchange delay + dropouts.
        long p50 = percentile(roundDelays, 50);
        long p95 = percentile(roundDelays, 95);
        gossipRoundRows.add(new long[] { round, roundDuration, p50, p95, dropoutsThisRound });

        // TID at the round barrier (§8.3): signatures are now the end-of-round models.
        if (tidRecorder != null) {
            tidRecorder.recordRound(round, nodes);
        }

        if (round + 1 < maxRounds) {
            currentRound = round + 1;
            subscribe(roundInterval); // next tick opens the next round
        } else {
            finish();
        }
    }

    private void finish() {
        if (exportPath != null) {
            try {
                writeTelemetry(exportPath);
            } catch (IOException e) {
                System.out.println("FLGossipOrchestrator: telemetry write failed – " + e.getMessage());
            }
        }
        if (finishedCallback != null) {
            finishedCallback.run();
        }
    }

    private static String joinAscending(List<Integer> peers) {
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < peers.size(); idx++) {
            if (idx > 0) {
                sb.append(';');
            }
            sb.append(peers.get(idx));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Telemetry export.
    // ------------------------------------------------------------------

    /** The telemetry as a deterministic CSV string (header + one row per round-node). */
    public String telemetryCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("round,node,peers,ul_bytes,dl_bytes,busy_ticks,train_ticks,round_duration_ticks,idle_ticks\n");
        for (int r = 0; r < roundRows.size(); r++) {
            long[] row = roundRows.get(r);
            sb.append(row[0]).append(',')
              .append(row[1]).append(',')
              .append(peerCol.get(r)).append(',')
              .append(row[2]).append(',')
              .append(row[3]).append(',')
              .append(row[4]).append(',')
              .append(row[5]).append(',')
              .append(row[6]).append(',')
              .append(row[7]).append('\n');
        }
        return sb.toString();
    }

    private void writeTelemetry(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(telemetryCsv());
        }
    }

    // ------------------------------------------------------------------
    // Accessors (testing / telemetry).
    // ------------------------------------------------------------------

    public int currentRound() {
        return currentRound;
    }

    public Map<Integer, List<Integer>> peerSets() {
        return peerSets;
    }

    public List<FLGossipNode> nodes() {
        return nodes;
    }

    /** Per-round node-energy rows {@code {round, node, ulBytes, dlBytes, energyMj}} (feeds P5.1). */
    public List<double[]> perRoundEnergyRows() {
        return perRoundEnergyRows;
    }

    // ------------------------------------------------------------------
    // Run identity / metadata (P5.1 run_metadata.json).
    // ------------------------------------------------------------------

    public void setScenarioId(String id) {
        this.scenarioId = id;
    }

    /** λ̄₂ and union-λ₂ metadata for dynamic graphs (written to run_metadata). */
    public void setLambda2Metadata(Double expected, Double union) {
        this.lambda2Expected = expected;
        this.lambda2Union = union;
    }

    // ------------------------------------------------------------------
    // P5.1 Pass-3 export bundle.
    // ------------------------------------------------------------------

    private static long percentile(List<Long> values, int pct) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        java.util.Collections.sort(sorted);
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }

    /**
     * Writes the full Pass-3 artefact set into {@code dir} (§9 / P5.1):
     * {@code per_exchange.csv}, {@code idle_time.csv}, {@code gossip_round.csv},
     * {@code energy_per_round.csv}, {@code run_metadata.json}, the gossip
     * telemetry, and (if attached) the TID
     * time series. Per-exchange energy is apportioned from the per-round
     * barrier snapshot proportionally to each endpoint's exchanged bytes (a
     * mid-round meter sample is unsafe — see {@code onArrived}); values are
     * exported in millijoules.
     */
    public void exportAll(Path dir) throws IOException {
        Files.createDirectories(dir);

        // Per-node per-round energy lookup (mJ) for apportionment.
        Map<Long, Double> nodeRoundEnergy = new LinkedHashMap<>();
        Map<Long, Long> nodeRoundBytes = new LinkedHashMap<>();
        for (double[] e : perRoundEnergyRows) {
            long key = ((long) e[0] << 20) | ((long) e[1] & 0xFFFFF); // round,node
            nodeRoundEnergy.put(key, e[4]);
            nodeRoundBytes.put(key, (long) e[2] + (long) e[3]); // ul+dl
        }

        // per_exchange.csv
        StringBuilder px = new StringBuilder("round,src,dst,bytes,delay_ticks,src_mj,dst_mj,failed\n");
        for (long[] r : perExchange) {
            int round = (int) r[0];
            int src = (int) r[1];
            int dst = (int) r[2];
            long bytes = r[3];
            double srcMj = apportion(nodeRoundEnergy, nodeRoundBytes, round, src, bytes);
            double dstMj = apportion(nodeRoundEnergy, nodeRoundBytes, round, dst, bytes);
            px.append(round).append(',').append(src).append(',').append(dst).append(',')
              .append(bytes).append(',').append(r[4]).append(',')
              .append(fmt(srcMj)).append(',').append(fmt(dstMj)).append(',').append(r[5]).append('\n');
        }
        Files.write(dir.resolve("per_exchange.csv"), px.toString().getBytes(StandardCharsets.UTF_8));

        // idle_time.csv
        StringBuilder idle = new StringBuilder("round,node,idle_ticks,busy_ticks\n");
        for (long[] row : roundRows) {
            idle.append(row[0]).append(',').append(row[1]).append(',')
                .append(row[7]).append(',').append(row[4]).append('\n');
        }
        Files.write(dir.resolve("idle_time.csv"), idle.toString().getBytes(StandardCharsets.UTF_8));

        // gossip_round.csv
        StringBuilder gr = new StringBuilder("round,round_duration_ticks,p50_delay_ticks,p95_delay_ticks,dropouts\n");
        for (long[] row : gossipRoundRows) {
            gr.append(row[0]).append(',').append(row[1]).append(',')
              .append(row[2]).append(',').append(row[3]).append(',').append(row[4]).append('\n');
        }
        Files.write(dir.resolve("gossip_round.csv"), gr.toString().getBytes(StandardCharsets.UTF_8));

        // gossip telemetry (per round-node) — the richer view.
        Files.write(dir.resolve("gossip_telemetry.csv"), telemetryCsv().getBytes(StandardCharsets.UTF_8));

        // energy_per_round.csv — the per-node barrier snapshots (mJ), same rows
        // the apportionment above is derived from.
        StringBuilder en = new StringBuilder("round,node,ul_bytes,dl_bytes,energy_mj\n");
        for (double[] e : perRoundEnergyRows) {
            en.append((long) e[0]).append(',').append((long) e[1]).append(',')
              .append((long) e[2]).append(',').append((long) e[3]).append(',')
              .append(fmt(e[4])).append('\n');
        }
        Files.write(dir.resolve("energy_per_round.csv"), en.toString().getBytes(StandardCharsets.UTF_8));

        // run_metadata.json
        writeRunMetadata(dir.resolve("run_metadata.json"));

        // TID time series (if instrumented).
        if (tidRecorder != null) {
            tidRecorder.write(dir.resolve("tid_timeseries.csv"));
        }
    }

    private static double apportion(Map<Long, Double> energy, Map<Long, Long> bytes,
                                    int round, int node, long exchangeBytes) {
        long key = ((long) round << 20) | ((long) node & 0xFFFFF);
        Double e = energy.get(key);
        Long total = bytes.get(key);
        if (e == null || e.isNaN() || total == null || total == 0L) {
            return 0.0;
        }
        return e * ((double) exchangeBytes / (double) total);
    }

    private void writeRunMetadata(Path out) throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schema_version", 1);
        meta.put("scenario_id", scenarioId);
        meta.put("seed", seed);
        meta.put("n", nodes.size());
        meta.put("rounds", maxRounds);
        meta.put("k", k);
        meta.put("policy", policy.id());
        meta.put("merge_rule", mergeRule.id());
        meta.put("topology_hash", topology.topologyHash());
        meta.put("lambda2", topology.lambda2());
        meta.put("lambda2_expected", lambda2Expected);
        meta.put("lambda2_union", lambda2Union);
        meta.put("dynamic", dynamic);
        meta.put("p_link", dynamic ? pLink : null);
        meta.put("learning_source", traceMode() ? "TRACE" : "SYNTHETIC");
        meta.put("selection_agreement_rate", selectionAgreementRate());
        meta.put("payload_bytes", payloadBytes());
        meta.put("param_count", paramCount);
        // Provenance of the simulated local-training delay: a calibrated per-tier
        // fit, or the raw measured host time (which is not a device model).
        meta.put("compute_delay_source",
                hasCalibratedDelay() ? "CALIBRATED_TIER_FIT" : "MEASURED_HOST_WALLCLOCK");
        meta.put("compute_delay_instrument", computeDelay.instrument());
        meta.put("config_hash", topology.topologyHash().substring(0, 12) + "-" + seed + "-k" + k);
        meta.put("code_git_hash", gitHash());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out.toFile(), meta);
    }

    private static String gitHash() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                p.waitFor();
                return (line == null || p.exitValue() != 0) ? "unknown" : line.trim();
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Locale-stable formatting helper for any future float columns. */
    static String fmt(double v) {
        return String.format(Locale.US, "%.6f", v);
    }
}
