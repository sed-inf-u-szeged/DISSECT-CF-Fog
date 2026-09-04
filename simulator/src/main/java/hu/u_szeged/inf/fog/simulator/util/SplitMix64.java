package hu.u_szeged.inf.fog.simulator.util;

/**
 * Tiny, dependency-free SplitMix64 PRNG used as the cross-language deterministic
 * stream for federated gossip decisions (peer selection ties, dynamic edge
 * toggling, etc.). Java's {@link java.util.Random} and Python's {@code random}
 * are NOT bit-compatible, so we implement the same algorithm on both sides
 * (see {@code harness/flrng.py}) and rely on it for the
 * Pass-2 (Python) ↔ Pass-3 (Java) replay agreement.
 *
 * <h2>Algorithm</h2>
 * <pre>
 *     state += 0x9E3779B97F4A7C15
 *     z      = state
 *     z      = (z ^ (z &gt;&gt;&gt; 30)) * 0xBF58476D1CE4E5B9
 *     z      = (z ^ (z &gt;&gt;&gt; 27)) * 0x94D049BB133111EB
 *     return  z ^ (z &gt;&gt;&gt; 31)
 * </pre>
 * Reference: Steele, Lea, Flood, "Fast Splittable Pseudorandom Number Generators",
 * OOPSLA 2014. All arithmetic is unsigned 64-bit; in Java the operations on
 * primitive {@code long} are already 2's-complement modulo 2^64 (sign-irrelevant
 * for the bit pattern), so a simple {@code long} suffices.
 *
 * <h2>Cross-language contract</h2>
 * The Python mirror ({@code harness/flrng.py}) MUST:
 * <ul>
 *   <li>mask every {@code += / *=} to 64 bits ({@code &amp; 0xFFFFFFFFFFFFFFFF}),</li>
 *   <li>use {@code &gt;&gt;} (logical unsigned shift) for the {@code >>> 30/27/31} mixes,</li>
 *   <li>produce {@code nextDouble} as {@code (nextLong() &gt;&gt;&gt; 11) * 2^-53}
 *       so the 53-bit mantissa is bit-identical,</li>
 *   <li>use the same rejection-sampling {@code nextInt(bound)} below.</li>
 * </ul>
 * The committed golden-vector tests (JUnit + pytest) lock the wire format.
 *
 * <h2>Per-decision consumption-order contract (P0.3)</h2>
 * Documented in {@link SimRandom#derive(long, int, int)}; this class is just the
 * generator — the *order* of draws is what makes the simulator and the Python
 * harness agree on peer sets.
 *
 * <p>Not thread-safe. Each per-decision stream is built fresh from
 * {@link SimRandom#derive(long, int, int)}; there is no shared mutable state.</p>
 */
public final class SplitMix64 {

    /** SplitMix64 increment (golden-ratio constant). */
    static final long GAMMA = 0xFFFFFFFFFFFFFFFFL & 0x9E3779B97F4A7C15L;

    private long state;

    /** Build a stream from the given 64-bit seed. */
    public SplitMix64(long seed) {
        this.state = seed;
    }

    /** Apply the SplitMix64 finalizer once to {@code z}. Public so callers can
     *  also use the mixer as a one-shot hash for stream-derivation (see
     *  {@link SimRandom#derive}). */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Next 64-bit value. */
    public long nextLong() {
        state += GAMMA;
        return mix(state);
    }

    /**
     * Uniform double in [0, 1). Bits 53 most-significant of {@link #nextLong()}
     * scaled by 2^-53, so the Python mirror reproduces the value exactly.
     */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /**
     * Uniform int in [0, bound). Rejection-sampled from {@link #nextLong()} so
     * the distribution is exact (no modulo bias) and so that the Python mirror,
     * fed bit-identical {@code nextLong()} output and using the same rejection
     * rule, returns the same integer for the same call.
     *
     * <p>Procedure:</p>
     * <ol>
     *   <li>{@code u = nextLong() &amp; Long.MAX_VALUE} — the low 63 bits, in
     *       {@code [0, 2^63 - 1]}.</li>
     *   <li>{@code r = u % bound}.</li>
     *   <li>If {@code u - r + (bound - 1)} overflows {@code long} (i.e. is
     *       negative in signed arithmetic), {@code u} is in the ragged tail
     *       past the last bound-aligned block — redraw. Otherwise return r.</li>
     * </ol>
     *
     * <p>The Python mirror checks {@code u - r + (bound - 1) &lt; (1 &lt;&lt; 63)}
     * on infinite-precision ints — the same condition.</p>
     *
     * @throws IllegalArgumentException if {@code bound <= 0}.
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        while (true) {
            long u = nextLong() & Long.MAX_VALUE;                  // [0, 2^63 - 1]
            long r = u % bound;
            long check = u - r + (long) (bound - 1);
            // signed-overflow detection: if check < 0 the unsigned value was
            // >= 2^63, meaning `u` was in the ragged tail — redraw.
            if (check >= 0L) {
                return (int) r;
            }
        }
    }
}
