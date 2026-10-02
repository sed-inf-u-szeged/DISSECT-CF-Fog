package hu.u_szeged.inf.fog.simulator.prediction.settings;

import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecyclePolicy;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Defines lifecycle configuration used during a prediction simulation.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LifecycleSettings {

    /**
     * Determines whether model lifecycle evaluation is enabled.
     */
    private boolean enabled;

    /**
     * Minimum number of matched predictions required for evaluation.
     */
    private int minimumMatchedSamples;

    /**
     * Maximum accepted Mean Absolute Error.
     */
    private double maximumMae;

    /**
     * Maximum accepted Mean Squared Error.
     */
    private double maximumMse;

    /**
     * Maximum accepted Root Mean Squared Error.
     */
    private double maximumRmse;

    /**
     * Maximum accepted SMAPE percentage.
     */
    private double maximumSmape;

    /**
     * Minimum required accuracy percentage.
     */
    private double minimumAccuracy;

    /**
     * Relative error tolerance used when calculating accuracy.
     */
    private double accuracyTolerance;

    /**
     * Creates a lifecycle policy from this simulation configuration.
     *
     * @return the configured lifecycle policy
     */
    public LifecyclePolicy toPolicy() {
        return new LifecyclePolicy(
                minimumMatchedSamples,
                maximumMae,
                maximumMse,
                maximumRmse,
                maximumSmape,
                minimumAccuracy,
                accuracyTolerance
        );
    }
}