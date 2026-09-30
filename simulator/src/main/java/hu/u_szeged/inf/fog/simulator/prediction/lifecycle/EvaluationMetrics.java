package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Stores the evaluation results calculated from matched shadow predictions
 * and actual values.
 */
@Getter
@AllArgsConstructor
public class EvaluationMetrics {

    /**
     * Number of predictions that have been matched with actual values.
     */
    private final int matchedSamples;

    /**
     * Mean Absolute Error.
     */
    private final double mae;

    /**
     * Mean Squared Error.
     */
    private final double mse;

    /**
     * Root Mean Squared Error.
     */
    private final double rmse;

    /**
     * Symmetric Mean Absolute Percentage Error, expressed as a percentage.
     */
    private final double smape;

    /**
     * Percentage of predictions within the configured error tolerance.
     */
    private final double accuracy;
}