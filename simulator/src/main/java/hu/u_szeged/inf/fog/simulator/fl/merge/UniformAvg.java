package hu.u_szeged.inf.fog.simulator.fl.merge;

import java.util.Arrays;

/**
 * Uniform averaging: {@code ω = 1/(|P|+1)} for self and each selected peer
 * (§8.2, S2 merge ablation). Ignores divergence, sample counts, and degree.
 */
public final class UniformAvg implements MergeRule {

    @Override
    public double[] weights(double[] divergence, int[] sampleCount, int[] degree) {
        MergeRule.checkArgs(divergence, sampleCount, degree);
        int m = divergence.length;
        double[] w = new double[m];
        Arrays.fill(w, 1.0 / m);
        return w;
    }

    @Override
    public String id() {
        return "UNIFORM";
    }
}
