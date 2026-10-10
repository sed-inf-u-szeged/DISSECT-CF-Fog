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

    private boolean enabled;
    private String baselinePredictorName;
    private int minimumMatchedSamples;
    private double maximumMae;
    private double maximumMse;
    private double maximumRmse;
    private double maximumSmape;
    private double minimumAccuracy;
    private double accuracyTolerance;

    /**
     * Null preserves the original accuracy tolerance rule.
     */
    private Double absoluteTolerance;

    public LifecycleSettings(boolean enabled, int minimumMatchedSamples,
            double maximumMae, double maximumMse, double maximumRmse,
            double maximumSmape, double minimumAccuracy,
            double accuracyTolerance) {
        this(enabled, null, minimumMatchedSamples, maximumMae, maximumMse,
                maximumRmse, maximumSmape, minimumAccuracy, accuracyTolerance);
    }

    public LifecycleSettings(boolean enabled, String baselinePredictorName,
            int minimumMatchedSamples, double maximumMae, double maximumMse,
            double maximumRmse, double maximumSmape, double minimumAccuracy,
            double accuracyTolerance) {
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

    public LifecycleSettings(boolean enabled, String baselinePredictorName,
            int minimumMatchedSamples, double maximumMae, double maximumMse,
            double maximumRmse, double maximumSmape, double minimumAccuracy,
            double accuracyTolerance, double absoluteTolerance) {
        this(enabled, baselinePredictorName, minimumMatchedSamples,
                maximumMae, maximumMse, maximumRmse, maximumSmape,
                minimumAccuracy, accuracyTolerance);
        this.absoluteTolerance = absoluteTolerance;
    }

    public boolean isBaselinePredictor(String predictorName) {
        return baselinePredictorName != null
                && !baselinePredictorName.isBlank()
                && baselinePredictorName.equals(predictorName);
    }

    public LifecyclePolicy toPolicy() {
        double effectiveAbsoluteTolerance = absoluteTolerance == null
                ? accuracyTolerance : absoluteTolerance;
        return new LifecyclePolicy(minimumMatchedSamples, maximumMae,
                maximumMse, maximumRmse, maximumSmape, minimumAccuracy,
                accuracyTolerance, effectiveAbsoluteTolerance);
    }
}