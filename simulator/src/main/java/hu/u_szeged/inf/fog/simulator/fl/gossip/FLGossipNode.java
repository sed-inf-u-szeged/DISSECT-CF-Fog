package hu.u_szeged.inf.fog.simulator.fl.gossip;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.u_szeged.inf.fog.simulator.fl.FLEdgeDevice;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollectorFL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A decentralized gossip node (§8.1 Gossip Orchestrator). Wraps an
 * {@link FLEdgeDevice} (reusing its PM/VM attachment, repository,
 * {@code instructionPerByte}, {@code throughput}, and DP fields) and holds the
 * node-local state a gossip round needs: the current model <b>signature</b>, a
 * <b>cache</b> of the most recent signature seen from each peer (with its
 * staleness age), a per-round inbox of arrived peer models, and per-round
 * telemetry counters. No node holds any global federation state.
 *
 * <h2>Cache / staleness contract (P0.3b)</h2>
 * The cache entry for a peer {@code j} is updated <b>only at merge completion</b>
 * and <b>only for selected peers</b> {@code j ∈ P_i}, setting
 * {@code signature ← w_j(t)} and {@code lastSeenRound ← t}. It is never updated
 * on scheduling an exchange, on arrival before merge, or for unselected
 * neighbours. This pins the cached selection divergence feeding the next round
 * identically to the Python harness.
 *
 * <h2>Energy registration (Risk R1)</h2>
 * The constructor ensures the device's {@link PhysicalMachine} is registered
 * with {@link EnergyDataCollectorFL}; a per-PM meter covers the PM plus its
 * repository's network in/out spreaders, so device↔device peer exchanges are
 * metered at both endpoints (verified by {@code FLPeerExchangeEnergyTest}).
 */
public final class FLGossipNode {

    /** A cached peer signature with the round it was observed. */
    public static final class CachedPeer {
        public float[] signature;
        public int lastSeenRound;

        CachedPeer(float[] signature, int lastSeenRound) {
            this.signature = signature;
            this.lastSeenRound = lastSeenRound;
        }
    }

    /** A model received from a peer during the current round's exchange phase. */
    public static final class ReceivedModel {
        public final int fromPeer;
        public final float[] signature;
        public final int sampleCount;
        public final long payloadBytes;

        ReceivedModel(int fromPeer, float[] signature, int sampleCount, long payloadBytes) {
            this.fromPeer = fromPeer;
            this.signature = signature;
            this.sampleCount = sampleCount;
            this.payloadBytes = payloadBytes;
        }
    }

    private final int nodeId;
    private final FLEdgeDevice device;

    private float[] signature;
    private int sampleCount;

    private final Map<Integer, CachedPeer> cache = new HashMap<>();
    private final Map<Integer, ReceivedModel> inbox = new LinkedHashMap<>();

    // Per-round telemetry counters.
    private long busyTicks;
    private long exchangedBytesUl;
    private long exchangedBytesDl;
    private long trainTicks;

