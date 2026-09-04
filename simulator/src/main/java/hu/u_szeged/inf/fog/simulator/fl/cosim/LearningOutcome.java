package hu.u_szeged.inf.fog.simulator.fl.cosim;

/**
 * The learning result for one {@code (node, round)} cell, as recorded by the
 * Pass-2 harness and consumed by Pass-3 replay (§8.4). These are the
 * non-signature columns of {@code learning_trace.csv}; the model signature is
 * served separately by {@link TraceReader#signature(int, int)} (it lives in the
 * float32 sidecar). Immutable value type shared by {@link LearningProvider}.
 */
public final class LearningOutcome {

    /**
     * Accuracy of the local fit on the node's <b>own training shard</b> — a
     * training diagnostic, not a reportable figure. Under a skewed Dirichlet
     * draw a node holding two classes trivially reaches ≈1.0 here.
     */
    public final double acc;
    /**
     * Accuracy on the disjoint <b>held-out</b> pool — the reportable figure
     * (§9 metrics), and what the replay's evaluation step surfaces.
     * {@code NaN} on rounds outside the harness's evaluation cadence.
     */
    public final double heldoutAcc;
    /** Local loss. */
    public final double loss;
    /** Update norm ‖Δw‖₂. */
    public final double deltaNorm;
    /** Model payload size in bytes (drives traffic/energy in Pass-3). */
    public final long payloadBytes;
    /** Measured local-training time (ms) — used for the Track-B calibration check. */
    public final double trainTimeMs;
    /** Local sample count {@code n_node}. */
    public final int nSamples;
    /** Selected peer ids, ascending (empty for centralized/hierarchical). */
    public final int[] peers;
    /** Merge weights aligned with {@code [self, peers…]} (self first); empty if none. */
    public final double[] mergeWeights;
    /** Max staleness age over cached peers at selection time (diagnostic). */
    public final int stalenessMax;

    /** Creates an immutable outcome; null {@code peers}/{@code mergeWeights} become empty. */
    public LearningOutcome(double acc, double heldoutAcc, double loss, double deltaNorm,
                           long payloadBytes, double trainTimeMs, int nSamples, int[] peers,
                           double[] mergeWeights, int stalenessMax) {
        this.acc = acc;
        this.heldoutAcc = heldoutAcc;
        this.loss = loss;
        this.deltaNorm = deltaNorm;
        this.payloadBytes = payloadBytes;
        this.trainTimeMs = trainTimeMs;
        this.nSamples = nSamples;
        this.peers = peers == null ? new int[0] : peers;
        this.mergeWeights = mergeWeights == null ? new double[0] : mergeWeights;
        this.stalenessMax = stalenessMax;
    }
}
