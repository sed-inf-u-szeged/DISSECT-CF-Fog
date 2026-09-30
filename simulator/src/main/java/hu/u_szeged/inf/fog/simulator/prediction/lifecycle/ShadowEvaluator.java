package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import java.util.ArrayList;
import java.util.List;

/**
 * Matches shadow predictions with actual values and calculates
 * independent evaluation metrics.
 */
public class ShadowEvaluator {

    private final List<ShadowPrediction> predictions = new ArrayList<>();

    /**
     * Stores a new prediction until its actual value becomes available.
     *
     * @param prediction the shadow prediction to store
     */
    public void registerPrediction(ShadowPrediction prediction) {
        if (prediction == null) {
            throw new IllegalArgumentException("Prediction must not be null.");
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
            boolean matches = !prediction.isResolved()
                    && prediction.getFeatureName().equals(featureName)
                    && prediction.getTargetTimestamp() == targetTimestamp;

            if (matches) {
                prediction.resolve(actualValue);
                resolvedCount++;
            }
        }

        return resolvedCount;
    }

    /**
     * Calculates metrics for one predictor and one feature using only
     * predictions that already have an actual value.
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
        if (accuracyTolerance < 0) {
            throw new IllegalArgumentException(
                    "Accuracy tolerance must not be negative."
            );
        }

        int matchedSamples = 0;
        int accurateSamples = 0;
        double absoluteErrorSum = 0;
        double squaredErrorSum = 0;
        double smapeSum = 0;

        for (ShadowPrediction prediction : predictions) {
            boolean belongsToEvaluation = prediction.isResolved()
                    && prediction.getPredictorName().equals(predictorName)
                    && prediction.getFeatureName().equals(featureName);

            if (!belongsToEvaluation) {
                continue;
            }

            double predictedValue = prediction.getPredictedValue();
            double actualValue = prediction.getActualValue();
            double absoluteError = Math.abs(predictedValue - actualValue);

            matchedSamples++;
            absoluteErrorSum += absoluteError;
            squaredErrorSum += absoluteError * absoluteError;

            double smapeDenominator =
                    Math.abs(predictedValue) + Math.abs(actualValue);

            if (smapeDenominator > 0) {
                smapeSum += 200.0 * absoluteError / smapeDenominator;
            }

            double accuracyDenominator = Math.max(Math.abs(actualValue), 1.0);

            if (absoluteError
                    <= accuracyTolerance * accuracyDenominator) {
                accurateSamples++;
            }
        }

        if (matchedSamples == 0) {
            return new EvaluationMetrics(0, 0, 0, 0, 0, 0);
        }

        double mae = absoluteErrorSum / matchedSamples;
        double mse = squaredErrorSum / matchedSamples;
        double rmse = Math.sqrt(mse);
        double smape = smapeSum / matchedSamples;
        double accuracy = 100.0 * accurateSamples / matchedSamples;

        return new EvaluationMetrics(
                matchedSamples,
                mae,
                mse,
                rmse,
                smape,
                accuracy
        );
    }
}