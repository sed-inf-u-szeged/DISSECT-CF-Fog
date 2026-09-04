package hu.u_szeged.inf.fog.simulator.fl.merge;

/**
 * Drift-suppressed merge (§8.2, Eq. merge): a proximity-weighted average where a
 * far-drifted, stale, or anomalous peer contributes less,
 * {@code ω_ij ∝ n_j / (1 + D_ij)} normalised so {@code Σ ω = 1}. With equal
 * divergences this recovers FedAvg-style sample weighting. {@code D} is the
 * <i>fresh</i> divergence on the just-exchanged signatures (self {@code D=0}).
 *
 * <p>The weight is monotonically decreasing in {@code D_ij} (holding {@code n_j}
 * fixed): a more divergent peer is down-weighted. Introduces no tunable
 * parameter beyond the functional form.</p>
 */
public final class DriftSuppressed implements MergeRule {

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
            if (divergence[i] < 0.0) {
                throw new IllegalArgumentException("divergence must be >= 0");
            }
            w[i] = sampleCount[i] / (1.0 + divergence[i]);
            sum += w[i];
        }
        for (int i = 0; i < m; i++) {
            w[i] /= sum;
        }
        return w;
    }

    @Override
    public String id() {
        return "DRIFT_SUPPRESSED";
    }
}
