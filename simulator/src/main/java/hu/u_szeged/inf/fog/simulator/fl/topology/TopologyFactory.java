package hu.u_szeged.inf.fog.simulator.fl.topology;

import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology.Edge;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.util.ArrayList;
import java.util.List;

/**
 * Built-in topology generators (§8.1, §9 S2 and the system-only scaling
 * extension): ring, full mesh, scale-free (Barabási–Albert preferential
 * attachment), clustered (dense-intra / sparse-inter — the §9 base graph), and
 * a dynamic wrapper.
 *
 * <h2>Costs</h2>
 * Generators stamp every edge with uniform default costs
 * ({@link #DEFAULT_LATENCY_TICKS}, {@link #DEFAULT_BANDWIDTH_BYTES_PER_TICK}),
 * matching the Pass-1 schema example. λ₂ is purely structural (unweighted
 * Laplacian), so costs never affect the connectivity tests. A scenario that
 * wants geography-derived latency rebuilds edges through {@link GeoCostModel};
 * whatever lands on the edge is what Pass-1 exports.
 *
 * <h2>Cluster labels</h2>
 * {@link #clustered} assigns labels directly from the cluster sizes. The other
 * generators default to a 50/50 split ({@link #halfHalfLabels}) so the
 * inter-cluster TID scope stays defined at n ∈ {30, 100} (§9 "Experiment
 * scales").
 *
 * <h2>Determinism</h2>
 * The scale-free generator consumes a {@link SplitMix64} stream; topology
 * generation happens once in Pass-1 (Java) and is <i>exported</i> (Python reads
 * the edge list, it does not regenerate), so the only contract is
 * same-seed ⇒ same-graph within Java. The cross-language RNG contract applies
 * to per-round decisions (peer selection, dynamic toggling), not to one-shot
 * graph generation.
 */
public final class TopologyFactory {

    /** Default uniform link latency (ticks) stamped on generated edges. */
    public static final long DEFAULT_LATENCY_TICKS = 10L;
    /** Default uniform link bandwidth (bytes/tick) stamped on generated edges. */
    public static final long DEFAULT_BANDWIDTH_BYTES_PER_TICK = 40_000L;

    /** Minimum node count for a structurally valid scale-free graph (§8.1, §9). */
    public static final int SCALE_FREE_MIN_NODES = 30;

    private TopologyFactory() {
    }

    /** Cluster labels splitting {@code 0..n-1} in half: {@code [0, n/2) -> 0},
     *  {@code [n/2, n) -> 1}. */
    public static int[] halfHalfLabels(int n) {
        int[] labels = new int[n];
        int half = n / 2;
        for (int i = 0; i < n; i++) {
            labels[i] = i < half ? 0 : 1;
        }
        return labels;
    }

    private static Edge defaultEdge(int u, int v) {
        return new Edge(u, v, DEFAULT_LATENCY_TICKS, DEFAULT_BANDWIDTH_BYTES_PER_TICK);
    }

