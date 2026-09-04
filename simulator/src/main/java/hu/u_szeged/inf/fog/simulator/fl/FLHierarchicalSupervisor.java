package hu.u_szeged.inf.fog.simulator.fl;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Higher-tier coordinator for hierarchical Federated Learning. Periodically
 * synthesises a global model from the per-region {@link FLAggregator}s and
 * pushes it back into each region's aggregator via
 * {@link FLAggregator#replaceGlobalModel(double[])}.
 *
 * <p>This is the same class that used to live as the {@code
 * static final class HierarchicalSupervisor} inside
 * {@link hu.u_szeged.inf.fog.simulator.fl.demos.FLHierarchicalDemo}; it has
 * been lifted out so it can be reused by the gossip scenario runner (S0
 * baselines) without dragging the demo's setup code along. The original
 * console output (the {@code [Hierarchical] Global aggregation #k ...} line)
 * is preserved byte-for-byte so the existing demo's stdout is unchanged
 * under SYNTHETIC mode.</p>
 *
 * <h2>Generalisation from two to N regions</h2>
 * The original class held two {@link FLAggregator} fields {@code aggA, aggB}
 * and averaged them uniformly. This version keeps the same call shape with
 * a public 2-arg constructor, and additionally exposes an N-region
 * constructor taking a {@link List}. Averaging is uniform across regions
 * (sample-weighted averaging is a one-line change deferred to future work,
 * named in the manuscript).
 *
 * <h2>Behavioural parity with the inner class</h2>
 * <ul>
 *   <li>Subscribes one {@code interval} from "now" (gives regional
 *       aggregators a chance to complete their first batch of edge rounds).</li>
 *   <li>On each {@link #tick(long)}, calls {@link #performGlobalSync(String)
 *       performGlobalSync("scheduled")} and increments the global-round
 *       counter; {@link #unsubscribe()} on reaching {@code maxGlobalRounds}.</li>
 *   <li>{@link #forceFinalSync()} fires one extra sync with reason "final".</li>
 *   <li>If both/all regional models are empty, the sync logs "skipped" and
 *       returns — same as before.</li>
 *   <li>Console log line format is preserved bit-for-bit for the 2-region
 *       case so {@link hu.u_szeged.inf.fog.simulator.fl.demos.FLHierarchicalDemo}
 *       stdout is unchanged.</li>
 * </ul>
 *
 * <h2>Optional finished-callback hook</h2>
 * The gossip scenario runner (P6) needs to know when the supervisor has
 * exhausted its rounds. A {@link Runnable} can be registered via
 * {@link #setFinishedCallback(Runnable)} and is invoked on the unsubscribe
 * tick. The existing demo does not set this callback (it has its own
 * finished-callback chain through the regional aggregators) so the demo's
 * behaviour is unaffected.
 */
public class FLHierarchicalSupervisor extends Timed {

    private final List<FLAggregator> aggregators;
    private final long interval;
    private final int maxGlobalRounds;
    private int globalRound = 0;
    private Runnable finishedCallback;

    /**
     * Two-region constructor — call-compatible with the original inner-class
     * signature. Produces byte-identical log output for the 2-region case
     * (so the existing {@code FLHierarchicalDemo} stdout is unchanged).
     *
     * @param a the first regional aggregator.
     * @param b the second regional aggregator.
     * @param interval ticks between scheduled syncs.
     * @param maxGlobalRounds number of scheduled syncs before unsubscribe.
     */
    public FLHierarchicalSupervisor(FLAggregator a, FLAggregator b,
                                    long interval, int maxGlobalRounds) {
        this(Arrays.asList(a, b), interval, maxGlobalRounds);
    }

    /**
     * N-region constructor for future use (S0 with more than two regional
     * aggregators). The list is copied defensively so subsequent caller
     * mutations cannot reorder regions mid-run.
     *
     * @param regional regional aggregators (size &ge; 1).
     * @param interval ticks between scheduled syncs.
     * @param maxGlobalRounds number of scheduled syncs before unsubscribe.
     */
    public FLHierarchicalSupervisor(List<FLAggregator> regional,
                                    long interval, int maxGlobalRounds) {
        if (regional == null || regional.isEmpty()) {
            throw new IllegalArgumentException("regional aggregators must be non-empty");
        }
        this.aggregators = Collections.unmodifiableList(new ArrayList<>(regional));
        this.interval = interval;
        this.maxGlobalRounds = maxGlobalRounds;
        // First fire one interval from now — preserves the original timing.
        subscribe(interval);
    }

    /** Register a callback fired when the supervisor unsubscribes (gossip
     *  scenario runner uses this; the existing demo does not). */
    public void setFinishedCallback(Runnable r) {
        this.finishedCallback = r;
    }

    @Override
    public void tick(long fires) {
        performGlobalSync("scheduled");
        globalRound++;
        if (globalRound >= maxGlobalRounds) {
            unsubscribe();
            if (finishedCallback != null) {
                finishedCallback.run();
            }
        }
    }

    /** Force one extra synthesis outside the schedule. */
    public void forceFinalSync() {
        performGlobalSync("final");
    }

    /** Number of global syncs already performed (test/runner introspection). */
    public int getGlobalRound() {
        return globalRound;
    }

    private void performGlobalSync(String reason) {
        // Pull each regional model. Compute the min length so a shorter region
        // (defensive — shouldn't happen) cannot index out of bounds.
        double[][] models = new double[aggregators.size()][];
        int dim = Integer.MAX_VALUE;
        for (int i = 0; i < aggregators.size(); i++) {
            models[i] = aggregators.get(i).getGlobalModel();
            if (models[i].length < dim) dim = models[i].length;
        }
        if (dim == 0 || dim == Integer.MAX_VALUE) {
            System.out.println("[Hierarchical] Sync skipped (" + reason + "): empty model.");
            return;
        }

        // Uniform average across regions (sample-weighted is future work).
        double[] avg = new double[dim];
        double inv = 1.0 / aggregators.size();
        for (int i = 0; i < dim; i++) {
            double s = 0.0;
            for (double[] m : models) s += m[i];
            avg[i] = s * inv;
        }
        for (FLAggregator a : aggregators) {
            a.replaceGlobalModel(avg);
        }

        // Log line: preserve the exact two-region format for parity with the
        // old inner-class output. For N>2 fall back to a list of L2 norms.
        if (aggregators.size() == 2) {
            System.out.printf("[Hierarchical] Global aggregation #%d (%s) | "
                            + "edge-A L2=%.6f, edge-B L2=%.6f, avg L2=%.6f%n",
                    globalRound + 1, reason, l2(models[0]), l2(models[1]), l2(avg));
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < models.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.format("edge-%d L2=%.6f", i, l2(models[i])));
            }
            System.out.printf("[Hierarchical] Global aggregation #%d (%s) | %s, avg L2=%.6f%n",
                    globalRound + 1, reason, sb, l2(avg));
        }
    }

    private static double l2(double[] v) {
        double s = 0.0;
        for (double x : v) s += x * x;
        return Math.sqrt(s);
    }
}
