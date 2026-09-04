package hu.u_szeged.inf.fog.simulator.fl.topology;

import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import hu.u_szeged.inf.fog.simulator.util.SplitMix64;

/**
 * Derives <b>heterogeneous</b> per-link costs {@code (L_ij, B_ij)} from each
 * endpoint's <i>access link</i> plus great-circle propagation (§8.1).
 *
 * <h2>Why access links, not geography alone</h2>
 * {@link GeoCostModel} converts distance to latency at ≈5&nbsp;µs/km. At fog
 * scale (metro distances, tens of km) propagation is genuinely sub-millisecond,
 * so with a 1&nbsp;ms tick a purely geographic model rounds every intra-region
 * link to the same value and the {@code L̃}/{@code B̃} terms of Eq. score
 * degenerate. Physically, the dominant edge-side cost is the <i>access
 * network</i> (last-mile queueing, scheduling, and capacity), which differs per
 * device tier. This model therefore composes both:
 *
 * <pre>
 *   L_ij = access_L(i) + access_L(j) + propagation(dist(i,j))
 *   B_ij = min( access_B(i), access_B(j) )          // the bottleneck link
 * </pre>
 *
 * Latency accumulates over both access legs (a packet traverses the sender's
 * and the receiver's last mile); capacity is set by the slower of the two.
 *
 * <h2>Per-node draws</h2>
 * Each node's access latency and bandwidth are drawn once, deterministically,
 * from its tier's band using a <b>dedicated</b> stream
 * {@code derive(seed·37+11, 0, node)} — two {@code nextDouble()} draws per node,
 * latency first — so link costs can never alias with the peer-selection,
 * tie-break, dynamic-toggle, or background-load streams (§2.1 of
 * docs/REPRODUCIBILITY.md). Costs are computed once in Pass 1 and
 * <i>exported</i> in {@code system_trace.json}; the Python harness reads them
 * and never regenerates them, so this class has no cross-language contract.
 *
 * <p>All methods are pure and deterministic in {@code (seed, node, tier)}.</p>
 */
public final class LinkCostModel {

    /** Stream-key multiplier for the dedicated link-cost stream. */
    static final long STREAM_MULT = 37L;
    /** Stream-key addend for the dedicated link-cost stream. */
    static final long STREAM_ADD = 11L;

    /**
     * An access-link band for one device tier: latency and bandwidth are drawn
     * uniformly from {@code [min, max]}.
     */
    public static final class Tier {
        /** Tier name, matching {@code SystemTrace.NodeJson.profile}. */
        public final String name;
        public final long minLatencyTicks;
        public final long maxLatencyTicks;
        public final long minBandwidthBytesPerTick;
        public final long maxBandwidthBytesPerTick;

        /**
         * Defines a tier band.
         *
         * @param name tier name (e.g. {@code near_edge}).
         * @param minLatencyTicks lower access-latency bound (ticks, {@code >= 0}).
         * @param maxLatencyTicks upper access-latency bound ({@code >= min}).
         * @param minBandwidthBytesPerTick lower capacity bound ({@code > 0}).
         * @param maxBandwidthBytesPerTick upper capacity bound ({@code >= min}).
         */
        public Tier(String name, long minLatencyTicks, long maxLatencyTicks,
                    long minBandwidthBytesPerTick, long maxBandwidthBytesPerTick) {
            if (minLatencyTicks < 0 || maxLatencyTicks < minLatencyTicks) {
                throw new IllegalArgumentException("invalid latency band for tier " + name);
            }
            if (minBandwidthBytesPerTick <= 0 || maxBandwidthBytesPerTick < minBandwidthBytesPerTick) {
                throw new IllegalArgumentException("invalid bandwidth band for tier " + name);
            }
            this.name = name;
            this.minLatencyTicks = minLatencyTicks;
            this.maxLatencyTicks = maxLatencyTicks;
            this.minBandwidthBytesPerTick = minBandwidthBytesPerTick;
            this.maxBandwidthBytesPerTick = maxBandwidthBytesPerTick;
        }

        /**
         * The same tier with its capacity band multiplied by {@code factor},
         * latency untouched.
         *
         * <p>This exists for the constrained-bandwidth scenario (§9). In the
         * default deployment a peer exchange costs ≈22 ticks against ≈171 ticks
         * of local training, so communication is ~2% of a round and the
         * {@code B̃} term of Eq. score has no lever on round duration however it
         * ranks peers. Scaling capacity down moves communication into the
         * binding position, which is the only configuration in which a
         * bandwidth-aware selector could pay for the mixing it gives up.
         * Latency is deliberately <i>not</i> scaled: it is a queueing and
         * propagation quantity, and scaling both would confound which term
         * gained the lever.</p>
         *
         * @param factor capacity multiplier ({@code > 0}); {@code 0.1} makes
         *               links ten times slower.
         * @return a scaled copy; the band floor stays at 1 byte/tick.
         */
        public Tier withBandwidthScale(double factor) {
            if (factor <= 0.0) {
                throw new IllegalArgumentException("bandwidth scale must be > 0: " + factor);
            }
            if (factor == 1.0) {
                return this;
            }
            return new Tier(name, minLatencyTicks, maxLatencyTicks,
                    Math.max(1L, Math.round(minBandwidthBytesPerTick * factor)),
                    Math.max(1L, Math.round(maxBandwidthBytesPerTick * factor)));
        }
    }

