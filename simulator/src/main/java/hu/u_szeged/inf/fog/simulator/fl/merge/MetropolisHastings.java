package hu.u_szeged.inf.fog.simulator.fl.merge;

/**
 * Degree-corrected Metropolis–Hastings weights (Xiao &amp; Boyd; §8.2 / §9
 * canonical decentralized-averaging baseline). For each selected peer
 * {@code j ≠ i}, {@code ω_ij = 1 / (1 + max(deg_i, deg_j))}; the self weight
 * absorbs the remainder, {@code ω_ii = 1 − Σ_j ω_ij}.
 *
 * <p>Row-stochastic by construction. The self weight is non-negative whenever
 * the selected peers are genuine neighbours of {@code i} (then there are at most
 * {@code deg_i} of them, so {@code Σ_j ω_ij ≤ deg_i/(1+deg_i) < 1}); a negative
 * self weight signals an inconsistent {@code (peers, degree)} input and is
 * rejected rather than silently clamped.</p>
 */
public final class MetropolisHastings implements MergeRule {

    @Override
    public double[] weights(double[] divergence, int[] sampleCount, int[] degree) {
        MergeRule.checkArgs(divergence, sampleCount, degree);
        int m = divergence.length;
        double[] w = new double[m];
        int degSelf = degree[0];
        double peerSum = 0.0;
        for (int j = 1; j < m; j++) {
            int maxDeg = Math.max(degSelf, degree[j]);
            w[j] = 1.0 / (1.0 + maxDeg);
            peerSum += w[j];
        }
        w[0] = 1.0 - peerSum;
        if (w[0] < -1e-12) {
            throw new IllegalArgumentException(
                    "Metropolis-Hastings self weight negative (" + w[0] + "); selected peers ("
                            + (m - 1) + ") inconsistent with self degree (" + degSelf + ")");
        }
        if (w[0] < 0.0) {
            w[0] = 0.0; // clamp sub-ULP negative round-off only
        }
        return w;
    }

    @Override
    public String id() {
        return "METROPOLIS_HASTINGS";
    }
}
