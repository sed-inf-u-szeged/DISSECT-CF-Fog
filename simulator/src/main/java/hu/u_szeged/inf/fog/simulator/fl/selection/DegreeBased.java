package hu.u_szeged.inf.fog.simulator.fl.selection;

import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.util.List;

/**
 * Degree-based selector (§8.2 topology-only control / §9 policy baseline):
 * selects the {@code k} most-connected neighbours (highest topology degree).
 * Implemented as a top-k over the score {@code -degree(j)} so the shared
 * RNG tie-break ({@link PeerSelectionPolicy#topKByScore}) applies identically to
 * the other selectors.
 */
public final class DegreeBased implements PeerSelectionPolicy {

    @Override
    public List<Integer> selectPeers(int node, int round, int totalRounds, List<Integer> neighbours,
                                     CostView costs, SplitMix64 rng, int k) {
        List<Integer> sorted = PeerSelectionPolicy.sortedAscending(neighbours);
        int n = sorted.size();
        if (n == 0) {
            return sorted;
        }
        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            score[i] = -(double) costs.degree(sorted.get(i)); // higher degree => lower score
        }
        return PeerSelectionPolicy.topKByScore(sorted, score, rng, k);
    }

    @Override
    public String id() {
        return "DEGREE";
    }
}