    /**
     * Wraps a device as gossip node {@code nodeId} and registers its PM with
     * the energy collector (R1).
     *
     * @param nodeId stable node id ({@code 0..n-1}).
     * @param device the backing FL edge device.
     * @param signature initial model signature (length d); copied.
     * @param sampleCount local sample count {@code n_i} ({@code > 0}).
     */
    public FLGossipNode(int nodeId, FLEdgeDevice device, float[] signature, int sampleCount) {
        if (device == null) {
            throw new IllegalArgumentException("device must not be null");
        }
        if (signature == null || signature.length == 0) {
            throw new IllegalArgumentException("signature must be non-empty");
        }
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("sampleCount must be > 0");
        }
        this.nodeId = nodeId;
        this.device = device;
        this.signature = signature.clone();
        this.sampleCount = sampleCount;
        ensurePmRegistered();
    }

    /** R1: register the device PM with the energy collector if not already. */
    private void ensurePmRegistered() {
        PhysicalMachine pm = device.getLocalMachine();
        if (pm != null && EnergyDataCollectorFL.getEnergyCollector(pm) == null) {
            new EnergyDataCollectorFL("gossip-node-" + nodeId, pm, true);
        }
    }

    // ------------------------------------------------------------------
    // Identity / wiring
    // ------------------------------------------------------------------

    public int nodeId() {
        return nodeId;
    }

    public FLEdgeDevice device() {
        return device;
    }

    /** The device's repository (source/target of peer-exchange transfers). */
    public Repository repository() {
        PhysicalMachine pm = device.getLocalMachine();
        return pm == null ? null : pm.localDisk;
    }

    /** The energy collector for this node's PM (may be null if PM is null). */
    public EnergyDataCollectorFL energyCollector() {
        PhysicalMachine pm = device.getLocalMachine();
        return pm == null ? null : EnergyDataCollectorFL.getEnergyCollector(pm);
    }

    // ------------------------------------------------------------------
    // Model state
    // ------------------------------------------------------------------

    /** The current model signature (live reference; treat as read-only). */
    public float[] signature() {
        return signature;
    }

    /** Replaces the current model signature (defensive copy). */
    public void setSignature(float[] sig) {
        this.signature = sig.clone();
    }

    public int sampleCount() {
        return sampleCount;
    }

    // ------------------------------------------------------------------
    // Peer-signature cache (P0.3b contract)
    // ------------------------------------------------------------------

    public boolean hasCached(int peerId) {
        return cache.containsKey(peerId);
    }

    /** Cached signature for {@code peerId}, or null if never observed. */
    public float[] cachedSignature(int peerId) {
        CachedPeer c = cache.get(peerId);
        return c == null ? null : c.signature;
    }

    /**
     * Updates the cache for a <b>selected</b> peer at <b>merge completion</b>
     * (the only legal update point — P0.3b). Stores a copy of {@code w_j(t)} and
     * sets {@code lastSeenRound = round}.
     */
    public void updateCacheAtMerge(int peerId, float[] peerSignature, int round) {
        CachedPeer existing = cache.get(peerId);
        if (existing == null) {
            cache.put(peerId, new CachedPeer(peerSignature.clone(), round));
        } else {
            existing.signature = peerSignature.clone();
            existing.lastSeenRound = round;
        }
    }

    // ------------------------------------------------------------------
    // Per-round inbox
    // ------------------------------------------------------------------

    /** Delivers a peer model into this round's inbox (called on exchange arrival). */
    public void deliver(ReceivedModel model) {
        inbox.put(model.fromPeer, model);
    }

    public ReceivedModel received(int peerId) {
        return inbox.get(peerId);
    }

    public boolean hasReceived(int peerId) {
        return inbox.containsKey(peerId);
    }

    // ------------------------------------------------------------------
    // Per-round telemetry
    // ------------------------------------------------------------------

    /** Resets per-round counters and inbox; call at round open. */
    public void beginRound() {
        busyTicks = 0;
        exchangedBytesUl = 0;
        exchangedBytesDl = 0;
        trainTicks = 0;
        inbox.clear();
    }

    public void addBusyTicks(long ticks) {
        busyTicks += ticks;
    }

    public void addUploadBytes(long bytes) {
        exchangedBytesUl += bytes;
    }

    public void addDownloadBytes(long bytes) {
        exchangedBytesDl += bytes;
    }

    public void addTrainTicks(long ticks) {
        trainTicks += ticks;
        busyTicks += ticks;
    }

    public long busyTicks() {
        return busyTicks;
    }

    public long exchangedBytesUl() {
        return exchangedBytesUl;
    }

    public long exchangedBytesDl() {
        return exchangedBytesDl;
    }

    public long trainTicks() {
        return trainTicks;
    }

    // ------------------------------------------------------------------
    // Signature math
    // ------------------------------------------------------------------

    /** Euclidean (ℓ₂) distance between two equal-length signatures, in float32. */
    public static double l2(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("signature length mismatch: " + a.length + " vs " + b.length);
        }
        double s = 0.0;
        for (int i = 0; i < a.length; i++) {
            double d = (double) a[i] - (double) b[i];
            s += d * d;
        }
        return Math.sqrt(s);
    }
}
