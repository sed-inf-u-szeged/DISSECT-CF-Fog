package hu.u_szeged.inf.fog.simulator.prediction.settings;

import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecyclePolicy;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Defines lifecycle configuration used during a prediction simulation.
 */
@Getter
@Setter
@NoArgsConstructor
public class LifecycleSettings {

    /**
     * Determines whether model lifecycle evaluation is enabled.
     */
    private boolean enabled;

    /**
     * Name of the trusted predictor that starts as ACTIVE.
     * When null or blank, no predictor is automatically activated.
     */
    private String baselinePredictorName;

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
     * Creates lifecycle settings without an initial baseline predictor.
     *
     * This constructor preserves compatibility with existing code that
     * uses the original eight lifecycle arguments.
     *
     * @param enabled whether lifecycle evaluation is enabled
     * @param minimumMatchedSamples minimum matched sample count
     * @param maximumMae maximum accepted MAE
     * @param maximumMse maximum accepted MSE
     * @param maximumRmse maximum accepted RMSE
     * @param maximumSmape maximum accepted SMAPE
     * @param minimumAccuracy minimum accepted accuracy
     * @param accuracyTolerance relative accuracy tolerance
     */
    public LifecycleSettings(
            boolean enabled,
            int minimumMatchedSamples,
            double maximumMae,
            double maximumMse,
            double maximumRmse,
            double maximumSmape,
            double minimumAccuracy,
            double accuracyTolerance
    ) {
        this(
                enabled,
                null,
                minimumMatchedSamples,
                maximumMae,
                maximumMse,
                maximumRmse,
                maximumSmape,
                minimumAccuracy,
                accuracyTolerance
        );
    }

    /**
     * Creates lifecycle settings with a trusted baseline predictor.
     *
     * @param enabled whether lifecycle evaluation is enabled
     * @param baselinePredictorName predictor that starts as ACTIVE
     * @param minimumMatchedSamples minimum matched sample count
     * @param maximumMae maximum accepted MAE
     * @param maximumMse maximum accepted MSE
     * @param maximumRmse maximum accepted RMSE
     * @param maximumSmape maximum accepted SMAPE
     * @param minimumAccuracy minimum accepted accuracy
     * @param accuracyTolerance relative accuracy tolerance
     */
    public LifecycleSettings(
            boolean enabled,
            String baselinePredictorName,
            int minimumMatchedSamples,
            double maximumMae,
            double maximumMse,
            double maximumRmse,
            double maximumSmape,
            double minimumAccuracy,
            double accuracyTolerance
    ) {
        this.enabled = enabled;
        this.baselinePredictorName = baselinePredictorName;
        this.minimumMatchedSamples = minimumMatchedSamples;
        this.maximumMae = maximumMae;
        this.maximumMse = maximumMse;
        this.maximumRmse = maximumRmse;
        this.maximumSmape = maximumSmape;
        this.minimumAccuracy = minimumAccuracy;
        this.accuracyTolerance = accuracyTolerance;
    }

    /**
     * Checks whether the supplied predictor is the configured baseline.
     *
     * @param predictorName predictor name to check
     * @return true when it is the configured baseline predictor
     */
    public boolean isBaselinePredictor(String predictorName) {
        return baselinePredictorName != null
                && !baselinePredictorName.isBlank()
                && baselinePredictorName.equals(predictorName);
    }

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