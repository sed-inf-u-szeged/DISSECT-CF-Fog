package hu.u_szeged.inf.fog.simulator.fl.selection;

import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.util.ArrayList;
import java.util.List;

/**
 * Random-k selector (§8.2 topology-only control / §9 policy baseline): selects
 * {@code k} uniformly-random neighbours.
 *
 * <p><b>Draw-order contract</b> (mirrored in {@code harness/selection.py}): a
 * Fisher–Yates shuffle over the neighbour list <i>sorted ascending by id</i>,
 * drawing {@code rng.nextInt(i+1)} for {@code i} from {@code n-1} down to 1; the
 * first {@code k} of the shuffled list are taken. Returned ascending.</p>
 */
public final class RandomK implements PeerSelectionPolicy {

    @Override
    public List<Integer> selectPeers(int node, int round, int totalRounds, List<Integer> neighbours,
                                     CostView costs, SplitMix64 rng, int k) {
        List<Integer> shuffled = new ArrayList<>(PeerSelectionPolicy.sortedAscending(neighbours));
        int n = shuffled.size();
        // Fisher–Yates over the ascending list (one nextInt per i, n-1 .. 1).
        for (int i = n - 1; i >= 1; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, tmp);
        }
        int take = Math.min(k, n);
        List<Integer> chosen = new ArrayList<>(shuffled.subList(0, take));
        chosen.sort(Integer::compareTo);
        return chosen;
    }

    @Override
    public String id() {
        return "RANDOM";
    }
}
