package hu.u_szeged.inf.fog.simulator.fl.selection;

import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.util.ArrayList;
import java.util.List;

/**
 * A peer-selection policy: at round {@code t}, node {@code i} scores its current
 * neighbours and exchanges with the {@code k} lowest-cost peers, forming the
 * peer set {@code P_i ⊆ N(i)} (§8.2). The seven paper selectors plus the
 * D-PSGD {@code AllNeighbors} selector implement this.
 *
 * <p><b>Determinism.</b> The derived per-decision stream {@code rng} (built by
 * the caller as {@code SimRandom.derive(seed, round, node)}) supplies all
 * stochastic choices, in the documented draw order (see {@code
 * docs/REPRODUCIBILITY.md} §2.1). Score-based selectors draw one tie-break
 * {@code nextDouble()} per candidate in ascending peer-id order
 * ({@link #topKByScore}); {@link RandomK} uses a Fisher–Yates shuffle. The
 * Python mirror ({@code harness/selection.py}, P3.3) reproduces these exactly.</p>
 */
public interface PeerSelectionPolicy {

    /**
     * Selects up to {@code k} peers for {@code node} at {@code round}.
     *
     * @param node the deciding node id.
     * @param round the current round ({@code >= 0}).
     * @param totalRounds total rounds {@code T} (needed for the γ schedule).
     * @param neighbours the node's current active neighbours (any order; the
     *                   policy sorts ascending internally for determinism).
     * @param costs cost view over those neighbours.
     * @param rng the derived per-decision stream for this (seed, round, node).
     * @param k the number of peers to select.
     * @return the selected peer ids (ascending order).
     */
    List<Integer> selectPeers(int node, int round, int totalRounds, List<Integer> neighbours,
                              CostView costs, SplitMix64 rng, int k);

    /** Stable identifier (used in trace columns and telemetry). */
    String id();

    // ------------------------------------------------------------------
    // Shared helper: top-k by ascending score with RNG tie-break.
    // ------------------------------------------------------------------

    /**
     * Selects the {@code k} neighbours with the lowest {@code score[i]}, breaking
     * ties with one {@code nextDouble()} per candidate drawn in ascending
     * peer-id order, then by id as a final deterministic fallback.
     *
     * <p>{@code sortedNeighbours} must be ascending by id; {@code score} is
     * aligned with it. The draw order — one double per candidate, ascending id —
     * is the cross-language contract.</p>
     *
     * @param sortedNeighbours neighbours ascending by id.
     * @param score per-neighbour score (lower = more desirable), aligned.
     * @param rng derived stream.
     * @param k number to select.
     * @return selected ids, returned in ascending order.
     */
    static List<Integer> topKByScore(List<Integer> sortedNeighbours, double[] score,
                                     SplitMix64 rng, int k) {
        int n = sortedNeighbours.size();
        double[] tieBreak = new double[n];
        for (int i = 0; i < n; i++) {
            // One draw per candidate, ascending id order (sortedNeighbours is ascending).
            tieBreak[i] = rng.nextDouble();
        }
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        java.util.Arrays.sort(idx, (a, b) -> {
            int c = Double.compare(score[a], score[b]);
            if (c != 0) {
                return c;
            }
            c = Double.compare(tieBreak[a], tieBreak[b]);
            if (c != 0) {
                return c;
            }
            // Final tiebreak by id (ascending) for full determinism.
            return Integer.compare(sortedNeighbours.get(a), sortedNeighbours.get(b));
        });
        int take = Math.min(k, n);
        List<Integer> chosen = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            chosen.add(sortedNeighbours.get(idx[i]));
        }
        chosen.sort(Integer::compareTo);
        return chosen;
    }

    /** Returns {@code neighbours} sorted ascending by id (a fresh list). */
    static List<Integer> sortedAscending(List<Integer> neighbours) {
        List<Integer> sorted = new ArrayList<>(neighbours);
        sorted.sort(Integer::compareTo);
        return sorted;
    }
}
