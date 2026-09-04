package hu.u_szeged.inf.fog.simulator.fl.selection;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple map-backed {@link CostView} for selection unit tests. Unset terms
 * default to 0; divergence is "cold" unless explicitly set via
 * {@link #div(int, double)}.
 */
final class TestCostView implements CostView {

    private final Map<Integer, Double> latency = new HashMap<>();
    private final Map<Integer, Double> load = new HashMap<>();
    private final Map<Integer, Double> divergence = new HashMap<>();
    private final Map<Integer, Double> bandwidth = new HashMap<>();
    private final Map<Integer, Integer> degree = new HashMap<>();

    TestCostView lat(int j, double v) {
        latency.put(j, v);
        return this;
    }

    TestCostView load(int j, double v) {
        load.put(j, v);
        return this;
    }

    TestCostView div(int j, double v) {
        divergence.put(j, v);
        return this;
    }

    TestCostView bw(int j, double v) {
        bandwidth.put(j, v);
        return this;
    }

    TestCostView deg(int j, int v) {
        degree.put(j, v);
        return this;
    }

    @Override
    public double latency(int neighbour) {
        return latency.getOrDefault(neighbour, 0.0);
    }

    @Override
    public double load(int neighbour) {
        return load.getOrDefault(neighbour, 0.0);
    }

    @Override
    public double divergence(int neighbour) {
        return divergence.getOrDefault(neighbour, 0.0);
    }

    @Override
    public boolean hasDivergence(int neighbour) {
        return divergence.containsKey(neighbour);
    }

    @Override
    public double bandwidthCost(int neighbour) {
        return bandwidth.getOrDefault(neighbour, 0.0);
    }

    @Override
    public int degree(int neighbour) {
        return degree.getOrDefault(neighbour, 0);
    }
}
