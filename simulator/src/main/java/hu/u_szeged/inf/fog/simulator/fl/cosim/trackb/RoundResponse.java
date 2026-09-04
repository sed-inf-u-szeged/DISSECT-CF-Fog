package hu.u_szeged.inf.fog.simulator.fl.cosim.trackb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * Track-B per-round response written by the worker after real training (§8.4):
 * per-node learning outcomes plus a signatures file for the round. The
 * worker's wall-clock is <i>not</i> used to advance the simulator — only these
 * learning results are accepted; timing and energy stay analytic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RoundResponse {

    public int round;
    /** node id (as string) -> per-node outcome. */
    public Map<String, NodeOutcome> perNode;
    /** file name of the round's signatures sidecar (relative to the round dir). */
    public String signaturesFile;
    public int signatureDim;
    /** §8.4 distortion check (D5): signature-space vs full-weight pairwise
     *  distance ratios for this round (Track B is the only place the full
     *  weights exist). {@code meanRatio ≈ 1} validates the linear projection. */
    public Distortion signatureDistortion;

    public RoundResponse() {
    }

    /** Per-round signature-vs-full-weight distance distortion summary. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Distortion {
        public int pairs;
        public double meanRatio;
        public double minRatio;
        public double maxRatio;

        public Distortion() {
        }
    }

    /** Per-node learning outcome for a Track-B round. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class NodeOutcome {
        public double acc;
        public double loss;
        public double deltaNorm;
        public double trainTimeMs;
        public long payloadBytes;
        public int nSamples;

        public NodeOutcome() {
        }
    }
}
