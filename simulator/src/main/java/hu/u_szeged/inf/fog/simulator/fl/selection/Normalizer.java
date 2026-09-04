package hu.u_szeged.inf.fog.simulator.fl.selection;

/**
 * Min–max normalisation of a cost term within a node's neighbourhood (§8.2,
 * Eq. norm) with the two guards the paper specifies:
 *
 * <ul>
 *   <li><b>Degenerate neighbourhood</b>: when {@code max == min} (e.g. identical
 *       link latencies on a ring), the normalised value is defined as
 *       {@code 0} for every present element, which leaves the ranking
 *       unaffected.</li>
 *   <li><b>Divergence cold start</b>: a neighbour with no cached signature
 *       ({@code present[j] == false}) takes the neutral value {@code 0.5} and is
 *       <i>excluded</i> from the min–max statistics, so a single cold peer
 *       cannot collapse the normalisation of the familiar ones.</li>
 * </ul>
 *
 * <p>The {@code present} mask is only ever non-trivial for the divergence term;
 * latency, load, and bandwidth are always fully present. This mirrors
 * {@code harness/selection.py} (P3.3) exactly — the normalisation is a
 * divergence-decision input, so it must agree bit-for-bit across the bridge.</p>
 */
public final class Normalizer {

    /** Neutral cold-start value for an excluded element (§8.2). */
    public static final double COLD_START_VALUE = 0.5;

    private Normalizer() {
    }

    /**
     * Min–max normalises {@code raw}, treating elements with {@code present[i] ==
     * false} as cold (assigned {@link #COLD_START_VALUE}, excluded from the
     * min/max). The returned array has the same length and index alignment as
     * {@code raw}.
     *
     * @param raw raw term values, aligned with the neighbour list.
     * @param present per-element presence mask (true ⇒ included in min/max).
     * @return normalised values in {@code [0,1]} for present elements (or 0 under
     *         the degenerate guard), {@link #COLD_START_VALUE} for absent ones.
     */
    public static double[] normalize(double[] raw, boolean[] present) {
        if (raw.length != present.length) {
            throw new IllegalArgumentException("raw and present length mismatch");
        }
        int n = raw.length;
        double[] out = new double[n];

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        boolean anyPresent = false;
        for (int i = 0; i < n; i++) {
            if (present[i]) {
                anyPresent = true;
                if (raw[i] < min) {
                    min = raw[i];
                }
                if (raw[i] > max) {
                    max = raw[i];
                }
            }
        }

        if (!anyPresent) {
            // All cold (e.g. round 0 divergence): everyone gets the neutral value.
            java.util.Arrays.fill(out, COLD_START_VALUE);
            return out;
        }

        boolean degenerate = (max == min);
        for (int i = 0; i < n; i++) {
            if (!present[i]) {
                out[i] = COLD_START_VALUE;
            } else if (degenerate) {
                out[i] = 0.0; // §8.2: homogeneous term leaves ranking unaffected
            } else {
                out[i] = (raw[i] - min) / (max - min);
            }
        }
        return out;
    }

    /** Convenience overload for a fully-present term (latency, load, bandwidth). */
    public static double[] normalize(double[] raw) {
        boolean[] present = new boolean[raw.length];
        java.util.Arrays.fill(present, true);
        return normalize(raw, present);
    }
}
