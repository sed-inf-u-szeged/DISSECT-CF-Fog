package hu.u_szeged.inf.fog.simulator.fl.merge;

import java.util.Arrays;

/**
 * Fixed uniform mixing {@code ω = 1/(deg_i + 1)} — the merge half of the D-PSGD
 * baseline (§9, Lian2017), where a node averages itself with all neighbours
 * using fixed weights independent of divergence or sample count.
 *
 * <p>This is row-stochastic exactly when the member set is {@code self} plus all
 * of {@code i}'s neighbours (i.e. paired with the {@code AllNeighbors} selector,
 * so {@code member count = deg_i + 1}). The constructor of the gossip run wires
 * that pairing; here we assert it, so a misuse with a {@code k}-subset surfaces
 * immediately rather than producing non-stochastic rows.</p>
 */
public final class FixedUniform implements MergeRule {

    @Override
    public double[] weights(double[] divergence, int[] sampleCount, int[] degree) {
        MergeRule.checkArgs(divergence, sampleCount, degree);
        int m = divergence.length;
        int degSelf = degree[0];
        if (m != degSelf + 1) {
            throw new IllegalArgumentException(
                    "FixedUniform (D-PSGD) requires the member set to be self + all neighbours: "
                            + "member count " + m + " != deg_i+1 " + (degSelf + 1)
                            + " — pair it with the AllNeighbors selector");
        }
        double[] w = new double[m];
        Arrays.fill(w, 1.0 / (degSelf + 1));
        return w;
    }

    @Override
    public String id() {
        return "FIXED_UNIFORM";
    }
}
