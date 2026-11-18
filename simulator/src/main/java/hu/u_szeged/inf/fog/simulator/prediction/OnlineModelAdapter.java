package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.TrainSettings;
import java.nio.file.Path;
import org.json.JSONObject;

/**
 * Supplies model-specific operations to an online lifecycle controller.
 */
public interface OnlineModelAdapter {

    String getModelType();

    String getRegistryFeatureName();

    String getVersionName(int versionNumber);

    int getInitialWindowPoints(TrainSettings settings,
            hu.u_szeged.inf.fog.simulator.prediction.settings.OnlineTrainingSettings onlineSettings);

    JSONObject train(TrainSettings settings, Path resultDirectory) throws Exception;

    PairPredictionSettings createPredictionSettings(String predictorName,
            String modelPath, TrainSettings settings);
}