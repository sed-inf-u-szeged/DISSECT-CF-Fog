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
        synchronized (LOCK) {
            RNG = new Random(seed); 
        }
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
                if (RNG == null) {
                    RNG = new Random();
                }
                r = RNG;
            }
        }
        return r;
    }
}
