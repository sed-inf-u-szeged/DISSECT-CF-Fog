package hu.u_szeged.inf.fog.simulator.fl.merge;

/**
 * A merge rule produces the convex combination weights {@code ω} for a node's
 * post-exchange model update {@code ŵ_i = Σ_{j∈{i}∪P_i} ω_ij · w_j} (§8.2,
 * Eq. merge). Four interchangeable rules are provided: uniform averaging, the
 * drift-suppressed rule, degree-corrected Metropolis–Hastings, and a fixed
 * uniform mixing for the D-PSGD baseline.
 *
 * <h2>Member convention</h2>
 * All inputs and the output are aligned over the <b>member array</b>
 * {@code [self, peer_0, …, peer_{p-1}]} of length {@code p+1}, with the deciding
 * node {@code self} at index 0 (so the returned {@code ω[0]} is the self weight,
 * which the trace records first — P3.2). For each index {@code m}:
 * <ul>
 *   <li>{@code divergence[m]} = {@code D_self,m} on the <i>freshly exchanged</i>
 *       signatures ({@code divergence[0] = 0} for self);</li>
 *   <li>{@code sampleCount[m]} = local sample count {@code n_m};</li>
 *   <li>{@code degree[m]} = topology degree of member {@code m} (for MH /
 *       fixed-uniform).</li>
 * </ul>
 *
 * <p><b>Single divergence basis (D5).</b> {@code divergence} is always computed
 * in <i>signature space</i>, identically on both sides of the bridge; the Python
 * mirror {@code harness/merge.py} uses the same formulas, so the Java replay
 * reproduces the harness weights exactly.</p>
 *
 * <p>Every rule returns weights that sum to 1 (row-stochastic).</p>
 */
public interface MergeRule {

    /**
     * Computes the convex member weights {@code [self, peers…]} (Σ = 1).
     *
     * @param divergence per-member fresh divergence {@code D_self,m} ({@code [0]=0}).
     * @param sampleCount per-member local sample counts {@code n_m} ({@code > 0}).
     * @param degree per-member topology degree (used by MH / fixed-uniform).
     * @return convex weights aligned with the member array, summing to 1.
     */
    double[] weights(double[] divergence, int[] sampleCount, int[] degree);

    /** Stable identifier (trace column / telemetry). */
    String id();

    /** Validates the aligned-array contract; shared by the implementations. */
    static void checkArgs(double[] divergence, int[] sampleCount, int[] degree) {
        int m = divergence.length;
        if (m < 1 || sampleCount.length != m || degree.length != m) {
            throw new IllegalArgumentException("member arrays must be non-empty and equal length");
        }
    }
}
