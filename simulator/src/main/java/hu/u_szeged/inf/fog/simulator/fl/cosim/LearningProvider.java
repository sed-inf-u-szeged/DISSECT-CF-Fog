package hu.u_szeged.inf.fog.simulator.fl.cosim;

import hu.u_szeged.inf.fog.simulator.fl.FLLearningSource;

/**
 * Supplies the learning result (accuracy, loss, update norm, payload bytes,
 * train time, model signature) for a {@code (node, round)} cell to the
 * simulator's event handlers (§8.4). This is the runtime half of the
 * learning-source switch ({@link FLLearningSource}); it lets Pass-3 replay
 * consume Pass-2 outcomes without changing the discrete-event physics, while
 * keeping the legacy synthetic path the default.
 *
 * <p><b>Read-first (D4).</b> In TRACE mode the trace is authoritative for all
 * replayed physics; the provider only <i>supplies</i> what Pass-2 recorded.</p>
 *
 * <p>For the centralized/hierarchical path, the global model is addressed with
 * the sentinel node id {@link #GLOBAL_NODE}.</p>
 */
public interface LearningProvider {

    /** Sentinel node id addressing the single global model (centralized/hierarchical). */
    int GLOBAL_NODE = -1;

    /** Which learning source this provider represents. */
    FLLearningSource source();

    /**
     * The learning outcome for {@code (node, round)}.
     *
     * @throws UnsupportedOperationException if the source does not serve metrics
     *         this way (e.g. the synthetic provider, whose accuracy is computed
     *         inline by {@code EvaluationEvent} to stay byte-identical).
     */
    LearningOutcome outcome(int node, int round);

    /** The model signature for {@code (node, round)}, or {@code null} if unavailable. */
    float[] signature(int node, int round);
}
