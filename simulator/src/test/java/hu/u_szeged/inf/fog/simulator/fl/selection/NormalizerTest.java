package hu.u_szeged.inf.fog.simulator.fl.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** P2.5: Eq. norm with the degenerate guard and the divergence cold-start rule. */
class NormalizerTest {

    private static final double EPS = 1e-12;

    @Test
    @DisplayName("min–max maps min→0, max→1")
    void minMaxBasic() {
        double[] out = Normalizer.normalize(new double[] { 10, 20, 30 });
        assertEquals(0.0, out[0], EPS);
        assertEquals(0.5, out[1], EPS);
        assertEquals(1.0, out[2], EPS);
    }

    @Test
    @DisplayName("degenerate guard: max==min ⇒ all 0 (ranking unaffected)")
    void degenerateGuard() {
        double[] out = Normalizer.normalize(new double[] { 7, 7, 7, 7 });
        for (double v : out) {
            assertEquals(0.0, v, EPS);
        }
    }

    @Test
    @DisplayName("cold start: absent elements get 0.5 and are excluded from min/max")
    void coldStartExcluded() {
        // raw present values {2, 4}; index 1 is cold.
        double[] raw = { 2.0, 999.0, 4.0 };
        boolean[] present = { true, false, true };
        double[] out = Normalizer.normalize(raw, present);
        assertEquals(0.0, out[0], EPS);   // min of present
        assertEquals(0.5, out[1], EPS);   // cold -> neutral, NOT pulled by the 999
        assertEquals(1.0, out[2], EPS);   // max of present
    }

    @Test
    @DisplayName("all cold (e.g. round 0): everyone gets the neutral 0.5")
    void allCold() {
        double[] raw = { 1.0, 2.0, 3.0 };
        boolean[] present = { false, false, false };
        double[] out = Normalizer.normalize(raw, present);
        for (double v : out) {
            assertEquals(0.5, v, EPS);
        }
    }
}
