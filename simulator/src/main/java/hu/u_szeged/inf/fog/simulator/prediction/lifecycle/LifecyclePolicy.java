package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import lombok.Getter;

/**
 * Defines the acceptance thresholds used by the model lifecycle.
 */
@Getter
public class LifecyclePolicy {

    private final int minimumMatchedSamples;
    private final double maximumMae;
    private final double maximumMse;
    private final double maximumRmse;
    private final double maximumSmape;
    private final double minimumAccuracy;
    private final double accuracyTolerance;
    private final double absoluteTolerance;

    /**
     * Preserves the original accuracy tolerance rule.
     */
    public LifecyclePolicy(int minimumMatchedSamples, double maximumMae,
            double maximumMse, double maximumRmse, double maximumSmape,
            double minimumAccuracy, double accuracyTolerance) {
        this(minimumMatchedSamples, maximumMae, maximumMse, maximumRmse,
                maximumSmape, minimumAccuracy, accuracyTolerance,
                accuracyTolerance);
    }

    public LifecyclePolicy(int minimumMatchedSamples, double maximumMae,
            double maximumMse, double maximumRmse, double maximumSmape,
            double minimumAccuracy, double accuracyTolerance,
            double absoluteTolerance) {
        this.minimumMatchedSamples = minimumMatchedSamples;
        this.maximumMae = maximumMae;
        this.maximumMse = maximumMse;
        this.maximumRmse = maximumRmse;
        this.maximumSmape = maximumSmape;
        this.minimumAccuracy = minimumAccuracy;
        this.accuracyTolerance = accuracyTolerance;
        this.absoluteTolerance = absoluteTolerance;
    }

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