package hu.u_szeged.inf.fog.simulator.fl.selection;

import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.util.List;

/**
 * Single-factor load selector (§8.2): the composite score with the load weight
 * set to 1 — picks the {@code k} least-loaded peers, penalising stragglers.
 */
public final class SingleFactorLoad implements PeerSelectionPolicy {

    @Override
    public List<Integer> selectPeers(int node, int round, int totalRounds, List<Integer> neighbours,
                                     CostView costs, SplitMix64 rng, int k) {
        List<Integer> sorted = PeerSelectionPolicy.sortedAscending(neighbours);
        if (sorted.isEmpty()) {
            return sorted;
        }
        double[] score = Normalizer.normalize(SelectionTerms.load(sorted, costs));
        return PeerSelectionPolicy.topKByScore(sorted, score, rng, k);
    }

    @Override
    public String id() {
        return "SINGLE_LOAD";
    }
}
