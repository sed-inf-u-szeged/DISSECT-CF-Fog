package hu.u_szeged.inf.fog.simulator.fl.selection;

import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.util.List;

/**
 * All-neighbour selector — the selector half of the D-PSGD baseline (§9,
 * Lian2017): returns the full active neighbour set, bypassing scoring (the
 * {@code k} argument and {@code rng} are ignored). Paired with a fixed-mixing
 * merge rule ({@code FixedUniform} or {@code MetropolisHastings}) it realises
 * decentralized SGD's all-neighbour exchange.
 */
public final class AllNeighbors implements PeerSelectionPolicy {

    @Override
    public List<Integer> selectPeers(int node, int round, int totalRounds, List<Integer> neighbours,
                                     CostView costs, SplitMix64 rng, int k) {
        return PeerSelectionPolicy.sortedAscending(neighbours);
    }

    @Override
    public String id() {
        return "ALL_NEIGHBORS";
    }
}
