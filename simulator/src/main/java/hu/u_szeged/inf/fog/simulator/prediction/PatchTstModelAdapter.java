package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.PythonTrainLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.settings.OnlineTrainingSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictorSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.TrainSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * Adapts the existing PATCHTST trainer and predictor to the lifecycle contract.
 */
public class PatchTstModelAdapter implements OnlineModelAdapter {

    @Override
    public String getModelType() {
        return "PATCHTST";
    }

    @Override
    public String getRegistryFeatureName() {
        return "shared";
    }

    @Override
    public String getVersionName(int versionNumber) {
        if (versionNumber < 1) {
            throw new IllegalArgumentException("Version number must be positive.");
        }
        // Keep each trained candidate distinct in routing and result storage.
        return "PatchTST256-v" + versionNumber;
    }

    @Override
    public int getInitialWindowPoints(TrainSettings settings,
            OnlineTrainingSettings onlineSettings) {
        return onlineSettings.getInitialWindowPoints(settings);
    }

    @Override
    public JSONObject train(TrainSettings settings,
            Path resultDirectory) throws Exception {
        if (settings == null || resultDirectory == null) {
            throw new IllegalArgumentException(
                    "Training settings and result directory are required.");
        }

        validateTrainingSettings(settings);
        Path directory = resultDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        settings.setModelOutputLocation(directory.toString());

        Path resultPath = directory.resolve("patchtst_training_result.json");
        Files.deleteIfExists(resultPath);

        Process process = new PythonTrainLauncher(
                "predictor_models/patchtst_trainer.py",
                "patchtst_training.log", settings).open();

        if (process == null) {
            throw new IllegalStateException("PATCHTST training process could not start.");
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw exception;
        }

        if (exitCode != 0) {
            throw new IllegalStateException(
                    "PATCHTST training exit code=" + exitCode
                            + "; check patchtst_training.log");
        }
        if (!Files.isRegularFile(resultPath)) {
            throw new IllegalStateException(
                    "Training result was not created: " + resultPath);
        }

        JSONObject result = new JSONObject(
                Files.readString(resultPath, StandardCharsets.UTF_8));
        validateResult(result);
        return result;
    }

    @Override
    public PairPredictionSettings createPredictionSettings(
            String predictorName, String modelPath, TrainSettings settings) {
        if (settings == null || settings.getInputSize() < 1
                || settings.getOutputSize() < 1) {
            throw new IllegalArgumentException(
                    "Positive input and output sizes are required.");
        }

        validateTrainingSettings(settings);
        int batchSize = Math.addExact(
                settings.getInputSize(), settings.getOutputSize());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("future_model_location", modelPath);
        parameters.put("test_model_location", modelPath);

        return new PairPredictionSettings(predictorName,
                new PredictionSettings(
                        settings.getOutputSize(), settings.getOutputSize(),
                        batchSize, settings.getSmoothingSettings(),
                        settings.getScale(), 0),
                new PredictorSettings(
                        PredictorSettings.PredictorEnum.PATCHTST.value,
                        parameters, null));
    }

    private void validateTrainingSettings(TrainSettings settings) {
        if (settings.getInputSize() != 192 || settings.getOutputSize() != 64
                || !Boolean.FALSE.equals(settings.getScale())
                || settings.getSmoothingSettings() != null
                || !"combined_mae_mean10".equals(settings.getLossFunction())
                || !"Adam".equals(settings.getOptimizer()) || settings.getEpochs() < 1) {
            throw new IllegalArgumentException(
                    "PATCHTST requires input192/output64, scale=false, no smoothing, "
                    + "combined_mae_mean10, Adam and positive epochs.");
        }
    }

    private void validateResult(JSONObject result) throws org.json.JSONException {
        String[] fields = {
            "artifact_path", "model_version", "model_type", "feature_name"
        };
        for (String field : fields) {
            if (result.getString(field).isBlank()) {
                throw new IllegalStateException(
                        "Training result contains an empty " + field);
            }
        }

        if (!getModelType().equals(result.getString("model_type"))
                || !getRegistryFeatureName().equals(
                        result.getString("feature_name"))) {
            throw new IllegalStateException(
                    "Training result does not belong to the shared PATCHTST model.");
        }

        Path artifact = Path.of(result.getString("artifact_path"));
        if (!artifact.isAbsolute() || !Files.isRegularFile(artifact)) {
            throw new IllegalStateException(
                    "Training result must reference an existing absolute model path.");
        }
    }
}
