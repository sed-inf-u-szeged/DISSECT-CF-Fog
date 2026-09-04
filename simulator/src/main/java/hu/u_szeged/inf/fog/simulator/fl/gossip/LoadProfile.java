package hu.u_szeged.inf.fog.simulator.fl.gossip;

import hu.u_szeged.inf.fog.simulator.util.SimRandom;
import hu.u_szeged.inf.fog.simulator.util.SplitMix64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-node compute load {@code C_j} (§8.4 Pass 1), defined to avoid the circular
 * dependence of load on training durations: a static per-node base load (from
 * the device profile tier) plus a scheduled, seed-deterministic background
 * component drawn once per node-round.
 *
 * <p><b>Draw-order contract</b> (docs/REPRODUCIBILITY.md §2.1, mirrored in the
 * Python harness): the background component uses a <i>dedicated</i> stream
 * {@code derive(seed*31+7, round, node)} — a single {@code nextDouble()} per
 * node-round — so it cannot alias with the selection / tie-break draws on the
 * same {@code (round, node)}. The load is {@code clamp01(base[node] +
 * amplitude · draw)}.</p>
 *
 * <p>The full table is generated in Pass 1 and embedded in
 * {@code system_trace.json} so Java and Python read identical {@code C_j}.</p>
 */
public final class LoadProfile {

    /** Stream-key offset multiplier (must match {@code harness} and §2.1). */
    static final long STREAM_MULT = 31L;
    /** Stream-key offset addend (must match {@code harness} and §2.1). */
    static final long STREAM_ADD = 7L;

    private final double[] baseLoad;
    private final double amplitude;
    private final long seed;

    /**
     * Creates a schedule from static per-node base loads plus a seeded
     * background component.
     *
     * @param baseLoad per-node static base load (length n, each in [0,1]).
     * @param amplitude background-load amplitude ({@code >= 0}); the scheduled
     *                  component is {@code amplitude · U[0,1)}.
     * @param seed campaign seed.
     */
    public LoadProfile(double[] baseLoad, double amplitude, long seed) {
        if (baseLoad == null || baseLoad.length == 0) {
            throw new IllegalArgumentException("baseLoad must be non-empty");
        }
        if (amplitude < 0.0) {
            throw new IllegalArgumentException("amplitude must be >= 0");
        }
        this.baseLoad = baseLoad.clone();
        this.amplitude = amplitude;
        this.seed = seed;
    }

    /** Number of nodes. */
    public int size() {
        return baseLoad.length;
    }

    /**
     * The compute load {@code C_node} at {@code round}, in {@code [0,1]}.
     * Deterministic in {@code (seed, round, node)} via the dedicated stream.
     */
    public double load(int node, int round) {
        SplitMix64 rng = SimRandom.derive(seed * STREAM_MULT + STREAM_ADD, round, node);
        double draw = rng.nextDouble();
        double v = baseLoad[node] + amplitude * draw;
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    /**
     * The full per-node per-round table {@code [node][round]} for Pass-1 export.
     *
     * @param rounds number of rounds ({@code >= 1}).
     */
    public double[][] table(int rounds) {
        if (rounds < 1) {
            throw new IllegalArgumentException("rounds must be >= 1");
        }
        double[][] out = new double[baseLoad.length][rounds];
        for (int node = 0; node < baseLoad.length; node++) {
            for (int r = 0; r < rounds; r++) {
                out[node][r] = load(node, r);
            }
        }
        return out;
    }

    /** The table as a {@code node-id-string -> per-round} map (system_trace schema). */
    public Map<String, double[]> toLoadProfilesMap(int rounds) {
        double[][] tbl = table(rounds);
        Map<String, double[]> map = new LinkedHashMap<>();
        for (int node = 0; node < tbl.length; node++) {
            map.put(Integer.toString(node), tbl[node]);
        }
        return map;
    }
}
