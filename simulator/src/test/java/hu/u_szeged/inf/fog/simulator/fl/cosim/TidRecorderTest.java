package hu.u_szeged.inf.fog.simulator.fl.cosim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** P3.6: TID-dispersion (global + inter-cluster) and consensus distance on known signatures. */
class TidRecorderTest {

    @Test
    @DisplayName("identical models ⇒ zero dispersion and zero consensus distance")
    void allEqualIsZero() {
        int[] labels = { 0, 0, 1, 1 };
        TidRecorder rec = new TidRecorder(labels);
        float[][] sigs = {
                { 1f, 2f }, { 1f, 2f }, { 1f, 2f }, { 1f, 2f }
        };
        rec.record(0, sigs);
        assertEquals(0.0, rec.valueAt(0, "global"), 1e-9);
        assertEquals(0.0, rec.valueAt(0, "inter_cluster"), 1e-9);
        assertEquals(0.0, rec.valueAt(0, "consensus"), 1e-9);
    }

    @Test
    @DisplayName("global vs inter-cluster dispersion on a hand-computable example")
    void handComputed() {
        // 2 clusters: {0,1} and {2,3}. 1-D signatures at 0,0,3,3.
        int[] labels = { 0, 0, 1, 1 };
        TidRecorder rec = new TidRecorder(labels);
        float[][] sigs = { { 0f }, { 0f }, { 3f }, { 3f } };
        rec.record(0, sigs);

        // Pairs: (0,1)=0, (0,2)=3, (0,3)=3, (1,2)=3, (1,3)=3, (2,3)=0 → mean = 12/6 = 2.0
        assertEquals(2.0, rec.valueAt(0, "global"), 1e-9);
        // Inter-cluster pairs: (0,2),(0,3),(1,2),(1,3) = 3 each → mean 3.0
        assertEquals(3.0, rec.valueAt(0, "inter_cluster"), 1e-9);
        // Mean model = 1.5; |0-1.5|+|0-1.5|+|3-1.5|+|3-1.5| = 6 → /4 = 1.5
        assertEquals(1.5, rec.valueAt(0, "consensus"), 1e-9);
    }

    @Test
    @DisplayName("CSV has 3 rows per round with the expected scopes")
    void csvShape() {
        TidRecorder rec = new TidRecorder(new int[] { 0, 1 });
        rec.record(0, new float[][] { { 0f }, { 2f } });
        rec.record(1, new float[][] { { 0f }, { 1f } });
        String csv = rec.toCsv();
        String[] lines = csv.split("\n");
        assertEquals(1 + 2 * 3, lines.length, "header + 3 scopes × 2 rounds");
        assertEquals("round,scope,value", lines[0]);
        assertTrue(csv.contains("0,global,"));
        assertTrue(csv.contains("1,consensus,"));
    }
}