    /**
     * Near-edge tier (§9): well-connected fog nodes — 1–4 ms access latency,
     * 40–80 kB/tick (≈40–80 MB/s at a 1 ms tick).
     */
    public static final Tier NEAR_EDGE = new Tier("near_edge", 1L, 4L, 40_000L, 80_000L);

    /**
     * Far-edge tier (§9): constrained access — 5–15 ms latency, 8–25 kB/tick
     * (≈8–25 MB/s), the straggler side of the deployment.
     */
    public static final Tier FAR_EDGE = new Tier("far_edge", 5L, 15L, 8_000L, 25_000L);

    private final Tier[] tierOfNode;
    private final GeoCostModel propagation;
    private final long seed;
    private final long[] accessLatency;
    private final long[] accessBandwidth;

    /**
     * Builds the model and draws every node's access-link characteristics.
     *
     * @param tierOfNode per-node tier (index = node id).
     * @param propagation the great-circle propagation model (may be null ⇒ no
     *                    propagation term).
     * @param seed the campaign seed.
     */
    public LinkCostModel(Tier[] tierOfNode, GeoCostModel propagation, long seed) {
        if (tierOfNode == null || tierOfNode.length == 0) {
            throw new IllegalArgumentException("tierOfNode must be non-empty");
        }
        this.tierOfNode = tierOfNode.clone();
        this.propagation = propagation;
        this.seed = seed;
        int n = tierOfNode.length;
        this.accessLatency = new long[n];
        this.accessBandwidth = new long[n];
        for (int i = 0; i < n; i++) {
            // Dedicated stream, two draws per node, latency first (draw-order contract).
            SplitMix64 rng = SimRandom.derive(seed * STREAM_MULT + STREAM_ADD, 0, i);
            Tier t = tierOfNode[i];
            accessLatency[i] = bandDraw(rng.nextDouble(), t.minLatencyTicks, t.maxLatencyTicks);
            accessBandwidth[i] = bandDraw(rng.nextDouble(),
                    t.minBandwidthBytesPerTick, t.maxBandwidthBytesPerTick);
        }
    }

    /** Maps a {@code [0,1)} draw onto the inclusive integer band {@code [lo, hi]}. */
    private static long bandDraw(double draw, long lo, long hi) {
        if (hi <= lo) {
            return lo;
        }
        long span = hi - lo + 1L;
        long offset = (long) Math.floor(draw * span);
        if (offset >= span) {
            offset = span - 1L;
        }
        return lo + offset;
    }

    /** This node's access-link one-way latency (ticks). */
    public long accessLatencyTicks(int node) {
        return accessLatency[node];
    }

    /** This node's access-link capacity (bytes/tick). */
    public long accessBandwidthBytesPerTick(int node) {
        return accessBandwidth[node];
    }

    /** The tier assigned to {@code node}. */
    public Tier tier(int node) {
        return tierOfNode[node];
    }

    /**
     * End-to-end one-way latency of edge {@code (u,v)}: both access legs plus
     * great-circle propagation.
     *
     * @param u first endpoint id.
     * @param v second endpoint id.
     * @param a location of {@code u} (may be null ⇒ no propagation term).
     * @param b location of {@code v} (may be null ⇒ no propagation term).
     * @return latency in ticks ({@code >= 1}).
     */
    public long latencyTicks(int u, int v, GeoLocation a, GeoLocation b) {
        long ticks = accessLatency[u] + accessLatency[v];
        if (propagation != null && a != null && b != null) {
            // GeoCostModel.latencyTicks includes its own base floor; the access
            // legs already provide the floor here, so take the propagation part only.
            ticks += propagation.latencyTicks(a, b) - GeoCostModel.DEFAULT_BASE_LATENCY_TICKS;
        }
        return Math.max(1L, ticks);
    }

    /** Bottleneck capacity of edge {@code (u,v)} — the slower access link. */
    public long bandwidthBytesPerTick(int u, int v) {
        return Math.min(accessBandwidth[u], accessBandwidth[v]);
    }

    /** The campaign seed these draws were derived from. */
    public long seed() {
        return seed;
    }
}