    /**
     * Ring C_n: each node connected to the next, wrapping around
     * ({@code i — (i+1) mod n}). λ₂ = 2(1 − cos(2π/n)).
     *
     * @param n number of nodes ({@code >= 3}; a ring needs at least a triangle).
     */
    public static FLTopology ring(int n) {
        if (n < 3) {
            throw new IllegalArgumentException("ring requires n >= 3: " + n);
        }
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            edges.add(defaultEdge(i, (i + 1) % n));
        }
        return new FLTopology(n, edges, halfHalfLabels(n));
    }

    /**
     * Complete graph K_n: every pair connected. λ₂ = n.
     *
     * @param n number of nodes ({@code >= 2}).
     */
    public static FLTopology fullMesh(int n) {
        if (n < 2) {
            throw new IllegalArgumentException("fullMesh requires n >= 2: " + n);
        }
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                edges.add(defaultEdge(i, j));
            }
        }
        return new FLTopology(n, edges, halfHalfLabels(n));
    }

    /**
     * Scale-free graph via Barabási–Albert preferential attachment. Seeds a
     * clique of {@code m+1} nodes, then each subsequent node attaches to
     * {@code m} distinct existing nodes chosen with probability proportional to
     * their current degree. Connected by construction.
     *
     * <p><b>Guard:</b> instantiated only at {@code n >= 30} (§8.1/§9 — at
     * smaller n preferential attachment is not structurally meaningful).</p>
     *
     * @param n number of nodes ({@code >= 30}).
     * @param m edges added per new node ({@code 1 <= m < n}).
     * @param rng deterministic stream for attachment choices.
     */
    public static FLTopology scaleFreeBA(int n, int m, SplitMix64 rng) {
        if (n < SCALE_FREE_MIN_NODES) {
            throw new IllegalArgumentException(
                    "scale-free generation requires n >= " + SCALE_FREE_MIN_NODES
                            + " (preferential attachment is not structurally valid below that): " + n);
        }
        if (m < 1 || m >= n) {
            throw new IllegalArgumentException("m must satisfy 1 <= m < n: " + m);
        }
        List<Edge> edges = new ArrayList<>();
        // `targets` is a multiset of node ids: each node appears once per
        // incident edge, so a uniform pick from it is a degree-proportional pick.
        List<Integer> targets = new ArrayList<>();

        // Seed clique on nodes 0..m (size m+1), guaranteeing >= m existing nodes
        // and non-zero degrees for the first attachment step.
        int seedSize = m + 1;
        for (int i = 0; i < seedSize; i++) {
            for (int j = i + 1; j < seedSize; j++) {
                edges.add(defaultEdge(i, j));
                targets.add(i);
                targets.add(j);
            }
        }

        for (int newNode = seedSize; newNode < n; newNode++) {
            // Pick m distinct existing targets, degree-proportionally.
            List<Integer> chosen = new ArrayList<>(m);
            int guard = 0;
            while (chosen.size() < m) {
                int pick = targets.get(rng.nextInt(targets.size()));
                if (!chosen.contains(pick)) {
                    chosen.add(pick);
                }
                if (++guard > 100_000) {
                    // Defensive: should never trigger for m < n with a healthy multiset.
                    throw new IllegalStateException("scale-free attachment failed to find m distinct targets");
                }
            }
            for (int t : chosen) {
                edges.add(defaultEdge(newNode, t));
                targets.add(newNode);
                targets.add(t);
            }
        }
        return new FLTopology(n, edges, halfHalfLabels(n));
    }

    /**
     * Watts–Strogatz small-world graph: a ring lattice in which every node is
     * joined to its {@code kRing/2} nearest neighbours on each side, with each
     * lattice edge rewired to a uniformly random target with probability
     * {@code beta}.
     *
     * <p><b>Why the sweep needs it.</b> λ₂ is the predictor in the TID
     * monotonicity test, and the ring/scale-free/dynamic/mesh set leaves a two
     * order-of-magnitude hole between the sparse pole and the dense ones. β
     * interpolates continuously between the lattice ({@code β = 0}, low λ₂) and
     * a random regular graph ({@code β → 1}, high λ₂), so two β values populate
     * exactly that gap. With more families the family-level permutation test has
     * a lower floor: at four families the smallest attainable p is 1/24 ≈ 0.042,
     * so only a perfectly monotone ordering is detectable at all; at six it is
     * 1/720, so a partial ordering can also be assessed.</p>
     *
     * <p>Rewiring preserves the edge count and never creates a self-loop or a
     * duplicate; if no legal target exists the lattice edge is kept, which is
     * the standard construction.</p>
     *
     * @param n number of nodes ({@code >= 4}).
     * @param kRing lattice degree — must be even and satisfy {@code 2 <= kRing < n}.
     * @param beta rewiring probability in {@code [0, 1]}.
     * @param rng deterministic stream for the rewiring choices.
     */
    public static FLTopology wattsStrogatz(int n, int kRing, double beta, SplitMix64 rng) {
        if (n < 4) {
            throw new IllegalArgumentException("watts-strogatz requires n >= 4: " + n);
        }
        if (kRing < 2 || kRing % 2 != 0 || kRing >= n) {
            throw new IllegalArgumentException(
                    "kRing must be even and satisfy 2 <= kRing < n: " + kRing);
        }
        if (beta < 0.0 || beta > 1.0) {
            throw new IllegalArgumentException("beta must be in [0,1]: " + beta);
        }
        int half = kRing / 2;
        // Adjacency as a canonical pair set, so duplicate checks are O(1).
        java.util.Set<Long> present = new java.util.HashSet<>();
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 1; j <= half; j++) {
                int b = (i + j) % n;
                long key = canonicalKey(i, b, n);
                if (present.add(key)) {
                    pairs.add(new int[] { i, b });
                }
            }
        }
        // Rewire in the construction order, one draw per edge, then one draw per
        // target attempt — the documented order for reproducibility.
        for (int[] e : pairs) {
            if (rng.nextDouble() >= beta) {
                continue;
            }
            int keep = e[0];
            int old = e[1];
            int target = -1;
            for (int attempt = 0; attempt < 32; attempt++) {
                int cand = rng.nextInt(n);
                if (cand == keep || present.contains(canonicalKey(keep, cand, n))) {
                    continue;
                }
                target = cand;
                break;
            }
            if (target < 0) {
                continue; // no legal rewiring available: keep the lattice edge
            }
            present.remove(canonicalKey(keep, old, n));
            present.add(canonicalKey(keep, target, n));
            e[1] = target;
        }
        List<Edge> edges = new ArrayList<>(pairs.size());
        for (int[] e : pairs) {
            edges.add(defaultEdge(e[0], e[1]));
        }
        return new FLTopology(n, edges, halfHalfLabels(n));
    }

    private static long canonicalKey(int a, int b, int n) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return (long) lo * n + hi;
    }

    /**
     * Clustered topology: each cluster is a dense intra-cluster mesh (clique),
     * with a small number of inter-cluster bridge edges (the §9 base graph is
     * two clusters of three with two bridges). Consecutive clusters are always
     * bridged so the graph is connected; any remaining bridge budget is spread
     * across distinct cross-cluster node pairs deterministically.
     *
     * @param clusterSizes size of each cluster (sum = n); at least one cluster.
     * @param bridgeEdges total number of inter-cluster edges
     *                    ({@code >= clusterCount-1} to stay connected).
     */
    public static FLTopology clustered(int[] clusterSizes, int bridgeEdges) {
        if (clusterSizes == null || clusterSizes.length < 1) {
            throw new IllegalArgumentException("at least one cluster required");
        }
        int n = 0;
        for (int s : clusterSizes) {
            if (s < 1) {
                throw new IllegalArgumentException("cluster sizes must be >= 1");
            }
            n += s;
        }
        int clusterCount = clusterSizes.length;
        if (clusterCount > 1 && bridgeEdges < clusterCount - 1) {
            throw new IllegalArgumentException(
                    "bridgeEdges (" + bridgeEdges + ") must be >= clusterCount-1 (" + (clusterCount - 1)
                            + ") to keep the graph connected");
        }

        // Node ranges and labels per cluster.
        int[] start = new int[clusterCount];
        int[] label = new int[n];
        int acc = 0;
        for (int c = 0; c < clusterCount; c++) {
            start[c] = acc;
            for (int k = 0; k < clusterSizes[c]; k++) {
                label[acc + k] = c;
            }
            acc += clusterSizes[c];
        }

        List<Edge> edges = new ArrayList<>();
        // Dense intra-cluster mesh.
        for (int c = 0; c < clusterCount; c++) {
            int s = start[c];
            int sz = clusterSizes[c];
            for (int i = 0; i < sz; i++) {
                for (int j = i + 1; j < sz; j++) {
                    edges.add(defaultEdge(s + i, s + j));
                }
            }
        }

        // Inter-cluster bridges. First ensure connectivity by bridging each
        // consecutive cluster pair, then distribute the remaining budget.
        int placed = 0;
        // (a) connectivity bridges: cluster c node[bridgeIdx] <-> cluster c+1 node[bridgeIdx]
        for (int c = 0; c + 1 < clusterCount && placed < bridgeEdges; c++) {
            int a = start[c];
            int b = start[c + 1];
            addBridgeIfAbsent(edges, a, b);
            placed++;
        }
        // (b) remaining bridges: keep adding distinct cross-cluster pairs between
        // the first two clusters (the §9 two-cluster base needs exactly this),
        // walking node offsets so the bridges land on different node pairs.
        int offset = 1;
        while (placed < bridgeEdges && clusterCount >= 2) {
            int c0 = 0;
            int c1 = 1;
            int a = start[c0] + (offset % clusterSizes[c0]);
            int b = start[c1] + (offset % clusterSizes[c1]);
            if (!hasEdge(edges, a, b)) {
                edges.add(defaultEdge(a, b));
                placed++;
            }
            offset++;
            if (offset > clusterSizes[c0] * clusterSizes[c1] + 2) {
                // Exhausted distinct pairs between clusters 0 and 1.
                break;
            }
        }

        return new FLTopology(n, edges, label);
    }

    private static boolean hasEdge(List<Edge> edges, int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        for (Edge e : edges) {
            if (e.u == lo && e.v == hi) {
                return true;
            }
        }
        return false;
    }

    private static void addBridgeIfAbsent(List<Edge> edges, int a, int b) {
        if (!hasEdge(edges, a, b)) {
            edges.add(defaultEdge(a, b));
        }
    }

    /**
     * Dynamic topology wrapper (§8.1): a structural copy of {@code base} whose
     * dynamism is realised at runtime by the orchestrator calling
     * {@link FLTopology#applyDynamicRound(int, long, double)} each round with
     * {@code pLink}. The structure itself is the union (base) graph; the type
     * tag and per-round inactive schedule are written by the Pass-1 exporter.
     *
     * @param base the base/union graph to make time-varying.
     * @param pLink per-round link-up probability (validated to {@code [0,1]}).
     * @return a fresh copy of {@code base} (so the caller's base stays intact).
     */
    public static FLTopology dynamic(FLTopology base, double pLink) {
        if (pLink < 0.0 || pLink > 1.0) {
            throw new IllegalArgumentException("pLink must be in [0,1]: " + pLink);
        }
        return copyOf(base);
    }

    /** A structural deep copy (fresh Edge objects, reset to active). */
    public static FLTopology copyOf(FLTopology base) {
        List<Edge> copies = new ArrayList<>();
        for (Edge e : base.edges()) {
            copies.add(new Edge(e.u, e.v, e.latencyTicks, e.bandwidthBytesPerTick));
        }
        return new FLTopology(base.size(), copies, base.clusterLabels());
    }

    /**
     * Rebuilds {@code base} with per-edge <b>latency derived from node
     * geography</b> (design decision D1, §8.1: link costs follow the inter-node
     * distance / {@code GeoLocation} model): for each edge {@code (u,v)},
     * {@code latency = model.latencyTicks(locations[u], locations[v])}.
     * Bandwidth is kept from the base edge (geography informs propagation
     * delay, not link capacity). Structure, edge order, and cluster labels are
     * unchanged; the result is a fresh static graph (all edges active).
     *
     * <p>Whatever costs end up on the edges are what Pass 1 exports — one
     * source of truth. Note the {@code topologyHash()} covers costs, so the
     * geo-derived graph hashes differently from the flat-default one.</p>
     *
     * @param base the structural graph (flat default costs).
     * @param locations per-node positions, index = node id, length = n.
     * @param model the latency model (see {@link GeoCostModel} defaults).
     * @return a new topology with geography-derived latencies.
     */
    public static FLTopology withGeoCosts(FLTopology base, GeoLocation[] locations,
                                          GeoCostModel model) {
        if (locations == null || locations.length != base.size()) {
            throw new IllegalArgumentException("locations must have length n=" + base.size());
        }
        List<Edge> rebuilt = new ArrayList<>();
        for (Edge e : base.edges()) {
            long lat = model.latencyTicks(locations[e.u], locations[e.v]);
            rebuilt.add(new Edge(e.u, e.v, lat, e.bandwidthBytesPerTick));
        }
        return new FLTopology(base.size(), rebuilt, base.clusterLabels());
    }

    /**
     * Rebuilds {@code base} with <b>heterogeneous</b> per-edge latency
     * <i>and</i> bandwidth from a {@link LinkCostModel} (§8.1): each endpoint
     * contributes its device tier's access-link characteristics, capacity is the
     * bottleneck of the two, and great-circle propagation is added on top.
     *
     * <p>This is what makes the {@code L̃} and {@code B̃} terms of Eq. score
     * non-degenerate: with the flat generator defaults every edge carries an
     * identical cost, the min–max normaliser hits its homogeneous guard, and the
     * composite selector collapses onto its remaining terms. Structure, edge
     * order, and cluster labels are unchanged; the result is a fresh static
     * graph (all edges active).</p>
     *
     * <p>Whatever costs land on the edges are what Pass 1 exports — one source
     * of truth. {@code topologyHash()} covers costs, so a heterogeneous graph
     * hashes differently from the flat-default one.</p>
     *
     * @param base the structural graph (flat default costs).
     * @param locations per-node positions, index = node id, length = n (nullable
     *                  entries disable the propagation term for their edges).
     * @param model the link-cost model (already seeded).
     * @return a new topology with heterogeneous link costs.
     */
    public static FLTopology withLinkCosts(FLTopology base, GeoLocation[] locations,
                                           LinkCostModel model) {
        if (model == null) {
            throw new IllegalArgumentException("link cost model must not be null");
        }
        if (locations == null || locations.length != base.size()) {
            throw new IllegalArgumentException("locations must have length n=" + base.size());
        }
        List<Edge> rebuilt = new ArrayList<>();
        for (Edge e : base.edges()) {
            long lat = model.latencyTicks(e.u, e.v, locations[e.u], locations[e.v]);
            long bw = model.bandwidthBytesPerTick(e.u, e.v);
            rebuilt.add(new Edge(e.u, e.v, lat, bw));
        }
        return new FLTopology(base.size(), rebuilt, base.clusterLabels());
    }
}
