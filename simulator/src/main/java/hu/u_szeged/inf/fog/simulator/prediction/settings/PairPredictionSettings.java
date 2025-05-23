package hu.u_szeged.inf.fog.simulator.prediction.settings;

import hu.u_szeged.inf.fog.simulator.prediction.Prediction;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.FromJsonFieldAliases;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonFieldName;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonParseIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

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

    public PairPredictionSettings(String predictorName, PredictionSettings predictionSettings, PredictorSettings predictorSettings) {
        this.predictorName = predictorName;
        this.predictionSettings = predictionSettings;
        this.predictorSettings = predictorSettings;
        this.bestPredictor = 0;
    }

    public void increaseBestPredictor() {
        bestPredictor++;
    }

    public static void increaseBestPredictorCounterFromPredictions(Collection<Prediction> predictions) {
        for (Prediction prediction : Prediction.getBestPredictions(predictions).values()) {
            increaseBestPredictorCounterFromPrediction(prediction);
        }
    }

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

    public static PairPredictionSettings getPredictionSettingsByName(String name) {
        return SimulationSettings.get().getPredictionSettings().stream().filter(
                    settings -> settings.getPredictorName().equals(name)
                ).findFirst().orElseThrow();
    }

}
