package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.FromJsonFieldAliases;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonFieldName;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a prediction with its data, metrics, settings and model status.
 */
@Getter
@NoArgsConstructor
public class Prediction {

    @Setter
    @ToJsonFieldName(value = "window_start_timestamp")
    @FromJsonFieldAliases(fieldNames = {"window_start_timestamp"})
    private int windowStartTimestamp = -1;

    @Getter
    @NoArgsConstructor
    public class ErrorMetrics {
        @FromJsonFieldAliases(fieldNames = {"RMSE"})
        private double rmse = Double.NaN;

        @FromJsonFieldAliases(fieldNames = {"MSE"})
        private double mse = Double.NaN;

        @FromJsonFieldAliases(fieldNames = {"MAE"})
        private double mae = Double.NaN;
    }

    @Getter
    @NoArgsConstructor
    public class Data {
        private List<Integer> timestamp;
        private List<Double> data;
    }

    /**
     * Model diagnostics for one prediction phase.
     * Null Boolean values mean that the information is unavailable.
     */
    @Getter
    @NoArgsConstructor
    public static class ModelStatus {
        private String model;
        private String feature;
        private String phase;
        private String status;
        private Boolean converged;
        private Boolean finite;
        private List<Integer> order;

        @ToJsonFieldName(value = "error_type")
        @FromJsonFieldAliases(fieldNames = {"error_type"})
        private String errorType;

        private String error;
    }

    /**
     * Keeps future and historical test diagnostics separate.
     */
    @Getter
    @NoArgsConstructor
    public static class PredictionStatus {
        private ModelStatus future;
        private ModelStatus test;
    }

    @ToJsonFieldName(value = "feature_name")
    @FromJsonFieldAliases(fieldNames = {"feature_name"})
    private String featureName;

    @Setter
    @ToJsonFieldName(value = "prediction_number")
    @FromJsonFieldAliases(fieldNames = {"prediction_number"})
    private int predictionNumber;

    @Setter
    @ToJsonFieldName(value = "prediction_settings")
    @FromJsonFieldAliases(fieldNames = {"prediction_settings"})
    private PairPredictionSettings predictionSettings;

    @ToJsonFieldName(value = "original_data")
    @FromJsonFieldAliases(fieldNames = {"original_data"})
    private Data originalData;

    @ToJsonFieldName(value = "preprocessed_data")
    @FromJsonFieldAliases(fieldNames = {"preprocessed_data"})
    private Data preprocessedData;

    @ToJsonFieldName(value = "test_data_beginning")
    @FromJsonFieldAliases(fieldNames = {"test_data_beginning"})
    private Data testDataBeginning;

    @ToJsonFieldName(value = "test_data_end")
    @FromJsonFieldAliases(fieldNames = {"test_data_end"})
    private Data testDataEnd;

    @ToJsonFieldName(value = "prediction_future")
    @FromJsonFieldAliases(fieldNames = {"prediction_future"})
    private Data predictionFuture;

    @ToJsonFieldName(value = "prediction_test")
    @FromJsonFieldAliases(fieldNames = {"prediction_test"})
    private Data predictionTest;

    @ToJsonFieldName(value = "error_metrics")
    @FromJsonFieldAliases(fieldNames = {"error_metrics"})
    private ErrorMetrics errorMetrics;

    @ToJsonFieldName(value = "prediction_time")
    @FromJsonFieldAliases(fieldNames = {"prediction_time"})
    private double predictionTime;

    @ToJsonFieldName(value = "prediction_status")
    @FromJsonFieldAliases(fieldNames = {"prediction_status"})
    private PredictionStatus predictionStatus;

    /**
     * Selects the lowest-MAE prediction for each feature.
     * Features without a valid candidate are omitted.
     */
    public static Map<String, Prediction> getBestPredictions(
            Collection<Prediction> predictions) {
        Map<String, Prediction> bestPredictions = new HashMap<>();
        if (predictions == null) {
            return bestPredictions;
        }

        for (Prediction prediction : predictions) {
            if (!hasValidSelectionMetric(prediction)
                    || prediction.getFeatureName() == null) {
                continue;
            }

            Prediction current = bestPredictions.get(prediction.getFeatureName());
            if (current == null
                    || prediction.getErrorMetrics().getMae()
                    < current.getErrorMetrics().getMae()) {
                bestPredictions.put(prediction.getFeatureName(), prediction);
            }
        }
        return bestPredictions;
    }

    /**
     * Selects by MAE only. Candidates must refer to the same feature
     * and comparable evaluation windows, as supplied by the caller.
     */
    public static Prediction getBestPrediction(Collection<Prediction> predictions) {
        if (predictions == null) {
            throw new IllegalArgumentException("Predictions must not be null");
        }
        return predictions.stream()
                .filter(Prediction::hasValidSelectionMetric)
                .min(Comparator.comparingDouble(
                    prediction -> prediction.getErrorMetrics().getMae()))
                .orElseThrow(() -> new IllegalArgumentException(
                    "No prediction has a finite, non-negative MAE"));
    }

    private static boolean hasValidSelectionMetric(Prediction prediction) {
        return prediction != null
                && prediction.getErrorMetrics() != null
                && Double.isFinite(prediction.getErrorMetrics().getMae())
                && prediction.getErrorMetrics().getMae() >= 0;
    }

    /**
     * Returns the predictions for the requested feature.
     */
    public static Collection<Prediction> getPredictionsForFeature(
            Collection<Prediction> predictions, String featureName) {
        return predictions.stream().filter(
                prediction -> prediction.getFeatureName().equals(featureName)
        ).collect(Collectors.toList());
    }
}