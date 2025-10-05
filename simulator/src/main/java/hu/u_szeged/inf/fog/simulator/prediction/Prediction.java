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
 * Represents a prediction with its associated data, error metrics, and settings.
 */
@Getter
@NoArgsConstructor
public class Prediction {
    
    /**
     * Represents error metrics including RMSE, MSE, and MAE.
     */
    @Getter
    @NoArgsConstructor
    public class ErrorMetrics {
        @FromJsonFieldAliases(fieldNames = {"RMSE"})
        private double rmse;
        @FromJsonFieldAliases(fieldNames = {"MSE"})
        private double mse;
        @FromJsonFieldAliases(fieldNames = {"MAE"})
        private double mae;
    }
    
    /**
     * Represents a dataset with timestamps and data points.
     */
    @Getter
    @NoArgsConstructor
    public class Data {
        private List<Integer> timestamp;
        private List<Double> data;
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

    /**
     * Returns the best predictions for features from the given predictions.
     * The deceison is based on the getBestPrediction method.
     *
     * @param predictions the given predictions with possibly multiple predictions for a feature
     * @return The best prediction for the given features
     */
    public static Map<String, Prediction> getBestPredictions(Collection<Prediction> predictions) {
        Map<String, Prediction> bestPredictions = new HashMap<>();
        for (Prediction prediction : predictions) {
            Collection<Prediction> sameFeaturePrediction =
                    Prediction.getPredictionsForFeature(predictions, prediction.getFeatureName());

            bestPredictions.put(prediction.getFeatureName(), Prediction.getBestPrediction(sameFeaturePrediction));
        }
        return bestPredictions;
    }

    /**
     * Returns the best prediction in the given collection based on the average of the error metrics.
     *
     * @param predictions The predictions to decide from.
     */
    public static Prediction getBestPrediction(Collection<Prediction> predictions) {
        return predictions.stream().min(Comparator.comparingDouble(
                (Prediction pred) ->
                        (pred.getErrorMetrics().getMae()
                                + pred.getErrorMetrics().getMse()
                                + pred.getErrorMetrics().getRmse()) / 3
            )).orElseThrow();
    }

    /**
     * Separates the predictions of the given feature from all feature predictions.
     *
     * @param predictions The predictions for possibly multiple features.
     * @param featureName The name of the feature to separate.
     * @return The predictions for the given feature.
     */
    public static Collection<Prediction> getPredictionsForFeature(Collection<Prediction> predictions, String featureName) {
        return predictions.stream().filter(
                predictionForFilter -> predictionForFilter
                        .getFeatureName().equals(featureName)
        ).collect(Collectors.toList());
    }
}