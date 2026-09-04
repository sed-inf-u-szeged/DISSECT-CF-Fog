package hu.u_szeged.inf.fog.simulator.fl.cosim;

import hu.u_szeged.inf.fog.simulator.fl.gossip.FLGossipNode;
import hu.u_szeged.inf.fog.simulator.fl.topology.FLTopology;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Records Topology-Induced Divergence (§8.3) at each synchronous round barrier,
 * computed in <b>signature space</b> (the single divergence basis, D5):
 *
 * <ul>
 *   <li><b>TID-dispersion, global</b> — mean pairwise ℓ₂ distance over <i>all</i>
 *       node pairs (Eq. tid-disp), tracking overall consensus;</li>
 *   <li><b>TID-dispersion, inter-cluster</b> — mean pairwise distance over pairs
 *       that straddle the two clusters (the bridge bottleneck the two-cluster
 *       topology is designed to stress);</li>
 *   <li><b>consensus distance</b> — mean over nodes of {@code ‖sig_i − mean‖₂},
 *       the distance of each model to the uniform mean model {@code w̄}.</li>
 * </ul>
 *
 * <p>Lower dispersion ⇒ better mixing. Output is {@code tid_timeseries.csv} with
 * rows {@code (round, scope, value)} where {@code scope ∈ {global,
 * inter_cluster, consensus}}. The TID-<i>gap</i> (Eq. tid-gap) is a cross-cell
 * quantity computed in the analysis package (P5), not here.</p>
 */
public final class TidRecorder {

    /** A recorded sample. */
    public static final class Sample {
        public final int round;
        public final String scope;
        public final double value;

        Sample(int round, String scope, double value) {
            this.round = round;
            this.scope = scope;
            this.value = value;
        }
    }

    private final int[] clusterLabel;
    private final List<Sample> samples = new ArrayList<>();

    /**
     * Creates a recorder scoped by the topology's cluster labels.
     *
     * @param topology the topology (for cluster labels).
     */
    public TidRecorder(FLTopology topology) {
        this.clusterLabel = topology.clusterLabels();
    }

    /**
     * Creates a recorder with explicit cluster labels.
     *
     * @param clusterLabel explicit per-node cluster labels.
     */
    public TidRecorder(int[] clusterLabel) {
        this.clusterLabel = clusterLabel.clone();
    }

    /** Records the three TID scopes for {@code round} from the live node signatures. */
    public void recordRound(int round, List<FLGossipNode> nodes) {
        float[][] sigs = new float[nodes.size()][];
        for (int i = 0; i < nodes.size(); i++) {
            sigs[i] = nodes.get(i).signature();
        }
        record(round, sigs);
    }

    /**
     * Records the three TID scopes for {@code round} from raw signatures
     * (index = node id).
     */
    public void record(int round, float[][] signatures) {
        int n = signatures.length;

        // Global + inter-cluster dispersion (mean pairwise ℓ₂).
        double globalSum = 0.0;
        long globalCount = 0;
        double interSum = 0.0;
        long interCount = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dist = FLGossipNode.l2(signatures[i], signatures[j]);
                globalSum += dist;
                globalCount++;
                if (clusterLabel[i] != clusterLabel[j]) {
                    interSum += dist;
                    interCount++;
                }
            }
        }
        double global = globalCount == 0 ? 0.0 : globalSum / globalCount;
        double inter = interCount == 0 ? 0.0 : interSum / interCount;

        // Consensus distance: mean ‖sig_i − mean‖₂.
        int d = signatures[0].length;
        float[] mean = new float[d];
        for (float[] s : signatures) {
            for (int k = 0; k < d; k++) {
                mean[k] += s[k] / n;
            }
        }
        double consSum = 0.0;
        for (float[] s : signatures) {
            consSum += FLGossipNode.l2(s, mean);
        }
        double consensus = consSum / n;

        samples.add(new Sample(round, "global", global));
        samples.add(new Sample(round, "inter_cluster", inter));
        samples.add(new Sample(round, "consensus", consensus));
    }

    /** All recorded samples in insertion order. */
    public List<Sample> samples() {
        return samples;
    }

    /** Latest value for a given scope at a given round, or NaN if absent. */
    public double valueAt(int round, String scope) {
        for (Sample s : samples) {
            if (s.round == round && s.scope.equals(scope)) {
                return s.value;
            }
        }
        return Double.NaN;
    }

    /** The time series as a deterministic CSV string. */
    public String toCsv() {
        StringBuilder sb = new StringBuilder("round,scope,value\n");
        for (Sample s : samples) {
            sb.append(s.round).append(',')
              .append(s.scope).append(',')
              .append(String.format(Locale.US, "%.9f", s.value)).append('\n');
        }
        return sb.toString();
    }

    /** Writes {@code tid_timeseries.csv} to {@code path}. */
    public void write(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(toCsv());
        }
    }
}
