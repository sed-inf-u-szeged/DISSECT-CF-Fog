package hu.u_szeged.inf.fog.simulator.fl.selection;

import java.util.List;

/**
 * Package-private helpers that assemble raw cost-term arrays from a
 * {@link CostView} over a sorted neighbour list, aligned by index. Used by the
 * score-based selectors before normalisation ({@link Normalizer}).
 */
final class SelectionTerms {

    private SelectionTerms() {
    }

    static double[] latency(List<Integer> sorted, CostView costs) {
        double[] out = new double[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            out[i] = costs.latency(sorted.get(i));
        }
        return out;
    }

    static double[] load(List<Integer> sorted, CostView costs) {
        double[] out = new double[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            out[i] = costs.load(sorted.get(i));
        }
        return out;
    }

    static double[] bandwidth(List<Integer> sorted, CostView costs) {
        double[] out = new double[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            out[i] = costs.bandwidthCost(sorted.get(i));
        }
        return out;
    }

    /** Raw divergence values; absent (cold) entries left as 0 but flagged in {@code present}. */
    static double[] divergence(List<Integer> sorted, CostView costs, boolean[] present) {
        double[] out = new double[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            int j = sorted.get(i);
            present[i] = costs.hasDivergence(j);
            out[i] = present[i] ? costs.divergence(j) : 0.0;
        }
        return out;
    }
}
