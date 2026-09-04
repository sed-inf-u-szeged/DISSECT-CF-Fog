package hu.u_szeged.inf.fog.simulator.fl.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.util.SimRandom;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2.4 + DoD item 3/5: peer-selection policies and the documented ring
 * degeneracy (on degree-2 neighbourhoods with k=2 every selector must return
 * both neighbours — asserted, not hidden).
 */
class PeerSelectionTest {

    private static PeerSelectionPolicy[] allSelectors() {
        return new PeerSelectionPolicy[] {
                new RandomK(),
                new DegreeBased(),
                new SingleFactorLatency(),
                new SingleFactorLoad(),
                new SingleFactorDivergence(),
                new SingleFactorBandwidth(),
                new CompositeScore(GammaSchedule.EXPLORE_THEN_EXPLOIT),
                new AllNeighbors(),
        };
    }

    @Test
    @DisplayName("ring degeneracy: degree-2 node, k=2 ⇒ every selector returns both neighbours")
    void ringDegeneracyAllSelectors() {
        // Node 0 on a ring: neighbours {1, 5}, both degree 2.
        List<Integer> neigh = Arrays.asList(1, 5);
        TestCostView costs = new TestCostView()
                .lat(1, 10).lat(5, 20)
                .load(1, 0.3).load(5, 0.7)
                .div(1, 0.5).div(5, 1.2)
                .bw(1, 100).bw(5, 200)
                .deg(1, 2).deg(5, 2);

        for (PeerSelectionPolicy p : allSelectors()) {
            var rng = SimRandom.derive(42L, 0, 0);
            List<Integer> chosen = p.selectPeers(0, 0, 100, neigh, costs, rng, 2);
            assertEquals(List.of(1, 5), chosen,
                    p.id() + " must return both ring neighbours when k=2=degree");
        }
    }

    @Test
    @DisplayName("single-factor latency picks the k lowest-latency peers")
    void singleFactorLatency() {
        List<Integer> neigh = Arrays.asList(1, 2, 3, 4);
        TestCostView costs = new TestCostView()
                .lat(1, 50).lat(2, 10).lat(3, 30).lat(4, 99);
        var rng = SimRandom.derive(1L, 0, 0);
        List<Integer> chosen = new SingleFactorLatency().selectPeers(0, 0, 100, neigh, costs, rng, 2);
        assertEquals(List.of(2, 3), chosen);
    }

    @Test
    @DisplayName("degree-based picks the k highest-degree peers")
    void degreeBased() {
        List<Integer> neigh = Arrays.asList(1, 2, 3, 4);
        TestCostView costs = new TestCostView()
                .deg(1, 2).deg(2, 9).deg(3, 5).deg(4, 1);
        var rng = SimRandom.derive(1L, 0, 0);
        List<Integer> chosen = new DegreeBased().selectPeers(0, 0, 100, neigh, costs, rng, 2);
        assertEquals(List.of(2, 3), chosen);
    }

    @Test
    @DisplayName("composite (explore, γ<0) prefers the MORE divergent peer when other terms tie")
    void compositeExploreRewardsDivergence() {
        // Identical latency/load/bandwidth → those normalise to 0; only D matters.
        List<Integer> neigh = Arrays.asList(1, 2, 3);
        TestCostView costs = new TestCostView()
                .lat(1, 5).lat(2, 5).lat(3, 5)
                .load(1, 0.1).load(2, 0.1).load(3, 0.1)
                .bw(1, 7).bw(2, 7).bw(3, 7)
                .div(1, 0.0).div(2, 0.5).div(3, 1.0);
        // round 0 < T/2 => explore (γ<0) => higher divergence is preferred (lower score).
        var rng = SimRandom.derive(9L, 0, 0);
        List<Integer> chosen = new CompositeScore(GammaSchedule.EXPLORE_THEN_EXPLOIT)
                .selectPeers(0, 0, 100, neigh, costs, rng, 1);
        assertEquals(List.of(3), chosen, "explore phase should reach the most divergent peer");

        // Late round (exploit, γ>0) => prefers the most similar peer.
        var rng2 = SimRandom.derive(9L, 80, 0);
        List<Integer> chosen2 = new CompositeScore(GammaSchedule.EXPLORE_THEN_EXPLOIT)
                .selectPeers(0, 80, 100, neigh, costs, rng2, 1);
        assertEquals(List.of(1), chosen2, "exploit phase should prefer the most similar peer");
    }

    @Test
    @DisplayName("random-k: deterministic per seed, returns k distinct neighbours")
    void randomKDeterministic() {
        List<Integer> neigh = Arrays.asList(1, 2, 3, 4, 5, 6);
        TestCostView costs = new TestCostView();
        List<Integer> a = new RandomK().selectPeers(0, 3, 100, neigh, costs, SimRandom.derive(7L, 3, 0), 2);
        List<Integer> b = new RandomK().selectPeers(0, 3, 100, neigh, costs, SimRandom.derive(7L, 3, 0), 2);
        assertEquals(a, b, "same derived stream => same random selection");
        assertEquals(2, a.size());
        assertTrue(neigh.containsAll(a));
        assertTrue(a.get(0) < a.get(1), "returned ascending");
    }

    @Test
    @DisplayName("all-neighbours selector returns the whole neighbour set (D-PSGD)")
    void allNeighbors() {
        List<Integer> neigh = Arrays.asList(4, 1, 9, 2);
        var rng = SimRandom.derive(1L, 0, 0);
        List<Integer> chosen = new AllNeighbors().selectPeers(0, 0, 100, neigh, new TestCostView(), rng, 2);
        assertEquals(List.of(1, 2, 4, 9), chosen);
    }

    @Test
    @DisplayName("k larger than neighbourhood returns all neighbours")
    void kExceedsNeighbourhood() {
        List<Integer> neigh = Arrays.asList(3, 7);
        TestCostView costs = new TestCostView().lat(3, 1).lat(7, 2);
        var rng = SimRandom.derive(1L, 0, 0);
        List<Integer> chosen = new SingleFactorLatency().selectPeers(0, 0, 100, neigh, costs, rng, 5);
        assertEquals(List.of(3, 7), chosen);
    }
}
