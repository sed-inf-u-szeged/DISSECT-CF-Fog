package hu.u_szeged.inf.fog.simulator.fl.selection;

import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.util.List;

/**
 * Single-factor bandwidth selector (§8.2): the composite score with the
 * bandwidth weight set to 1 — picks the {@code k} cheapest-to-reach peers
 * (lowest transfer cost {@code payload/bandwidth}), favouring traffic/energy
 * economy.
 */
public final class SingleFactorBandwidth implements PeerSelectionPolicy {

    @Override
    public List<Integer> selectPeers(int node, int round, int totalRounds, List<Integer> neighbours,
                                     CostView costs, SplitMix64 rng, int k) {
        List<Integer> sorted = PeerSelectionPolicy.sortedAscending(neighbours);
        if (sorted.isEmpty()) {
            return sorted;
        }
        double[] score = Normalizer.normalize(SelectionTerms.bandwidth(sorted, costs));
        return PeerSelectionPolicy.topKByScore(sorted, score, rng, k);
    }

    @Override
    public String id() {
        return "SINGLE_BANDWIDTH";
    }
}
