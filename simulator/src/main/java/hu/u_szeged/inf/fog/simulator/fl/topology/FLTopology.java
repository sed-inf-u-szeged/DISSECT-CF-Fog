package hu.u_szeged.inf.fog.simulator.fl.topology;

import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The simulation's decentralized communication graph (§8.1 Topology
 * Abstraction). Vertices are federated devices (node ids {@code 0..n-1});
 * edges are annotated with per-link latency and bandwidth (derived from the
 * inter-node distance / GeoLocation model — see {@link GeoCostModel}) and an
 * activation flag that lets links appear and disappear across rounds, which is
 * how dynamic (time-varying) topologies are realised.
 *
 * <p>This class is intentionally <b>JSON-free</b>: it exposes the graph
 * structure, per-link costs, algebraic connectivity, and a stable hash; the
 * Pass-1 serialisation to {@code system_trace.json} lives in
 * {@code hu.u_szeged.inf.fog.simulator.fl.cosim.SystemTraceWriter} so the
 * topology package carries no jackson dependency and there is no
 * {@code topology → cosim} cycle.</p>
 *
 * <h2>Edge model</h2>
 * Edges are stored once, in a canonical {@code u < v} orientation, in the
 * order they were added by the generator. The graph is undirected; the
 * activation flag belongs to the <i>edge</i> (§8.1), so toggling it removes
 * the link in both directions for that round.
 *
 * <h2>Static vs. dynamic</h2>
 * A freshly generated topology has every edge active (the static / union
 * graph). {@link #applyDynamicRound(int, long, double)} recomputes the active
 * set for a given round purely from {@code (seed, round, pLink)} — it is
 * idempotent per round and independent of the current active state, so it can
 * be applied, queried, and re-applied without drift. The realised inactive
 * set for the last applied round is available via {@link #inactiveEdges()}.
 *
 * <h2>λ₂</h2>
 * {@link #lambda2()} is computed over the <i>currently active</i> edges; see
 * {@link Lambda2}. It uses the unweighted Laplacian {@code L = D − A} (costs
 * do not enter λ₂), so closed-form values for canonical graphs are exact.
 *
 * <p>Not thread-safe; the simulator runs single-threaded on the {@code Timed}
 * loop.</p>
 */
public final class FLTopology {

    /**
     * An undirected, cost-annotated edge in canonical {@code u < v} form.
     * The {@link #active} flag is mutated only by
     * {@link FLTopology#applyDynamicRound(int, long, double)} and
     * {@link FLTopology#resetActive()}.
     */
    public static final class Edge {
        /** Lower-indexed endpoint ({@code u < v}). */
        public final int u;
        /** Higher-indexed endpoint ({@code u < v}). */
        public final int v;
        /** One-way link latency in simulator ticks (the {@code L_ij} term). */
        public final long latencyTicks;
        /** Available bandwidth in bytes/tick (drives {@code B_ij = payload / bw}). */
        public final long bandwidthBytesPerTick;
        /** Whether the link is up this round (§8.1: the flag belongs to the edge). */
        public boolean active;

        Edge(int u, int v, long latencyTicks, long bandwidthBytesPerTick) {
            if (u == v) {
                throw new IllegalArgumentException("self-loop not allowed: " + u);
            }
            // Canonical orientation u < v.
            if (u < v) {
                this.u = u;
                this.v = v;
            } else {
                this.u = v;
                this.v = u;
            }
            if (latencyTicks < 0 || bandwidthBytesPerTick <= 0) {
                throw new IllegalArgumentException(
                        "latency must be >= 0 and bandwidth > 0 (got " + latencyTicks
                                + ", " + bandwidthBytesPerTick + ")");
            }
            this.latencyTicks = latencyTicks;
            this.bandwidthBytesPerTick = bandwidthBytesPerTick;
            this.active = true;
        }

        /** The endpoint opposite {@code node}. */
        public int other(int node) {
            if (node == u) {
                return v;
            }
            if (node == v) {
                return u;
            }
            throw new IllegalArgumentException("node " + node + " is not an endpoint of edge (" + u + "," + v + ")");
        }

        @Override
        public String toString() {
            return "(" + u + "," + v + ",L=" + latencyTicks + ",B=" + bandwidthBytesPerTick
                    + "," + (active ? "up" : "down") + ")";
        }
    }

    private final int n;
    private final List<Edge> edges;
    private final int[] clusterLabel;
    /** node -> list of incident edges (over the full union edge set). */
    private final List<List<Edge>> incident;
    /** Canonical key ({@code u < v} pair) to edge, for O(1) {@code edge(i,j)} lookup. */
    private final Map<Long, Edge> edgeByPair;

    /** Inactive edges recorded by the most recent {@link #applyDynamicRound}. */
    private List<int[]> lastInactiveEdges = Collections.emptyList();
    private int lastDynamicRound = -1;

    /**
     * Builds a topology. Generators in {@link TopologyFactory} are the normal
     * way to obtain one; this constructor is public so explicit graphs can be
     * built in tests and from configuration.
     *
     * @param n number of nodes ({@code >= 1}).
     * @param edges the union edge set; each edge re-canonicalised to {@code u<v}.
     * @param clusterLabel per-node cluster id (length {@code n}); used for the
     *                     inter-cluster TID scope. Pass an all-zero array for a
     *                     single-cluster graph.
     */
    public FLTopology(int n, List<Edge> edges, int[] clusterLabel) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1: " + n);
        }
        if (clusterLabel == null || clusterLabel.length != n) {
            throw new IllegalArgumentException("clusterLabel must have length n=" + n);
        }
        this.n = n;
        this.clusterLabel = clusterLabel.clone();
        this.edges = new ArrayList<>();
        this.incident = new ArrayList<>(n);
        this.edgeByPair = new HashMap<>();
        for (int i = 0; i < n; i++) {
            this.incident.add(new ArrayList<>());
        }
        for (Edge e : edges) {
            addEdgeInternal(e);
        }
    }

    private void addEdgeInternal(Edge e) {
        if (e.u < 0 || e.v >= n) {
            throw new IllegalArgumentException("edge endpoint out of range for n=" + n + ": " + e);
        }
        long key = pairKey(e.u, e.v);
        if (edgeByPair.containsKey(key)) {
            throw new IllegalArgumentException("duplicate edge: " + e);
        }
        edgeByPair.put(key, e);
        edges.add(e);
        incident.get(e.u).add(e);
        incident.get(e.v).add(e);
    }

    private long pairKey(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return (long) lo * (long) n + (long) hi;
    }

    /** Number of nodes. */
    public int size() {
        return n;
    }

    /** The full union edge set (every edge, regardless of active state). The
     *  returned list is unmodifiable; the {@link Edge#active} flags inside are
     *  mutated by dynamic rounds. */
    public List<Edge> edges() {
        return Collections.unmodifiableList(edges);
    }

    /** Per-node cluster labels (defensive copy). */
    public int[] clusterLabels() {
        return clusterLabel.clone();
    }

    /** Cluster label of a single node. */
    public int clusterOf(int node) {
        return clusterLabel[node];
    }

    /**
     * Active neighbours of {@code node}, in ascending node-id order. Only edges
     * whose {@link Edge#active} flag is set are followed, so this reflects the
     * current (possibly dynamic) round.
     */
    public List<Integer> neighbours(int node) {
        List<Integer> out = new ArrayList<>();
        for (Edge e : incident.get(node)) {
            if (e.active) {
                out.add(e.other(node));
            }
        }
        Collections.sort(out);
        return out;
    }

    /**
     * The edge connecting {@code i} and {@code j}, or {@code null} if there is
     * no such edge in the union graph (regardless of active state).
     */
    public Edge edge(int i, int j) {
        return edgeByPair.get(pairKey(i, j));
    }

    /** Active degree of {@code node} (number of active incident edges). */
    public int degree(int node) {
        int d = 0;
        for (Edge e : incident.get(node)) {
            if (e.active) {
                d++;
            }
        }
        return d;
    }

    /** Algebraic connectivity λ₂ over the currently active edges (§8.1, §8.3). */
    public double lambda2() {
        return Lambda2.lambda2(this);
    }

    /**
     * Recomputes the active edge set for {@code round} using a Bernoulli trial
     * per edge with "up" probability {@code pLink} (so {@code pLink} close to 1
     * keeps most links up; a draw {@code >= pLink} drops the link, modelling a
     * failure / mobility-driven loss).
     *
     * <p>Draw-order contract (mirrors {@code harness/flrng.py}; see
     * {@link SimRandom#derive} and {@code docs/REPRODUCIBILITY.md} §2.1):
     * a single stream {@code derive(seed, round, -1)} yields one
     * {@code nextDouble()} per edge, with edges visited in ascending
     * {@code (u, v)} order; edge {@code active = (draw < pLink)}.</p>
     *
     * <p>Idempotent per round: the result depends only on
     * {@code (seed, round, pLink)} and the (immutable) edge ordering, never on
     * the prior active state, so it can be applied repeatedly. The realised
     * inactive set is recorded for {@link #inactiveEdges()} / Pass-1 export.</p>
     *
     * @param round the gossip round ({@code >= 0}).
     * @param seed the campaign seed.
     * @param pLink probability a link is up this round, in {@code [0, 1]}.
     */
    public void applyDynamicRound(int round, long seed, double pLink) {
        if (pLink < 0.0 || pLink > 1.0) {
            throw new IllegalArgumentException("pLink must be in [0,1]: " + pLink);
        }
        // Visit edges in ascending (u,v) order — the contract order. The
        // construction order is generator-defined, so sort a view by (u,v).
        List<Edge> ordered = edgesAscending();
        SplitMix64 rng = SimRandom.derive(seed, round, -1);
        List<int[]> inactive = new ArrayList<>();
        for (Edge e : ordered) {
            double draw = rng.nextDouble();
            e.active = draw < pLink;
            if (!e.active) {
                inactive.add(new int[] { e.u, e.v });
            }
        }
        this.lastInactiveEdges = inactive;
        this.lastDynamicRound = round;
    }

    /** Sets every edge active (the static / union graph). */
    public void resetActive() {
        for (Edge e : edges) {
            e.active = true;
        }
        this.lastInactiveEdges = Collections.emptyList();
        this.lastDynamicRound = -1;
    }

    /** Inactive edges (as {@code [u,v]} pairs) from the most recent
     *  {@link #applyDynamicRound}; empty if none applied or all links up. */
    public List<int[]> inactiveEdges() {
        return Collections.unmodifiableList(lastInactiveEdges);
    }

    /** The round index of the most recent {@link #applyDynamicRound}, or -1. */
    public int lastDynamicRound() {
        return lastDynamicRound;
    }

    /** Edges sorted ascending by {@code (u, v)} (a fresh list; elements shared). */
    public List<Edge> edgesAscending() {
        List<Edge> ordered = new ArrayList<>(edges);
        ordered.sort((a, b) -> a.u != b.u ? Integer.compare(a.u, b.u) : Integer.compare(a.v, b.v));
        return ordered;
    }

    /**
     * Whether the graph is connected over the currently active edges (BFS).
     * Generators produce connected graphs; dynamic rounds may disconnect, which
     * is allowed and surfaced through this method (and a near-zero λ₂).
     */
    public boolean isConnected() {
        if (n == 1) {
            return true;
        }
        boolean[] seen = new boolean[n];
        Deque<Integer> q = new ArrayDeque<>();
        q.add(0);
        seen[0] = true;
        int count = 1;
        while (!q.isEmpty()) {
            int cur = q.poll();
            for (Edge e : incident.get(cur)) {
                if (!e.active) {
                    continue;
                }
                int nb = e.other(cur);
                if (!seen[nb]) {
                    seen[nb] = true;
                    count++;
                    q.add(nb);
                }
            }
        }
        return count == n;
    }

    /**
     * A stable SHA-256 hash of the static graph identity: {@code n}, the
     * cluster labels, and the canonical edge list with costs (active flags are
     * <i>not</i> hashed, since they vary per round). Used by the Pass-3
     * {@code TraceReader} to verify a trace was produced against this exact
     * topology. Stable across runs and JVMs (no object identity, fixed
     * field order, integer-only formatting).
     *
     * @return lowercase hex SHA-256 digest.
     */
    public String topologyHash() {
        StringBuilder sb = new StringBuilder();
        sb.append("FLTopology/v1;n=").append(n).append(';');
        sb.append("clusters=").append(Arrays.toString(clusterLabel)).append(';');
        sb.append("edges=");
        for (Edge e : edgesAscending()) {
            sb.append(e.u).append(',').append(e.v).append(',')
              .append(e.latencyTicks).append(',').append(e.bandwidthBytesPerTick).append(';');
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JLS-standard providers; this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
