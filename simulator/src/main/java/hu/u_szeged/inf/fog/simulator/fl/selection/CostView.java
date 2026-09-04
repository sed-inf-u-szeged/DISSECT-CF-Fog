package hu.u_szeged.inf.fog.simulator.fl.selection;

/**
 * Read-only view of the four per-peer cost terms of the peer-selection score
 * (§8.2, Eq. score / Table score-terms), plus the topology degree used by the
 * degree-based selector. A {@link PeerSelectionPolicy} queries this for each
 * candidate neighbour {@code j} of the deciding node {@code i} at the current
 * round; the raw values are normalised by {@link Normalizer} before scoring.
 *
 * <p>All "cost" terms are oriented so that <b>lower is more desirable</b>
 * (a fast, lightly-loaded, nearby, similar peer scores low).</p>
 *
 * <ul>
 *   <li>{@link #latency(int)} — {@code L_ij}, link latency of edge (i,j) in ticks.</li>
 *   <li>{@link #load(int)} — {@code C_j}, compute load of peer j (load fraction).</li>
 *   <li>{@link #divergence(int)} — {@code D̂_ij}, the <i>cached</i> selection-time
 *       divergence {@code ||sig_i(t) − sig_j(t−1)||₂} in signature space; valid
 *       only when {@link #hasDivergence(int)} is true (cold-start otherwise).</li>
 *   <li>{@link #bandwidthCost(int)} — {@code B_ij}, transfer cost
 *       {@code payloadBytes / bandwidth_ij}.</li>
 *   <li>{@link #degree(int)} — topology degree of j (for the degree-based selector;
 *       not part of Eq. score).</li>
 * </ul>
 */
public interface CostView {

    /** {@code L_ij}: link latency of edge (i, j) in ticks. */
    double latency(int neighbour);

    /** {@code C_j}: compute load of peer j (a load fraction, typically [0,1]). */
    double load(int neighbour);

    /**
     * {@code D̂_ij}: cached selection-time divergence in signature space. Only
     * meaningful when {@link #hasDivergence(int)} returns true; otherwise the
     * peer is cold-started (neutral 0.5, excluded from the min–max — see
     * {@link Normalizer}).
     */
    double divergence(int neighbour);

    /** Whether a cached signature exists for {@code neighbour} (false ⇒ cold start). */
    boolean hasDivergence(int neighbour);

    /** {@code B_ij}: transfer cost, {@code payloadBytes / bandwidth_ij}. */
    double bandwidthCost(int neighbour);

    /** Topology degree of {@code neighbour} (used by the degree-based selector). */
    int degree(int neighbour);
}
