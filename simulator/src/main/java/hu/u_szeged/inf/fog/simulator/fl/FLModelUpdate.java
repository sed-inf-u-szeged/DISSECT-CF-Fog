package hu.u_szeged.inf.fog.simulator.fl;

import java.util.Arrays;

/**
 * Immutable value object representing one client's model update.
 *  
 * Contains:
 *   - {@code modelParameters}: the delta vector (defensive copy on construct/get),
 *   - {@code sampleCount}: synthetic number of local samples used,
 *   - {@code updateSize}: payload size in bytes (after compression),
 *   - {@code roundId}: round the update belongs to,
 *   - {@code baseModelVersion}: server model version the client trained on.
 */
public final class FLModelUpdate {
    private final double[] modelParameters;
    private final int sampleCount;
    private final long updateSize;

    private final int roundId;
    private final int baseModelVersion;

    /**
     * Full constructor.
     *
     * @param modelParameters   delta parameters (defensively copied)
     * @param sampleCount       synthetic local sample count (>0)
     * @param updateSize        payload size in bytes (after compression)
     * @param roundId           round index this update belongs to
     * @param baseModelVersion  server model version used for training
     */
    public FLModelUpdate(double[] modelParameters, int sampleCount, long updateSize,
                         int roundId, int baseModelVersion) {
        this.modelParameters = modelParameters.clone();
        this.sampleCount     = sampleCount;
        this.updateSize      = updateSize;
        this.roundId         = roundId;
        this.baseModelVersion= baseModelVersion;
    }

    /**
     * Backward-compatible constructor (without round/version metadata).
     * Fields default to -1 for {@code roundId} and {@code baseModelVersion}.
     */
    public FLModelUpdate(double[] modelParameters, int sampleCount, long updateSize) {
        this(modelParameters, sampleCount, updateSize, -1, -1);
    }

    /** @return defensive copy of the delta parameters */
    public double[] getModelParameters() {
        return modelParameters.clone();
    }

    /** @return local sample count */
    public int getSampleCount() {
        return sampleCount;
    }

    /** @return payload size in bytes (compressed) */
    public long getUpdateSize() {
        return updateSize;
    }

    /** @return round index this update belongs to (or -1 if unknown) */
    public int getRoundId() {
        return roundId;
    }

    /** @return server model version used for training (or -1 if unknown) */
    public int getBaseModelVersion() {
        return baseModelVersion;
    }

    @Override
    public String toString() {
        return "FLModelUpdate{params=" + Arrays.toString(modelParameters)
                + ", samples=" + sampleCount + ", size=" + updateSize + "B"
                + ", roundId=" + roundId
                + ", baseModelVersion=" + baseModelVersion
                + "}";
    }
}
