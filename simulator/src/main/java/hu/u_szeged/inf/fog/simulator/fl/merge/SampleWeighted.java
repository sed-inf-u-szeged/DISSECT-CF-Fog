package hu.u_szeged.inf.fog.simulator.fl.merge;

/**
 * FedAvg-style sample weighting, {@code ω_ij ∝ n_j} normalised so
 * {@code Σ ω = 1} — the ERQ4 control that isolates the drift factor.
 *
 * <p>{@link DriftSuppressed} changes <i>two</i> things relative to
 * {@link UniformAvg}: it weights members by their local sample count
 * {@code n_j}, and it damps divergent members by {@code 1/(1+D_ij)}. Under a
 * skewed Dirichlet draw the shard sizes span orders of magnitude, so sample
 * weighting alone is a large effect and a uniform-vs-drift-suppressed contrast
 * cannot attribute the gain to drift suppression. This rule holds the sample
 * factor and drops the drift factor, so {@code SAMPLE_WEIGHTED → DRIFT_SUPPRESSED}
 * is a clean one-factor contrast.</p>
 *
 * <p>Ignores divergence and degree.</p>
 */
public final class SampleWeighted implements MergeRule {

    @Override
    public double[] weights(double[] divergence, int[] sampleCount, int[] degree) {
        MergeRule.checkArgs(divergence, sampleCount, degree);
        int m = divergence.length;
        double[] w = new double[m];
        double sum = 0.0;
        for (int i = 0; i < m; i++) {
            if (sampleCount[i] <= 0) {
                throw new IllegalArgumentException("sample counts must be > 0");
            }
            w[i] = sampleCount[i];
            sum += w[i];
        }
        for (int i = 0; i < m; i++) {
            w[i] /= sum;
        }
        return w;
    }

    @Override
    public String id() {
        return "SAMPLE_WEIGHTED";
    }
}
