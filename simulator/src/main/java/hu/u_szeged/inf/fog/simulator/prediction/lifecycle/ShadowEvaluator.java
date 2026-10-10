package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Matches shadow predictions with actual values and calculates metrics.
 */
public class ShadowEvaluator {

    private final List<ShadowPrediction> predictions = new ArrayList<>();

    public void registerPrediction(ShadowPrediction prediction) {
        if (prediction == null) {
            throw new IllegalArgumentException("Prediction must not be null.");
        }
        predictions.add(prediction);
    }

    public int resolveActualValue(String featureName, int targetTimestamp,
            double actualValue) {
        int resolvedCount = 0;
        for (ShadowPrediction prediction : predictions) {
            if (!prediction.isResolved()
                    && prediction.getFeatureName().equals(featureName)
                    && prediction.getTargetTimestamp() == targetTimestamp) {
                prediction.resolve(actualValue);
                resolvedCount++;
            }
        }
        return resolvedCount;
    }

    /**
     * Preserves the original tolerance rule for existing callers.
     */
    public EvaluationMetrics calculateMetrics(String predictorName,
            String featureName, double accuracyTolerance) {
        return calculateMetrics(predictorName, featureName,
                accuracyTolerance, Integer.MAX_VALUE);
    }

    /**
     * Preserves relativeTolerance * max(abs(actual), 1).
     */
    public EvaluationMetrics calculateMetrics(String predictorName,
            String featureName, double accuracyTolerance,
            int maximumMatchedSamples) {
        return calculateMetrics(predictorName, featureName,
                accuracyTolerance, accuracyTolerance, maximumMatchedSamples);
    }

    /**
     * Uses max(absoluteTolerance, relativeTolerance * abs(actual)).
     * Tolerances affect accuracy only; error metrics remain unchanged.
     */
    public EvaluationMetrics calculateMetrics(String predictorName,
            String featureName, double relativeTolerance,
            double absoluteTolerance, int maximumMatchedSamples) {
        validateTolerance(relativeTolerance);
        validateTolerance(absoluteTolerance);
        if (maximumMatchedSamples < 1) {
            throw new IllegalArgumentException(
                    "Maximum matched samples must be positive.");
        }

        List<ShadowPrediction> resolved =
                getResolvedPredictions(predictorName, featureName);
        resolved.sort(Comparator.comparingInt(ShadowPrediction::getTargetTimestamp)
                .thenComparingInt(ShadowPrediction::getPredictionNumber));

        int firstIndex = Math.max(0, resolved.size() - maximumMatchedSamples);
        int matchedSamples = resolved.size() - firstIndex;
        if (matchedSamples == 0) {
            return new EvaluationMetrics(0, 0, 0, 0, 0, 0);
        }

        int accurateSamples = 0;
        double absoluteErrorSum = 0;
        double squaredErrorSum = 0;
        double smapeSum = 0;

        for (int index = firstIndex; index < resolved.size(); index++) {
            ShadowPrediction prediction = resolved.get(index);
            double predicted = prediction.getPredictedValue();
            double actual = prediction.getActualValue();
            double error = Math.abs(predicted - actual);

            absoluteErrorSum += error;
            squaredErrorSum += error * error;

            double denominator = Math.abs(predicted) + Math.abs(actual);
            if (denominator > 0) {
                smapeSum += 200.0 * error / denominator;
            }

            double allowedError = Math.max(absoluteTolerance,
                    relativeTolerance * Math.abs(actual));
            if (error <= allowedError) {
                accurateSamples++;
            }
        }

        double mae = absoluteErrorSum / matchedSamples;
        double mse = squaredErrorSum / matchedSamples;
        double rmse = Math.sqrt(mse);
        double smape = smapeSum / matchedSamples;
        double accuracy = 100.0 * accurateSamples / matchedSamples;
        return new EvaluationMetrics(matchedSamples, mae, mse, rmse,
                smape, accuracy);
    }

    public int getResolvedPredictionCount(String predictorName,
            String featureName) {
        return getResolvedPredictions(predictorName, featureName).size();
    }

    public void clear() {
        predictions.clear();
    }

    private static void validateTolerance(double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance < 0) {
            throw new IllegalArgumentException(
                    "Accuracy tolerance must be finite and non-negative.");
        }
    }

    private List<ShadowPrediction> getResolvedPredictions(String predictorName,
            String featureName) {
        List<ShadowPrediction> result = new ArrayList<>();
        for (ShadowPrediction prediction : predictions) {
            if (prediction.isResolved()
                    && prediction.getPredictorName().equals(predictorName)
                    && prediction.getFeatureName().equals(featureName)) {
                result.add(prediction);
            }
        }
        return result;
    }
}