package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.*;

import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.OnlineTrainingSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictorSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.TrainSettings;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class PatchTstModelAdapterTest {
    private TrainSettings settings() {
        return new TrainSettings("patchtst_i192_o64.keras", "input", "output",
                false, null, 192, 64, "combined_mae_mean10", "Adam", 40, false);
    }

    @Test
    void patchtstNamesAndWindowKeepCandidateIdentitySeparate() {
        PatchTstModelAdapter adapter = new PatchTstModelAdapter();
        assertEquals("PATCHTST", adapter.getModelType());
        assertEquals("shared", adapter.getRegistryFeatureName());
        assertEquals("PatchTST256-v1", adapter.getVersionName(1));
        assertEquals("PatchTST256-v2", adapter.getVersionName(2));
        assertThrows(IllegalArgumentException.class, () -> adapter.getVersionName(0));
        assertEquals(287, adapter.getInitialWindowPoints(
                settings(), OnlineTrainingSettings.getDefault()));
    }

    @Test
    void serializedRequestRoutesBothForecastsToThePatchTstArtifact() throws Exception {
        String artifact = Path.of("candidate.keras").toAbsolutePath().toString();
        PairPredictionSettings pair = new PatchTstModelAdapter().createPredictionSettings(
                "PatchTST256-v1", artifact, settings());
        JSONObject json = JsonParser.toJson(pair, PairPredictionSettings.class);
        assertEquals("PatchTST256-v1", json.getString("predictor_name"));
        JSONObject prediction = json.getJSONObject("prediction");
        assertEquals(256, prediction.getInt("batchSize"));
        assertEquals(64, prediction.getInt("length"));
        assertEquals(64, prediction.getInt("testSize"));
        assertFalse(prediction.getBoolean("scale"));
        assertTrue(!prediction.has("smoothing") || prediction.isNull("smoothing"));
        JSONObject predictor = json.getJSONObject("predictor");
        assertEquals("PATCHTST", predictor.getString("predictor"));
        JSONObject parameters = predictor.getJSONObject("hyperparameters");
        assertEquals(artifact, parameters.getString("future_model_location"));
        assertEquals(artifact, parameters.getString("test_model_location"));
    }

    @Test
    void externalScalingIsRejectedBeforeLaunchingTraining() {
        TrainSettings settings = settings();
        settings.setScale(true);
        assertThrows(IllegalArgumentException.class,
                () -> new PatchTstModelAdapter().train(settings, Path.of("unused")));
        assertThrows(IllegalArgumentException.class,
                () -> new PatchTstModelAdapter().createPredictionSettings(
                        "PatchTST256-v1", "model.keras", settings));
    }

    @Test
    void patchtstTemplateDoesNotReuseTheLstmRegistryType() throws Exception {
        PredictorSettings settings = PredictorSettings.getPredictorSettings(
                PredictorSettings.PredictorEnum.PATCHTST);
        assertEquals("PATCHTST", settings.getPredictor());
        assertEquals("<path_to_keras>", settings.getHyperparameters()
                .get("future_model_location"));
        assertEquals("LSTM", PredictorSettings.getPredictorSettings(
                PredictorSettings.PredictorEnum.LSTM).getPredictor());
    }
}
