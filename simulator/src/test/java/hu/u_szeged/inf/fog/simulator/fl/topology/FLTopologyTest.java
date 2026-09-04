package hu.u_szeged.inf.fog.simulator.fl.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology.Edge;
import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import hu.u_szeged.inf.fog.simulator.util.SplitMix64;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P1 DoD: generator degree distributions, connectivity, λ₂ closed forms
 * (within 1e-9), topology-hash stability, the scale-free n&lt;30 guard, and the
 * dynamic-schedule draw-order contract.
 */
class FLTopologyTest {

    private static final double EPS = 1e-9;

    // ---------------------------------------------------------------
    // λ₂ closed forms
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ring λ₂ = 2(1 - cos(2π/n)) for several n")
    void ringLambda2ClosedForm() {
        for (int n : new int[] { 3, 4, 6, 12, 30, 100 }) {
            FLTopology ring = TopologyFactory.ring(n);
            double expected = 2.0 * (1.0 - Math.cos(2.0 * Math.PI / n));
            assertEquals(expected, ring.lambda2(), EPS, "ring λ₂ mismatch at n=" + n);
        }
    }

    @Test
    @DisplayName("complete graph K_n λ₂ = n")
    void meshLambda2ClosedForm() {
        for (int n : new int[] { 2, 3, 6, 10, 30 }) {
            FLTopology mesh = TopologyFactory.fullMesh(n);
            assertEquals((double) n, mesh.lambda2(), EPS, "K_n λ₂ mismatch at n=" + n);
        }
    }

    @Test
    @DisplayName("star K_{1,n-1} λ₂ = 1")
    void starLambda2ClosedForm() {
        for (int n : new int[] { 3, 5, 8, 20 }) {
            FLTopology star = star(n);
            assertEquals(1.0, star.lambda2(), EPS, "star λ₂ mismatch at n=" + n);
        }
    }

