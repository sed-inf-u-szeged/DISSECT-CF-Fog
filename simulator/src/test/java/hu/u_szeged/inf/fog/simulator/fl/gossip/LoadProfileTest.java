package hu.u_szeged.inf.fog.simulator.fl.gossip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import hu.u_szeged.inf.fog.simulator.util.SplitMix64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** P2.7: deterministic, in-range C_j from a dedicated stream separate from selection. */
class LoadProfileTest {

    @Test
    @DisplayName("deterministic in (seed, round, node) and within [0,1]")
    void deterministicAndClamped() {
        double[] base = { 0.2, 0.8, 0.5 };
        LoadProfile lp = new LoadProfile(base, 0.3, 1234L);
        for (int node = 0; node < 3; node++) {
            for (int r = 0; r < 5; r++) {
                double a = lp.load(node, r);
                double b = lp.load(node, r);
                assertEquals(a, b, 0.0, "same (node,round) reproducible");
                assertTrue(a >= 0.0 && a <= 1.0, "load in [0,1]");
            }
        }
    }

    @Test
    @DisplayName("matches the documented stream derive(seed*31+7, round, node)")
    void matchesDocumentedStream() {
        double[] base = { 0.4 };
        double amplitude = 0.5;
        long seed = 99L;
        LoadProfile lp = new LoadProfile(base, amplitude, seed);
        int round = 2;
        int node = 0;
        SplitMix64 rng = SimRandom.derive(seed * 31L + 7L, round, node);
        double expected = base[0] + amplitude * rng.nextDouble();
        assertEquals(expected, lp.load(node, round), 1e-15);
    }

    @Test
    @DisplayName("background stream does not alias the selection stream on the same (round,node)")
    void streamSeparation() {
        long seed = 5L;
        int round = 3;
        int node = 1;
        double loadDraw = SimRandom.derive(seed * 31L + 7L, round, node).nextDouble();
        double selectionDraw = SimRandom.derive(seed, round, node).nextDouble();
        assertNotEquals(loadDraw, selectionDraw, "load and selection streams must differ");
    }

    @Test
    @DisplayName("table() shape and value agreement with load()")
    void tableShape() {
        double[] base = { 0.1, 0.2 };
        LoadProfile lp = new LoadProfile(base, 0.2, 7L);
        double[][] tbl = lp.table(4);
        assertEquals(2, tbl.length);
        assertEquals(4, tbl[0].length);
        for (int node = 0; node < 2; node++) {
            for (int r = 0; r < 4; r++) {
                assertEquals(lp.load(node, r), tbl[node][r], 0.0);
            }
        }
        assertEquals(2, lp.toLoadProfilesMap(4).size());
    }
}
