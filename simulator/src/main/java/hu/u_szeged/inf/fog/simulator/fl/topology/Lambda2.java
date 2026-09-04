package hu.u_szeged.inf.fog.simulator.fl.topology;

import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology.Edge;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

/**
 * Algebraic connectivity λ₂ — the second-smallest eigenvalue of the graph
 * Laplacian {@code L = D − A}, where {@code A} is the (unweighted) adjacency
 * matrix over the active edges and {@code D} is the degree matrix (§8.3).
 *
 * <p>For {@code n <= 500} a dense {@link EigenDecomposition} on the symmetric
 * Laplacian is instantaneous, so no sparse solver is needed. The Laplacian is
 * real and symmetric, hence all eigenvalues are real and
 * {@code 0 = λ₁ <= λ₂ <= ...}; λ₂ is the connectivity. A disconnected graph
 * has λ₂ = 0 (more precisely, the multiplicity of the zero eigenvalue equals
 * the number of connected components).</p>
 *
 * <h2>Closed forms (used by the unit tests)</h2>
 * <ul>
 *   <li>ring C_n: λ₂ = 2(1 − cos(2π/n))</li>
 *   <li>complete graph K_n: λ₂ = n</li>
 *   <li>star K_{1,n−1}: λ₂ = 1</li>
 *   <li>disconnected: λ₂ = 0</li>
 * </ul>
 */
public final class Lambda2 {

    private Lambda2() {
    }

    /**
     * λ₂ over the currently active edges of {@code topo}.
     *
     * @param topo the topology (read-only; active flags consulted, not changed).
     * @return the second-smallest Laplacian eigenvalue; {@code 0.0} for a
     *         single node and (numerically) for a disconnected graph.
     */
    public static double lambda2(FLTopology topo) {
        int n = topo.size();
        if (n <= 1) {
            return 0.0;
        }
        double[][] laplacian = new double[n][n];
        for (Edge e : topo.edges()) {
            if (!e.active) {
                continue;
            }
            laplacian[e.u][e.v] -= 1.0;
            laplacian[e.v][e.u] -= 1.0;
            laplacian[e.u][e.u] += 1.0;
            laplacian[e.v][e.v] += 1.0;
        }
        return secondSmallestEigenvalue(laplacian);
    }

    /**
     * λ̄₂ — the expected algebraic connectivity of a dynamic graph, estimated
     * as the mean λ₂ over the per-round realised graphs for rounds
     * {@code 0..rounds-1} (§8.1). The topology's active state is restored to
     * all-active on return.
     *
     * @param topo the base (union) topology.
     * @param pLink per-round link-up probability.
     * @param rounds number of rounds to average over ({@code >= 1}).
     * @param seed the campaign seed (same seed used by the run, so the averaged
     *             realisations match what the simulation will see).
     * @return mean λ₂ across the realised per-round graphs.
     */
    public static double expectedLambda2(FLTopology topo, double pLink, int rounds, long seed) {
        if (rounds < 1) {
            throw new IllegalArgumentException("rounds must be >= 1: " + rounds);
        }
        double sum = 0.0;
        try {
            for (int r = 0; r < rounds; r++) {
                topo.applyDynamicRound(r, seed, pLink);
                sum += lambda2(topo);
            }
        } finally {
            topo.resetActive();
        }
        return sum / rounds;
    }

    /**
     * λ₂ of the <i>union</i> graph realised over rounds {@code 0..rounds-1}:
     * an edge is in the union if it is active in at least one of those rounds
     * (§8.1 "union graph's λ₂ provided as supplementary metadata"). The
     * topology's active state is restored to all-active on return.
     *
     * @param topo the base (union) topology.
     * @param pLink per-round link-up probability.
     * @param rounds number of rounds.
     * @param seed the campaign seed.
     * @return λ₂ of the realised union graph.
     */
    public static double unionLambda2(FLTopology topo, double pLink, int rounds, long seed) {
        if (rounds < 1) {
            throw new IllegalArgumentException("rounds must be >= 1: " + rounds);
        }
        int n = topo.size();
        // Collect the set of edges active in at least one round.
        Set<Long> unionKeys = new HashSet<>();
        List<Edge> all = topo.edges();
        try {
            for (int r = 0; r < rounds; r++) {
                topo.applyDynamicRound(r, seed, pLink);
                for (Edge e : all) {
                    if (e.active) {
                        unionKeys.add((long) e.u * n + e.v);
                    }
                }
            }
        } finally {
            topo.resetActive();
        }
        // Build a Laplacian over the union edge set.
        double[][] laplacian = new double[n][n];
        for (Edge e : all) {
            if (unionKeys.contains((long) e.u * n + e.v)) {
                laplacian[e.u][e.v] -= 1.0;
                laplacian[e.v][e.u] -= 1.0;
                laplacian[e.u][e.u] += 1.0;
                laplacian[e.v][e.v] += 1.0;
            }
        }
        return secondSmallestEigenvalue(laplacian);
    }

    /**
     * Returns the second-smallest eigenvalue of a symmetric matrix via dense
     * {@link EigenDecomposition}. Eigenvalues are sorted ascending; tiny
     * negative values (round-off on the structural zero eigenvalue) are
     * clamped to 0 before sorting so a connected graph never reports a
     * spuriously negative λ₂.
     */
    private static double secondSmallestEigenvalue(double[][] symmetric) {
        int n = symmetric.length;
        RealMatrix m = new Array2DRowRealMatrix(symmetric, false);
        EigenDecomposition ed = new EigenDecomposition(m);
        double[] eig = ed.getRealEigenvalues();
        // Clamp round-off noise on the (exact) zero eigenvalue.
        for (int i = 0; i < eig.length; i++) {
            if (Math.abs(eig[i]) < 1e-12) {
                eig[i] = 0.0;
            }
        }
        Arrays.sort(eig);
        return eig[1];
    }

    /** Closed-form λ₂ of a ring C_n: {@code 2(1 − cos(2π/n))}. Test helper. */
    public static double ringClosedForm(int n) {
        return 2.0 * (1.0 - Math.cos(2.0 * Math.PI / n));
    }
}