    @Test
    @DisplayName("disconnected graph λ₂ = 0")
    void disconnectedLambda2IsZero() {
        // Two disjoint edges: 0-1 and 2-3 (n=4), no link between the components.
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(0, 1, 10, 40_000));
        edges.add(new Edge(2, 3, 10, 40_000));
        FLTopology g = new FLTopology(4, edges, new int[] { 0, 0, 1, 1 });
        assertFalse(g.isConnected(), "graph should be disconnected");
        assertEquals(0.0, g.lambda2(), EPS, "disconnected λ₂ should be 0");
    }

    // ---------------------------------------------------------------
    // Degree distributions & connectivity
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ring: every node has active degree 2 and graph is connected")
    void ringDegrees() {
        FLTopology ring = TopologyFactory.ring(30);
        for (int i = 0; i < 30; i++) {
            assertEquals(2, ring.degree(i), "ring node " + i + " degree");
            assertEquals(2, ring.neighbours(i).size(), "ring node " + i + " neighbour count");
        }
        assertTrue(ring.isConnected());
        assertEquals(30, ring.edges().size(), "ring edge count");
    }

    @Test
    @DisplayName("full mesh: every node degree n-1, edge count n(n-1)/2")
    void meshDegrees() {
        int n = 12;
        FLTopology mesh = TopologyFactory.fullMesh(n);
        for (int i = 0; i < n; i++) {
            assertEquals(n - 1, mesh.degree(i), "mesh node " + i + " degree");
        }
        assertEquals(n * (n - 1) / 2, mesh.edges().size(), "mesh edge count");
        assertTrue(mesh.isConnected());
    }

    @Test
    @DisplayName("scale-free BA: connected, correct edge count, sum of degrees = 2|E|")
    void scaleFreeStructure() {
        int n = 30;
        int m = 2;
        SplitMix64 rng = SimRandom.derive(42L, 0, 0);
        FLTopology sf = TopologyFactory.scaleFreeBA(n, m, rng);
        // Seed clique on m+1=3 nodes => 3 edges; then (n - (m+1)) new nodes × m edges.
        int expectedEdges = (m + 1) * m / 2 + (n - (m + 1)) * m;
        assertEquals(expectedEdges, sf.edges().size(), "BA edge count");
        assertTrue(sf.isConnected(), "BA graph must be connected");
        int degreeSum = 0;
        for (int i = 0; i < n; i++) {
            degreeSum += sf.degree(i);
        }
        assertEquals(2 * sf.edges().size(), degreeSum, "handshake lemma");
        assertTrue(sf.lambda2() > 0.0, "connected BA must have λ₂ > 0");
    }

    @Test
    @DisplayName("scale-free guard: n < 30 throws")
    void scaleFreeGuardFires() {
        SplitMix64 rng = SimRandom.derive(1L, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> TopologyFactory.scaleFreeBA(29, 2, rng));
        assertThrows(IllegalArgumentException.class,
                () -> TopologyFactory.scaleFreeBA(6, 2, rng));
    }

    @Test
    @DisplayName("scale-free: same seed reproduces the same graph (hash equal)")
    void scaleFreeDeterministic() {
        FLTopology a = TopologyFactory.scaleFreeBA(50, 3, SimRandom.derive(7L, 0, 0));
        FLTopology b = TopologyFactory.scaleFreeBA(50, 3, SimRandom.derive(7L, 0, 0));
        assertEquals(a.topologyHash(), b.topologyHash(), "same seed -> same BA graph");
        FLTopology c = TopologyFactory.scaleFreeBA(50, 3, SimRandom.derive(8L, 0, 0));
        assertNotEquals(a.topologyHash(), c.topologyHash(), "different seed -> different BA graph");
    }

    @Test
    @DisplayName("clustered base graph (§9): 2×3 cliques + 2 bridges, labels [0,0,0,1,1,1]")
    void clusteredBaseGraph() {
        FLTopology g = TopologyFactory.clustered(new int[] { 3, 3 }, 2);
        assertEquals(6, g.size());
        // Two triangles (3 edges each) + 2 bridges = 8 edges.
        assertEquals(8, g.edges().size(), "clustered edge count");
        assertTrue(g.isConnected());
        int[] labels = g.clusterLabels();
        assertEquals(0, labels[0]);
        assertEquals(0, labels[1]);
        assertEquals(0, labels[2]);
        assertEquals(1, labels[3]);
        assertEquals(1, labels[4]);
        assertEquals(1, labels[5]);
        // Count inter-cluster (bridge) edges.
        int bridges = 0;
        for (Edge e : g.edges()) {
            if (labels[e.u] != labels[e.v]) {
                bridges++;
            }
        }
        assertEquals(2, bridges, "exactly 2 inter-cluster bridges");
    }

    // ---------------------------------------------------------------
    // Topology hash
    // ---------------------------------------------------------------

    @Test
    @DisplayName("topologyHash is stable across runs and independent of active flags")
    void topologyHashStable() {
        FLTopology a = TopologyFactory.ring(20);
        FLTopology b = TopologyFactory.ring(20);
        assertEquals(a.topologyHash(), b.topologyHash(), "identical rings hash equal");

        String before = a.topologyHash();
        a.applyDynamicRound(3, 99L, 0.5); // toggles active flags
        assertEquals(before, a.topologyHash(),
                "hash must ignore per-round active flags (static identity only)");
        a.resetActive();

        FLTopology mesh = TopologyFactory.fullMesh(20);
        assertNotEquals(a.topologyHash(), mesh.topologyHash(), "ring vs mesh differ");
    }

    // ---------------------------------------------------------------
    // Dynamic schedule draw-order contract
    // ---------------------------------------------------------------

    @Test
    @DisplayName("applyDynamicRound: idempotent per round and matches the derive(seed,round,-1) contract")
    void dynamicRoundContract() {
        FLTopology g = TopologyFactory.fullMesh(10);
        long seed = 2026L;
        int round = 7;
        double pLink = 0.8;

        g.applyDynamicRound(round, seed, pLink);
        List<int[]> inactive1 = copy(g.inactiveEdges());
        g.applyDynamicRound(round, seed, pLink);
        List<int[]> inactive2 = copy(g.inactiveEdges());
        assertEquals(inactive1.size(), inactive2.size(), "idempotent inactive count");
        for (int i = 0; i < inactive1.size(); i++) {
            assertEquals(inactive1.get(i)[0], inactive2.get(i)[0]);
            assertEquals(inactive1.get(i)[1], inactive2.get(i)[1]);
        }

        // Independently re-derive the active state from the documented contract:
        // one nextDouble() per edge in ascending (u,v) order; active = draw < pLink.
        g.applyDynamicRound(round, seed, pLink);
        SplitMix64 rng = SimRandom.derive(seed, round, -1);
        for (Edge e : g.edgesAscending()) {
            boolean expectedActive = rng.nextDouble() < pLink;
            assertEquals(expectedActive, e.active,
                    "edge (" + e.u + "," + e.v + ") active flag must match the contract draw");
        }
        g.resetActive();
        for (Edge e : g.edges()) {
            assertTrue(e.active, "resetActive should re-enable all edges");
        }
    }

    @Test
    @DisplayName("expectedLambda2 / unionLambda2 are finite, in range, and restore active state")
    void dynamicLambda2Metadata() {
        FLTopology g = TopologyFactory.fullMesh(12);
        double barLambda = Lambda2.expectedLambda2(g, 0.7, 20, 5L);
        double unionLambda = Lambda2.unionLambda2(g, 0.7, 20, 5L);
        assertTrue(barLambda >= 0.0 && barLambda <= 12.0, "λ̄₂ in [0,n]");
        assertTrue(unionLambda >= 0.0 && unionLambda <= 12.0, "union λ₂ in [0,n]");
        // After both calls the graph must be back to all-active.
        for (Edge e : g.edges()) {
            assertTrue(e.active, "active state restored after λ₂ metadata computation");
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    // Watts–Strogatz (the S2 λ₂ interpolators)
    // ---------------------------------------------------------------

    @org.junit.jupiter.api.Test
    @DisplayName("Watts–Strogatz: edge count is preserved, graph stays simple and connected")
    void wattsStrogatzStructure() {
        for (int kRing : new int[] { 4, 6, 14 }) {
            FLTopology g = TopologyFactory.wattsStrogatz(
                    30, kRing, 0.3, hu.u_szeged.inf.fog.simulator.util.SimRandom.derive(11L, 0, 1));
            assertEquals(30, g.size());
            // Rewiring relocates an endpoint; it never adds or removes an edge.
            assertEquals(30 * kRing / 2, g.edges().size(),
                    "k_ring=" + kRing + " must keep n·k/2 edges");
            for (FLTopology.Edge e : g.edges()) {
                assertTrue(e.u != e.v, "no self-loops");
            }
            assertTrue(g.isConnected(), "k_ring=" + kRing + " should stay connected at β=0.3");
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Watts–Strogatz: λ₂ increases with lattice degree — the S2 sweep's premise")
    void wattsStrogatzLambda2GrowsWithDegree() {
        // S2 uses the lattice DEGREE, not β, to move λ₂: β is non-monotone at
        // n=30 (β=0.5 measured a lower λ₂ than β=0.3 and collided with the
        // scale-free family), whereas degree separates the families cleanly.
        double prev = -1.0;
        for (int kRing : new int[] { 4, 6, 10, 14, 20 }) {
            FLTopology g = TopologyFactory.wattsStrogatz(
                    30, kRing, 0.3, hu.u_szeged.inf.fog.simulator.util.SimRandom.derive(11L, 0, 1));
            double l2 = g.lambda2();
            assertTrue(l2 > prev, "λ₂ must grow with k_ring: " + kRing + " gave " + l2
                    + " after " + prev);
            prev = l2;
        }
        // And the two families S2 actually instantiates must sit inside the gap
        // between scale-free (≈0.6) and dynamic (≈21.7), else they add no
        // resolution where the sweep needs it.
        double low = TopologyFactory.wattsStrogatz(
                30, 6, 0.3, hu.u_szeged.inf.fog.simulator.util.SimRandom.derive(11L, 0, 1)).lambda2();
        double high = TopologyFactory.wattsStrogatz(
                30, 14, 0.3, hu.u_szeged.inf.fog.simulator.util.SimRandom.derive(11L, 0, 1)).lambda2();
        assertTrue(low > 1.0 && low < 4.0, "k_ring=6 λ₂ outside the intended band: " + low);
        assertTrue(high > 5.0 && high < 15.0, "k_ring=14 λ₂ outside the intended band: " + high);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Watts–Strogatz: β=0 is the pure ring lattice; same seed ⇒ same graph")
    void wattsStrogatzDeterminismAndLatticeLimit() {
        FLTopology lattice = TopologyFactory.wattsStrogatz(
                12, 4, 0.0, hu.u_szeged.inf.fog.simulator.util.SimRandom.derive(1L, 0, 1));
        for (int i = 0; i < 12; i++) {
            assertEquals(4, lattice.degree(i), "β=0 leaves every node at the lattice degree");
        }
        String a = TopologyFactory.wattsStrogatz(
                30, 6, 0.3, hu.u_szeged.inf.fog.simulator.util.SimRandom.derive(7L, 0, 1)).topologyHash();
        String b = TopologyFactory.wattsStrogatz(
                30, 6, 0.3, hu.u_szeged.inf.fog.simulator.util.SimRandom.derive(7L, 0, 1)).topologyHash();
        assertEquals(a, b, "same seed must give the same graph");
    }

    // ---------------------------------------------------------------

    /** Star K_{1,n-1}: center 0 connected to 1..n-1. */
    private static FLTopology star(int n) {
        List<Edge> edges = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            edges.add(new Edge(0, i, 10, 40_000));
        }
        return new FLTopology(n, edges, new int[n]);
    }

    private static List<int[]> copy(List<int[]> in) {
        List<int[]> out = new ArrayList<>();
        for (int[] p : in) {
            out.add(new int[] { p[0], p[1] });
        }
        return out;
    }
}
