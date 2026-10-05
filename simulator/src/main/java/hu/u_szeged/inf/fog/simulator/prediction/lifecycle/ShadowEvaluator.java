package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import java.util.ArrayList;
import java.util.List;

/**
 * Matches shadow predictions with actual values and calculates
 * independent evaluation metrics.
 */
public class ShadowEvaluator {

    private final List<ShadowPrediction> predictions =
            new ArrayList<>();

    /**
     * Stores a new prediction until its actual value becomes available.
     *
     * @param prediction the shadow prediction to store
     */
    public void registerPrediction(
            ShadowPrediction prediction
    ) {
        if (prediction == null) {
            throw new IllegalArgumentException(
                    "Prediction must not be null."
            );
        }

        predictions.add(prediction);
    }

    /**
     * Matches an actual value with every unresolved prediction for the same
     * feature and target timestamp.
     *
     * @param featureName the observed feature
     * @param targetTimestamp the timestamp of the actual value
     * @param actualValue the actual observed value
     * @return the number of predictions that were resolved
     */
    public int resolveActualValue(
            String featureName,
            int targetTimestamp,
            double actualValue
    ) {
        int resolvedCount = 0;

        for (ShadowPrediction prediction : predictions) {
            boolean matches =
                    !prediction.isResolved()
                            && prediction.getFeatureName()
                                    .equals(featureName)
                            && prediction.getTargetTimestamp()
                                    == targetTimestamp;

            if (matches) {
                prediction.resolve(actualValue);
                resolvedCount++;
            }
        }

        return resolvedCount;
    }

    /**
     * Calculates metrics from all resolved predictions.
     *
     * This method preserves the behaviour used by the existing lifecycle
     * tests and callers.
     *
     * @param predictorName the predictor to evaluate
     * @param featureName the feature to evaluate
     * @param accuracyTolerance allowed relative error for accuracy
     * @return the calculated evaluation metrics
     */
    public EvaluationMetrics calculateMetrics(
            String predictorName,
            String featureName,
            double accuracyTolerance
    ) {
        return calculateMetrics(
                predictorName,
                featureName,
                accuracyTolerance,
                Integer.MAX_VALUE
        );
    }

    /**
     * Calculates metrics using only the newest resolved predictions.
     *
     * A limited evaluation window is required for continuous monitoring.
     * Otherwise, old successful predictions would permanently hide a later
     * model degradation.
     *
     * @param predictorName the predictor to evaluate
     * @param featureName the feature to evaluate
     * @param accuracyTolerance allowed relative error for accuracy
     * @param maximumMatchedSamples maximum number of newest resolved samples
     * @return the calculated evaluation metrics
     */
    public EvaluationMetrics calculateMetrics(
            String predictorName,
            String featureName,
            double accuracyTolerance,
            int maximumMatchedSamples
    ) {
        if (accuracyTolerance < 0) {
            throw new IllegalArgumentException(
                    "Accuracy tolerance must not be negative."
            );
        }

        if (maximumMatchedSamples < 1) {
            throw new IllegalArgumentException(
                    "Maximum matched samples must be positive."
            );
        }

        List<ShadowPrediction> resolvedPredictions =
                getResolvedPredictions(
                        predictorName,
                        featureName
                );

        int firstPredictionIndex = Math.max(
                0,
                resolvedPredictions.size()
                        - maximumMatchedSamples
        );

        int matchedSamples = 0;
        int accurateSamples = 0;
        double absoluteErrorSum = 0;
        double squaredErrorSum = 0;
        double smapeSum = 0;

        for (int index = firstPredictionIndex;
                index < resolvedPredictions.size();
                index++) {

            ShadowPrediction prediction =
                    resolvedPredictions.get(index);

            double predictedValue =
                    prediction.getPredictedValue();

            double actualValue =
                    prediction.getActualValue();

            double absoluteError =
                    Math.abs(
                            predictedValue - actualValue
                    );

            matchedSamples++;
            absoluteErrorSum += absoluteError;
            squaredErrorSum +=
                    absoluteError * absoluteError;

            double smapeDenominator =
                    Math.abs(predictedValue)
                            + Math.abs(actualValue);

            if (smapeDenominator > 0) {
                smapeSum +=
                        200.0
                                * absoluteError
                                / smapeDenominator;
            }

            double accuracyDenominator =
                    Math.max(
                            Math.abs(actualValue),
                            1.0
                    );

            if (absoluteError
                    <= accuracyTolerance
                    * accuracyDenominator) {
                accurateSamples++;
            }
        }

        if (matchedSamples == 0) {
            return new EvaluationMetrics(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        double mae =
                absoluteErrorSum / matchedSamples;

        double mse =
                squaredErrorSum / matchedSamples;

        double rmse =
                Math.sqrt(mse);

        double smape =
                smapeSum / matchedSamples;

        double accuracy =
                100.0
                        * accurateSamples
                        / matchedSamples;

        return new EvaluationMetrics(
                matchedSamples,
                mae,
                mse,
                rmse,
                smape,
                accuracy
        );
    }

    /**
     * Returns the number of resolved predictions for one predictor-feature
     * combination.
     *
     * @param predictorName the predictor name
     * @param featureName the feature name
     * @return the number of resolved predictions
     */
    public int getResolvedPredictionCount(
            String predictorName,
            String featureName
    ) {
        return getResolvedPredictions(
                predictorName,
                featureName
        ).size();
    }

    /**
     * Removes predictions from the current evaluation generation.
     *
     * This is used before evaluating a newly trained candidate so metrics
     * from the previous model version cannot leak into the new evaluation.
     */
    public void clear() {
        predictions.clear();
    }

    /**
     * Collects resolved predictions belonging to one predictor and feature.
     */
    private List<ShadowPrediction> getResolvedPredictions(
            String predictorName,
            String featureName
    ) {
        List<ShadowPrediction> result =
                new ArrayList<>();

        for (ShadowPrediction prediction : predictions) {
            boolean belongsToEvaluation =
                    prediction.isResolved()
                            && prediction.getPredictorName()
                                    .equals(predictorName)
                            && prediction.getFeatureName()
                                    .equals(featureName);

            if (belongsToEvaluation) {
                result.add(prediction);
            }
        }

        return result;
    }
}