package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Defines the acceptance thresholds used by the model lifecycle.
 */
@Getter
@AllArgsConstructor
public class LifecyclePolicy {

    /**
     * Minimum number of matched predictions required for evaluation.
     */
    private final int minimumMatchedSamples;

    /**
     * Maximum accepted Mean Absolute Error.
     */
    private final double maximumMae;

    /**
     * Maximum accepted Mean Squared Error.
     */
    private final double maximumMse;

    /**
     * Maximum accepted Root Mean Squared Error.
     */
    private final double maximumRmse;

    /**
     * Maximum accepted SMAPE percentage.
     */
    private final double maximumSmape;

    /**
     * Minimum required accuracy percentage.
     */
    private final double minimumAccuracy;

    /**
     * Relative error tolerance used when calculating accuracy.
     */
    private final double accuracyTolerance;

    /**
     * Checks whether all lifecycle acceptance requirements are satisfied.
     *
     * @param metrics the shadow evaluation results
     * @return true only when every requirement is satisfied
     */
    public boolean isSatisfiedBy(EvaluationMetrics metrics) {
        return metrics != null
                && metrics.getMatchedSamples() >= minimumMatchedSamples
                && metrics.getMae() <= maximumMae
                && metrics.getMse() <= maximumMse
                && metrics.getRmse() <= maximumRmse
                && metrics.getSmape() <= maximumSmape
                && metrics.getAccuracy() >= minimumAccuracy;
    }
}