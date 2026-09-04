package hu.u_szeged.inf.fog.simulator.fl.selection;

import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.util.List;

/**
 * The adaptive composite selector of Eq. score (§8.2):
 * {@code S_ij = α·L̃_ij + β·C̃_j + γ(t)·D̂̃_ij + δ·B̃_ij}, where every term is
 * min–max normalised within the neighbourhood ({@link Normalizer}) and γ(t)
 * follows a {@link GammaSchedule} (explore early, exploit late by default). The
 * {@code k} lowest-scoring peers are selected, ties broken by the derived RNG
 * ({@link PeerSelectionPolicy#topKByScore}).
 *
 * <p>Default weights are α = β = δ = 0.25 and |γ| = 0.25, so the four terms act
 * on comparable scales (|α|+|β|+|γ|+|δ| = 1). The constructor also accepts
 * explicit weights for the ±0.15 weight-perturbation sensitivity cells; in that
 * case all weights (including the signed γ magnitude) are renormalised so the
 * sum of absolute weights is 1, keeping the scales comparable.</p>
 */
public final class CompositeScore implements PeerSelectionPolicy {

    private final double alpha;
    private final double beta;
    private final double delta;
    private final double gammaMagnitude;
    private final GammaSchedule schedule;
    private final String id;

    /** Equal-weight default (α = β = δ = 0.25, |γ| = 0.25) with the given schedule. */
    public CompositeScore(GammaSchedule schedule) {
        this(0.25, 0.25, 0.25, GammaSchedule.DEFAULT_MAGNITUDE, schedule);
    }

    /**
     * Explicit weights (for the sensitivity cells). Weights are renormalised so
     * {@code |α|+|β|+|γ|+|δ| = 1}.
     *
     * @param alpha latency weight ({@code >= 0}).
     * @param beta load weight ({@code >= 0}).
     * @param delta bandwidth weight ({@code >= 0}).
     * @param gammaMagnitude divergence weight magnitude ({@code >= 0}); its sign
     *                       comes from the {@code schedule} per round.
     * @param schedule the γ(t) schedule.
     */
    public CompositeScore(double alpha, double beta, double delta, double gammaMagnitude,
                          GammaSchedule schedule) {
        if (alpha < 0 || beta < 0 || delta < 0 || gammaMagnitude < 0) {
            throw new IllegalArgumentException("composite weights must be >= 0");
        }
        double sum = Math.abs(alpha) + Math.abs(beta) + Math.abs(delta) + Math.abs(gammaMagnitude);
        if (sum == 0.0) {
            throw new IllegalArgumentException("composite weights cannot all be zero");
        }
        this.alpha = alpha / sum;
        this.beta = beta / sum;
        this.delta = delta / sum;
        this.gammaMagnitude = gammaMagnitude / sum;
        this.schedule = schedule;
        this.id = "COMPOSITE";
    }

    @Override
    public List<Integer> selectPeers(int node, int round, int totalRounds, List<Integer> neighbours,
                                     CostView costs, SplitMix64 rng, int k) {
        List<Integer> sorted = PeerSelectionPolicy.sortedAscending(neighbours);
        int n = sorted.size();
        if (n == 0) {
            return sorted;
        }
        boolean[] present = new boolean[n];
        double[] lTilde = Normalizer.normalize(SelectionTerms.latency(sorted, costs));
        double[] cTilde = Normalizer.normalize(SelectionTerms.load(sorted, costs));
        double[] dTilde = Normalizer.normalize(SelectionTerms.divergence(sorted, costs, present), present);
        double[] bTilde = Normalizer.normalize(SelectionTerms.bandwidth(sorted, costs));

        double gamma = schedule.gamma(round, totalRounds, gammaMagnitude);
        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            score[i] = alpha * lTilde[i] + beta * cTilde[i] + gamma * dTilde[i] + delta * bTilde[i];
        }
        return PeerSelectionPolicy.topKByScore(sorted, score, rng, k);
    }

    @Override
    public String id() {
        return id;
    }
}
