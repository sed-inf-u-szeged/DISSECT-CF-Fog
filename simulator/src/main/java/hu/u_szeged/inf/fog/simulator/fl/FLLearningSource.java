package hu.u_szeged.inf.fog.simulator.fl;

/**
 * Selector for the source of learning outcomes (accuracy, loss, update norms,
 * payload bytes, signatures) consumed by the simulator's event handlers.
 *
 * <p>The runtime counterpart is the {@code fl.cosim.LearningProvider}
 * interface: a {@code null} provider means {@link #SYNTHETIC} (the legacy
 * inline formula — never routed through a provider so that path stays
 * byte-identical), {@code TraceProvider} serves {@link #TRACE}, and Track-B
 * {@link #ONLINE} outcomes are consumed directly from
 * {@code cosim.trackb.RoundResponse} by the round-sync connector.</p>
 *
 * <h2>Default</h2>
 * The default is {@link #SYNTHETIC} everywhere — the legacy
 * {@code 0.50 + 0.40·prog + N(0, 0.02)} accuracy formula in
 * {@link EvaluationEvent} is the backward-compat reference and must not be
 * changed. Switching to TRACE / ONLINE is an explicit opt-in.
 */
public enum FLLearningSource {

    /**
     * Synthetic learning outcomes — the legacy reference behaviour.
     *
     * <p>Accuracy follows the {@link EvaluationEvent} closed-form trend
     * (0.50 → 0.90 with small Gaussian jitter); model updates are random
     * {@link FLModelUpdate} deltas. All existing demos run in this mode by
     * default so their console output and CSV exports remain byte-identical
     * to the SIMPAT paper baseline.</p>
     */
    SYNTHETIC,

    /**
     * Scenario-indexed trace replay (Track A, Pass 3).
     *
     * <p>Per-round metrics (accuracy, loss, payload bytes, signatures, peer
     * sets) are read from a learning trace produced by the Python harness
     * in Pass 2. The simulator owns timing, energy, and scheduling; the
     * trace owns the learning result. This is the default mode for the
     * topology, policy, and scaling experiments in Section 9.</p>
     */
    TRACE,

    /**
     * Online round-synchronous coupling (Track B).
     *
     * <p>At each round boundary the simulator pauses, the PyTorch harness
     * trains the round live (via the directory-handshake protocol of
     * Phase 4), and the simulator resumes. The worker's wall-clock is not
     * used to advance the discrete-event timeline; only learning outcomes
     * (accuracy, loss, update norms, payload bytes, signatures) are
     * accepted. Track B is the fidelity anchor for ERQ1 — slow, used only
     * at small scale.</p>
     */
    ONLINE
}
