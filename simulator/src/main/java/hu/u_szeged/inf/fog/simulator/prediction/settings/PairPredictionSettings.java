package hu.u_szeged.inf.fog.simulator.prediction.settings;

import hu.u_szeged.inf.fog.simulator.prediction.Prediction;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.FromJsonFieldAliases;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonFieldName;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonParseIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;


/**
 * The implementation for the grouping of the PredictionSettings class and the PredictorSettings class
 * with additional fields for statistical purposes.
 */
@Data
@AllArgsConstructor
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

    public PairPredictionSettings(String predictorName, PredictionSettings predictionSettings, PredictorSettings predictorSettings) {
        this.predictorName = predictorName;
        this.predictionSettings = predictionSettings;
        this.predictorSettings = predictorSettings;
        this.bestPredictor = 0;
    }

    public void increaseBestPredictor() {
        bestPredictor++;
    }

    public PairPredictionSettings increaseSumOfErrorMetrics(double value) {
        sumOfErrorMetrics += value;
        return this;
    }

    public PairPredictionSettings increaseNumberOfPredictions() {
        numberOfPredictions++;
        return this;
    }

    /**
     * Increases the best predictor counter for multiple predictions at once.
     * @param predictions The best predictions.
     */
    public static void increaseBestPredictorCounterFromPredictions(Collection<Prediction> predictions) {
        for (Prediction prediction : Prediction.getBestPredictions(predictions).values()) {
            increaseBestPredictorCounterFromPrediction(prediction);
        }
    }

    /**
     * Increases the predictor setting's best predictor counter from the given prediction
     * @param prediction the best prediction.
     */
    public static void increaseBestPredictorCounterFromPrediction(Prediction prediction) {
        SimulationSettings.get().getPredictionSettings().stream()
                .filter(setting ->
                        setting.getPredictorSettings().equals(prediction.getPredictionSettings().getPredictorSettings())
                                && setting.getPredictionSettings().equals(
                                        prediction.getPredictionSettings().getPredictionSettings()
                                    )
                )
                .findFirst()
                .orElseThrow().increaseBestPredictor();
    }

    /**
     * Returns the predictor setting with the given name.
     * @param name The name of the predictor.
     * @return The PairPredictionSettings object with the given name.
     */
    public static PairPredictionSettings getPredictionSettingsByName(String name) {
        return SimulationSettings.get().getPredictionSettings().stream().filter(
                    settings -> settings.getPredictorName().equals(name)
                ).findFirst().orElseThrow();
    }

}
