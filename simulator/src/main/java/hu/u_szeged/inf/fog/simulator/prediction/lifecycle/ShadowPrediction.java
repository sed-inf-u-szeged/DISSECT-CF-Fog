package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import lombok.Getter;

/**
 * Represents one future prediction waiting to be matched with an actual value.
 */
@Getter
public class ShadowPrediction {

    /**
     * Name of the predictor that produced the prediction.
     */
    private final String predictorName;

    /**
     * Name of the predicted feature.
     */
    private final String featureName;

    /**
     * Sequence number of the prediction request.
     */
    private final int predictionNumber;

    /**
     * Timestamp for which the value was predicted.
     */
    private final int targetTimestamp;

    /**
     * Value predicted for the target timestamp.
     */
    private final double predictedValue;

    /**
     * Actual value observed later for the target timestamp.
     * It remains null until the prediction is matched.
     */
    private Double actualValue;

    /**
     * Creates an unresolved shadow prediction.
     *
     * @param predictorName the predictor that produced the prediction
     * @param featureName the predicted feature
     * @param predictionNumber the sequence number of the prediction request
     * @param targetTimestamp the timestamp being predicted
     * @param predictedValue the predicted value
     */
    public ShadowPrediction(
            String predictorName,
            String featureName,
            int predictionNumber,
            int targetTimestamp,
            double predictedValue
    ) {
        this.predictorName = predictorName;
        this.featureName = featureName;
        this.predictionNumber = predictionNumber;
        this.targetTimestamp = targetTimestamp;
        this.predictedValue = predictedValue;
    }

    /**
     * Returns whether an actual value has already been matched.
     *
     * @return true when the actual value is available
     */
    public boolean isResolved() {
        return actualValue != null;
    }

    /**
     * Matches the prediction with the actual value observed later.
     *
     * @param value the actual observed value
     */
    public void resolve(double value) {
        this.actualValue = value;
    }
}