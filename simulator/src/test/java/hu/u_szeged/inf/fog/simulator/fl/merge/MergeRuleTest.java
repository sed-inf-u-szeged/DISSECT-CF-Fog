package hu.u_szeged.inf.fog.simulator.fl.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2.6 / DoD items 2, 4, 12: merge-weight invariants — Σω = 1 (within 1e-12) for
 * all rules; MH row-stochastic &amp; non-negative; drift-suppressed monotone
 * decreasing in D; D-PSGD fixed-mixing row-stochastic.
 */
class MergeRuleTest {

    private static final double EPS = 1e-12;

    private static double sum(double[] w) {
        double s = 0.0;
        for (double x : w) {
            s += x;
        }
        return s;
    }

    @Test
    @DisplayName("all rules: weights sum to 1 within 1e-12")
    void allRulesSumToOne() {
        // members = [self, p0, p1, p2]; self degree 3 (so MH self weight >= 0).
        double[] div = { 0.0, 0.2, 1.5, 0.7 };
        int[] n = { 10, 5, 8, 3 };
        int[] deg = { 3, 2, 4, 2 };

        MergeRule[] rules = { new UniformAvg(), new DriftSuppressed(), new MetropolisHastings() };
        for (MergeRule rule : rules) {
            double[] w = rule.weights(div, n, deg);
            assertEquals(1.0, sum(w), EPS, rule.id() + " must sum to 1");
            for (double x : w) {
                assertTrue(x >= -EPS, rule.id() + " weights must be non-negative");
            }
        }
    }

    @Test
    @DisplayName("uniform: every member weight equals 1/m")
    void uniformEqualWeights() {
        double[] div = { 0.0, 0.2, 1.5 };
        int[] n = { 10, 5, 8 };
        int[] deg = { 2, 2, 2 };
        double[] w = new UniformAvg().weights(div, n, deg);
        for (double x : w) {
            assertEquals(1.0 / 3.0, x, EPS);
        }
    }

    @Test
    @DisplayName("drift-suppressed: ω ∝ n_j/(1+D); equal D recovers sample weighting")
    void driftSuppressedFormula() {
        // Equal divergences -> weights proportional to sample counts (FedAvg-style).
        double[] div = { 0.0, 0.0, 0.0 };
        int[] n = { 2, 3, 5 };
        int[] deg = { 2, 2, 2 };
        double[] w = new DriftSuppressed().weights(div, n, deg);
        assertEquals(1.0, sum(w), EPS);
        assertEquals(2.0 / 10.0, w[0], 1e-9);
        assertEquals(3.0 / 10.0, w[1], 1e-9);
        assertEquals(5.0 / 10.0, w[2], 1e-9);
    }

    @Test
    @DisplayName("drift-suppressed: weight strictly decreases as a peer's D rises (n fixed)")
    void driftSuppressedMonotoneInD() {
        int[] n = { 4, 4, 4 };
        int[] deg = { 2, 2, 2 };
        double[] prevPeerWeight = { Double.POSITIVE_INFINITY };
        double lastW = Double.POSITIVE_INFINITY;
        for (double d : new double[] { 0.0, 0.5, 1.0, 2.0, 5.0 }) {
            double[] div = { 0.0, d, 0.0 };       // vary only peer-0's divergence
            double[] w = new DriftSuppressed().weights(div, n, deg);
            assertTrue(w[1] < lastW, "peer weight must decrease as its D rises (d=" + d + ")");
            lastW = w[1];
        }
        prevPeerWeight[0] = lastW; // silence unused warning intent
        assertTrue(prevPeerWeight[0] >= 0.0);
    }

    @Test
    @DisplayName("Metropolis–Hastings: ω_ij=1/(1+max(deg)), self absorbs remainder, row-stochastic")
    void metropolisHastingsRowStochastic() {
        double[] div = { 0.0, 0.3, 0.9 };
        int[] n = { 1, 1, 1 };
        int[] deg = { 2, 3, 2 };  // self deg 2, peers deg 3 and 2
        double[] w = new MetropolisHastings().weights(div, n, deg);
        // peer0: 1/(1+max(2,3)) = 1/4 ; peer1: 1/(1+max(2,2)) = 1/3
        assertEquals(1.0 / 4.0, w[1], 1e-12);
        assertEquals(1.0 / 3.0, w[2], 1e-12);
        assertEquals(1.0 - (1.0 / 4.0 + 1.0 / 3.0), w[0], 1e-12);
        assertEquals(1.0, sum(w), EPS);
        assertTrue(w[0] >= 0.0, "self weight non-negative");
    }

    @Test
    @DisplayName("Metropolis–Hastings on a ring (deg 2, k=2): row-stochastic, self ≥ 0")
    void metropolisHastingsRing() {
        // Ring node: self deg 2, both peers deg 2.
        double[] div = { 0.0, 0.1, 0.4 };
        int[] n = { 1, 1, 1 };
        int[] deg = { 2, 2, 2 };
        double[] w = new MetropolisHastings().weights(div, n, deg);
        assertEquals(1.0 / 3.0, w[1], 1e-12);
        assertEquals(1.0 / 3.0, w[2], 1e-12);
        assertEquals(1.0 / 3.0, w[0], 1e-12);
        assertEquals(1.0, sum(w), EPS);
    }

    @Test
    @DisplayName("D-PSGD fixed-uniform: row-stochastic with self+all-neighbours, rejects k-subset")
    void fixedUniformRowStochastic() {
        // self degree 3, members = self + 3 neighbours (AllNeighbors config).
        double[] div = { 0.0, 0.2, 0.4, 0.6 };
        int[] n = { 1, 1, 1, 1 };
        int[] deg = { 3, 2, 4, 2 };
        double[] w = new FixedUniform().weights(div, n, deg);
        assertEquals(1.0, sum(w), EPS);
        for (double x : w) {
            assertEquals(1.0 / 4.0, x, EPS);
        }
        // Misuse: a k-subset (members < deg+1) must be rejected.
        double[] div2 = { 0.0, 0.2 };
        int[] n2 = { 1, 1 };
        int[] deg2 = { 3, 2 };
        assertThrows(IllegalArgumentException.class,
                () -> new FixedUniform().weights(div2, n2, deg2));
    }
}
