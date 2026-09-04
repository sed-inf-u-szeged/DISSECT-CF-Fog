package hu.u_szeged.inf.fog.simulator.util;

import java.util.Random;

/**
 * Singleton-like access to a shared {@link Random} instance across the simulation.
 *
 * Using a single RNG ensures that all stochastic decisions (sampling, noise injection,
 * failures, etc.) are reproducible when a seed is set "before" events are created.
 *
 * Thread-safety: Uses double-checked locking and a volatile reference.
 * While DISSECT-CF-Fog typically runs single-threaded event loops, this design guards against
 * accidental concurrent access. 
 */
public final class SimRandom {
	
	// Thread-safe Random wrapper
	private static volatile Random RNG = new Random();   // default: non-deterministic
	
	private static final Object LOCK = new Object();
	
    private SimRandom() { } // utility class - no instantiation

    /**
     * Sets a deterministic seed for the shared RNG.
     * Should be called once during startup (before scheduling events).
     *
     * @param seed seed value.
     */
    public static void setSeed(long seed) {
        synchronized (LOCK) { RNG = new Random(seed); }
    }

    /**
     * Returns the shared RNG instance. The returned object must not be replaced or mutated
     * beyond typical {@link Random} usage.
     *
     * @return shared Random.
     */
    public static Random get() {
        // double-checked locking avoids synchronization in the hot path
        Random r = RNG;
        if (r == null) {
            synchronized (LOCK) {
                if (RNG == null) RNG = new Random();
                r = RNG;
            }
        }
        return r;
    }

    // ---------------------------------------------------------------------
    // Per-decision derived streams (P0.3 — gossip co-simulation)
    // ---------------------------------------------------------------------

    /**
     * Builds a fresh, deterministic {@link SplitMix64} stream for a single
     * gossip-round decision keyed by {@code (seed, round, nodeId)}. Used by
     * the gossip orchestrator (peer-selection tie-breaks, Random-k selector,
     * dynamic edge toggling — see the consumption-order contract below) so the
     * Pass-3 Java replay can reproduce Pass-2 Python decisions bit-for-bit.
     *
     * <p><b>Why a derived stream and not the legacy shared {@link Random}?</b>
     * The shared stream is consumed by sampling, dropout, DP noise, failures,
     * etc. — anything that adds or removes a draw silently shifts every
     * subsequent decision. A per-decision stream isolates each choice from the
     * order in which the host code happens to schedule events.</p>
     *
     * <p><b>Consumption-order contract</b> (must be mirrored by
     * {@code harness/flrng.py} — order of draws is the real cross-language
     * hazard, not the PRNG itself):</p>
     * <table border="1">
     *   <caption>Per-decision draw order</caption>
     *   <tr><th>Decision</th><th>Stream key</th><th>Draws, in fixed order</th></tr>
     *   <tr>
     *     <td>Tie-break in top-k selection</td>
     *     <td>{@code derive(seed, round, node)}</td>
     *     <td>one {@link SplitMix64#nextDouble()} per candidate, candidates
     *         iterated in ascending peer-id order</td>
     *   </tr>
     *   <tr>
     *     <td>Random-k selector</td>
     *     <td>{@code derive(seed, round, node)}</td>
     *     <td>Fisher–Yates over neighbour list sorted by id (one
     *         {@link SplitMix64#nextInt(int) nextInt(remaining)} per swap, in
     *         decreasing-{@code remaining} order)</td>
     *   </tr>
     *   <tr>
     *     <td>Dynamic edge toggling (p_link)</td>
     *     <td>{@code derive(seed, round, -1)}</td>
     *     <td>one {@link SplitMix64#nextDouble()} per edge, edges in ascending
     *         {@code (u, v)} order with {@code u &lt; v}</td>
     *   </tr>
     *   <tr>
     *     <td>Background-load profile</td>
     *     <td>{@code derive(seed * 31 + 7, round, node)}</td>
     *     <td>one {@link SplitMix64#nextDouble()} per node-round — a separate
     *         stream so it never aliases with selection draws on the same node</td>
     *   </tr>
     * </table>
     *
     * <p><b>Stream-key mixing.</b> The three inputs are folded into the seed via
     * the SplitMix64 finalizer so distinct {@code (seed, round, nodeId)} triples
     * produce statistically independent streams. The multipliers are arbitrary
     * 64-bit odd constants drawn from public mixing tables — any non-trivial
     * choice would do; this one is locked by the committed golden vectors.</p>
     *
     * <p><b>Special encoding.</b> {@code nodeId = -1} is the convention for
     * "graph-level" decisions that do not belong to any node (e.g. dynamic
     * edge toggling). All other negative ids are reserved.</p>
     *
     * @param seed   the campaign-level seed (e.g. scenario cell seed).
     * @param round  the current gossip round, {@code >= 0}.
     * @param nodeId the node id, {@code >= 0}, or {@code -1} for graph-level.
     * @return a fresh SplitMix64 stream, never null.
     */
    public static SplitMix64 derive(long seed, int round, int nodeId) {
        long h = seed;
        h = SplitMix64.mix(h ^ (0xA24BAED4963EE407L * ((long) round + 1L)));
        h = SplitMix64.mix(h ^ (0x9FB21C651E98DF25L * ((long) nodeId + 1L)));
        return new SplitMix64(h);
    }
}
