package hu.u_szeged.inf.fog.simulator.fl.selection;

import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.util.List;

/**
 * Single-factor divergence selector (§8.2): the composite score with the
 * divergence weight set to {@code +1} — prefers the {@code k} most-similar peers
 * (lowest cached divergence). Cold-start peers take the neutral 0.5 and are
 * excluded from the min–max ({@link Normalizer}). Note the weight is fixed
 * {@code +1} (no γ schedule); the explore/exploit lever lives in
 * {@link CompositeScore}.
 */
public final class SingleFactorDivergence implements PeerSelectionPolicy {

    @Override
    public List<Integer> selectPeers(int node, int round, int totalRounds, List<Integer> neighbours,
                                     CostView costs, SplitMix64 rng, int k) {
        List<Integer> sorted = PeerSelectionPolicy.sortedAscending(neighbours);
        int n = sorted.size();
        if (n == 0) {
            return sorted;
        }
        boolean[] present = new boolean[n];
        double[] score = Normalizer.normalize(SelectionTerms.divergence(sorted, costs, present), present);
        return PeerSelectionPolicy.topKByScore(sorted, score, rng, k);
    }

    @Override
    public String id() {
        return "SINGLE_DIVERGENCE";
    }
}
