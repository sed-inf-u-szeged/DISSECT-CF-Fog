package hu.u_szeged.inf.fog.simulator.fl.selection;

import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.util.List;

/**
 * Single-factor latency selector (§8.2): the composite score with the latency
 * weight set to 1 and the rest to 0 — picks the {@code k} lowest-latency peers.
 * Isolates the contribution of link latency.
 */
public final class SingleFactorLatency implements PeerSelectionPolicy {

    @Override
    public List<Integer> selectPeers(int node, int round, int totalRounds, List<Integer> neighbours,
                                     CostView costs, SplitMix64 rng, int k) {
        List<Integer> sorted = PeerSelectionPolicy.sortedAscending(neighbours);
        if (sorted.isEmpty()) {
            return sorted;
        }
        double[] score = Normalizer.normalize(SelectionTerms.latency(sorted, costs));
        return PeerSelectionPolicy.topKByScore(sorted, score, rng, k);
    }

    @Override
    public String id() {
        return "SINGLE_LATENCY";
    }
}
