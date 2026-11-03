package hu.u_szeged.inf.fog.simulator.prediction.settings;

import hu.u_szeged.inf.fog.simulator.prediction.Prediction;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.FromJsonFieldAliases;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonFieldName;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonParseIgnore;
import java.util.Collection;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Groups predictor settings and prediction statistics.
 */
@Data
@NoArgsConstructor
public class PairPredictionSettings {

    @ToJsonFieldName("predictor_name")
    @FromJsonFieldAliases(fieldNames = {"predictor_name"})
    private String predictorName;
    @ToJsonFieldName("prediction")
    @FromJsonFieldAliases(fieldNames = {"prediction"})
    private PredictionSettings predictionSettings;
    @ToJsonFieldName("predictor")
    @FromJsonFieldAliases(fieldNames = {"predictor"})
    private PredictorSettings predictorSettings;

    @ToJsonParseIgnore
    private int bestPredictor;
    @ToJsonParseIgnore
    private double sumOfErrorMetrics;
    @ToJsonParseIgnore
    private double numberOfPredictions;

    @ToJsonParseIgnore
    private long validMetricCount;
    @ToJsonParseIgnore
    private long invalidMetricCount;
    @ToJsonParseIgnore
    private double meanMae;
    @ToJsonParseIgnore
    private double meanMse;
    @ToJsonParseIgnore
    private double meanRmse;

    public PairPredictionSettings(String predictorName,
            PredictionSettings predictionSettings,
            PredictorSettings predictorSettings) {
        this.predictorName = predictorName;
        this.predictionSettings = predictionSettings;
        this.predictorSettings = predictorSettings;
    }

    /**
     * Preserves the previous six-argument constructor.
     */
    public PairPredictionSettings(String predictorName,
            PredictionSettings predictionSettings,
            PredictorSettings predictorSettings,
            int bestPredictor, double sumOfErrorMetrics,
            double numberOfPredictions) {
        this(predictorName, predictionSettings, predictorSettings);
        this.bestPredictor = bestPredictor;
        this.sumOfErrorMetrics = sumOfErrorMetrics;
        this.numberOfPredictions = numberOfPredictions;
    }

    public void increaseBestPredictor() {
        bestPredictor++;
    }

    /**
     * Legacy accumulator retained while callers are migrated.
     */
    public PairPredictionSettings increaseSumOfErrorMetrics(double value) {
        sumOfErrorMetrics += value;
        return this;
    }

    public PairPredictionSettings increaseNumberOfPredictions() {
        numberOfPredictions++;
        return this;
    }

    /**
     * Records one prediction request and its separate error metrics.
     * Invalid metrics are counted but excluded from the averages.
     */
    public PairPredictionSettings recordPredictionMetrics(
            Prediction.ErrorMetrics metrics) {
        numberOfPredictions++;

        if (metrics == null
                || !isValidMetric(metrics.getMae())
                || !isValidMetric(metrics.getMse())
                || !isValidMetric(metrics.getRmse())) {
            invalidMetricCount++;
            return this;
        }

        validMetricCount++;
        meanMae += (metrics.getMae() - meanMae) / validMetricCount;
        meanMse += (metrics.getMse() - meanMse) / validMetricCount;
        meanRmse += (metrics.getRmse() - meanRmse) / validMetricCount;
        return this;
    }

    private static boolean isValidMetric(double value) {
        return Double.isFinite(value) && value >= 0;
    }

    /**
     * Returns null when no valid metrics have been recorded.
     */
    public Double getAverageMae() {
        return validMetricCount == 0 ? null : meanMae;
    }

    public Double getAverageMse() {
        return validMetricCount == 0 ? null : meanMse;
    }

    public Double getAverageRmse() {
        return validMetricCount == 0 ? null : meanRmse;
    }

    public static void increaseBestPredictorCounterFromPredictions(
            Collection<Prediction> predictions) {
        for (Prediction prediction
                : Prediction.getBestPredictions(predictions).values()) {
            increaseBestPredictorCounterFromPrediction(prediction);
        }
    }

    public static void increaseBestPredictorCounterFromPrediction(
            Prediction prediction) {
        SimulationSettings.get().getPredictionSettings().stream()
                .filter(setting ->
                    setting.getPredictorSettings().equals(
                        prediction.getPredictionSettings().getPredictorSettings())
                    && setting.getPredictionSettings().equals(
                        prediction.getPredictionSettings().getPredictionSettings()))
                .findFirst()
                .orElseThrow()
                .increaseBestPredictor();
    }

    public static PairPredictionSettings getPredictionSettingsByName(String name) {
        return SimulationSettings.get().getPredictionSettings().stream()
                .filter(settings -> settings.getPredictorName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}