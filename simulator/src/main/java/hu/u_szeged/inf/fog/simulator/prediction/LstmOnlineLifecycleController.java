package hu.u_szeged.inf.fog.simulator.prediction;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.LSTMTrainLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.EvaluationMetrics;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleCoordinator;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleState;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.ShadowPrediction;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictorSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.TrainSettings;
import hu.u_szeged.inf.fog.simulator.util.ProjectPathResolver;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Coordinates online LSTM training, shadow evaluation and activation.
 */
public class LstmOnlineLifecycleController {
    public enum Phase {
        IDLE, TRAINING, EVALUATING, ACTIVE, FAILED
    }

    private static final String PREDICTOR_NAME = "LSTM256";
    private static final String TRAINING_DATA_DIRECTORY = "online-training-data";
    private static final String REGISTRY_FEATURE_NAME = "shared";
    private static final String REGISTRY_MODEL_TYPE = "LSTM";
    private static final LstmOnlineLifecycleController INSTANCE = new LstmOnlineLifecycleController();
    private final List<Feature> candidateFeatures = new ArrayList<>();
    private PairPredictionSettings candidateSettings;
    private JSONObject trainingResult;
    private Phase phase = Phase.IDLE;
    private boolean addedToPredictionSettings;

    private LstmOnlineLifecycleController() {
    }

    public static LstmOnlineLifecycleController getInstance() {
        return INSTANCE;
    }

    /**
     * Trains a new LSTM candidate and starts a real future-data shadow test.
     */
    public void startTrainingAndShadowEvaluation() throws Exception {
        TrainSettings trainSettings = SimulationSettings.get().getTrainSettings();
        if (trainSettings == null) {
            return;
        }
        phase = Phase.TRAINING;
        logLifecycle("Training started");
        try {
            removeCandidateFromPredictionSettings();
            LifecycleManager.getInstance().fallbackPredictorToBaselines(PREDICTOR_NAME);
            deactivateRegistryModel();
            closePredictor();
            exportTrainingSnapshot(trainSettings);
            runTrainer();
            logLifecycle("Training completed");
            trainingResult = readTrainingResult();
            candidateSettings = createCandidateSettings(trainingResult.getString("artifact_path"));
            candidateFeatures.clear();
            candidateFeatures.addAll(getEligibleFeatures(
                    candidateSettings.getPredictionSettings().getBatchSize()));
            if (candidateFeatures.isEmpty()) {
                phase = Phase.FAILED;
                PredictionLogger.info("LSTM lifecycle", "No feature has enough data for shadow evaluation");
                return;
            }
            prepareCoordinators();
            logLifecycle("Shadow candidate values: " + describeFeatureValues());
            openPredictor(candidateSettings);
            requestAndRegisterShadowPredictions();
            phase = Phase.EVALUATING;
            logLifecycle("Shadow evaluation started for " + candidateFeatures.size() + " features");
        } catch (Exception exception) {
            failCandidate(exception);
        }
    }

    /**
     * Checks whether the candidate can start serving or must be retrained.
     * This method is called between deterministic simulation time slices.
     */
    public void checkLifecycle() throws Exception {
        if (phase == Phase.EVALUATING) {
            logLifecycle("Shadow check: " + describeEvaluation());
            if (!hasCompletedEvaluation()) {
                return;
            }
            if (areAllCandidateFeaturesAccepted()) {
                try {
                    activateRegistryModel(trainingResult);
                    activateAcceptedCandidateFeatures();
                    addCandidateToPredictionSettings();
                    phase = Phase.ACTIVE;
                    for (Feature feature : candidateFeatures) {
                        PredictionConfigurator.sqLiteManager.syncLifecycleData(feature.getName());
                    }
                    logLifecycle("Candidate entered ACTIVE; serving predictors: " + describeServingPredictors());
                } catch (Exception exception) {
                    failCandidate(exception);
                }
            } else {
                logLifecycle("Candidate rejected after complete shadow window: " + describeEvaluation());
                startTrainingAndShadowEvaluation();
            }
        } else if (phase == Phase.ACTIVE && hasDegradedFeature()) {
            logLifecycle("Active candidate degraded; restoring baseline");
            startTrainingAndShadowEvaluation();
        }
    }

    /**
     * Closes the online Python predictor when the simulation finishes.
     */
    public void finish() throws Exception {
        closePredictor();
    }

    public Phase getPhase() {
        return phase;
    }

    private void exportTrainingSnapshot(TrainSettings trainSettings) throws Exception {
        Path trainingDirectory = new File(ScenarioBase.resultDirectory, TRAINING_DATA_DIRECTORY)
                .toPath().toAbsolutePath().normalize();
        Files.createDirectories(trainingDirectory);
        FeatureManager.getInstance().exportDatasetToCsv(
                trainingDirectory.toString() + File.separator, "dataset.csv");
        trainSettings.setDatasetsDirectory(trainingDirectory.toString());
        trainSettings.setCollectAllCsvAndTrainOnIt(false);
    }

    private void runTrainer() throws Exception {
        Process trainingProcess = new LSTMTrainLauncher().open();
        if (trainingProcess == null) {
            throw new IllegalStateException("LSTM training process could not be started");
        }
        PredictionLogger.info("LSTM trainer", "Waiting for online training to finish");
        int exitCode;
        try {
            exitCode = trainingProcess.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
        if (exitCode != 0) {
            phase = Phase.FAILED;
            throw new IllegalStateException("LSTM training failed with exit code " + exitCode
                            + ". Check lstm_training.log for details.");
        }
    }

    private JSONObject readTrainingResult() throws Exception {
        File resultFile = new File(ScenarioBase.resultDirectory, "lstm_training_result.json");
        if (!resultFile.isFile()) {
            throw new IllegalStateException("LSTM training result was not created: "
                            + resultFile.getAbsolutePath());
        }
        return new JSONObject(Files.readString(resultFile.toPath(), StandardCharsets.UTF_8));
    }

    private PairPredictionSettings createCandidateSettings(String modelPath) {
        TrainSettings trainSettings = SimulationSettings.get().getTrainSettings();
        Map<String, Object> hyperparameters = new HashMap<>();
        hyperparameters.put("future_model_location", modelPath);
        hyperparameters.put("test_model_location", modelPath);
        return new PairPredictionSettings(PREDICTOR_NAME, new PredictionSettings(trainSettings.getOutputSize(),
                        trainSettings.getOutputSize(), trainSettings.getInputSize()
                                + trainSettings.getOutputSize(), trainSettings.getSmoothingSettings(),
                        trainSettings.getScale(), 0), new PredictorSettings(
                        PredictorSettings.PredictorEnum.LSTM.value, hyperparameters, null));
    }

    private List<Feature> getEligibleFeatures(int batchSize) {
        List<Feature> result = new ArrayList<>();
        for (Feature feature : FeatureManager.getInstance().getFeatures()) {
            if (feature.getValues().size() >= batchSize) {
                result.add(feature);
            }
        }
        return result;
    }

    private void prepareCoordinators() {
        LifecycleManager lifecycleManager = LifecycleManager.getInstance();
        for (Feature feature : candidateFeatures) {
            LifecycleCoordinator coordinator = lifecycleManager.getCoordinator(PREDICTOR_NAME, feature.getName());
            if (coordinator == null) {
                coordinator = new LifecycleCoordinator(PREDICTOR_NAME, feature.getName(),
                                SimulationSettings.get().getLifecycleSettings().toPolicy());
                lifecycleManager.registerCoordinator(coordinator);
                coordinator.startTraining();
            } else {
                coordinator.startRetraining();
            }
            coordinator.startEvaluation();
        }
    }

    private void requestAndRegisterShadowPredictions() throws Exception {
        BufferedWriter writer = PredictionConfigurator.predictor_writer.get(PREDICTOR_NAME);
        BufferedReader reader = PredictionConfigurator.predictor_reader.get(PREDICTOR_NAME);
        int batchSize = candidateSettings.getPredictionSettings().getBatchSize();
        for (Feature feature : candidateFeatures) {
            String payload = new JSONObject().put("feature", new JSONObject()
                    .put("name", feature.getName())
                    .put("values", new JSONArray(feature.getWindowValues(batchSize))))
                    .toString();
            writer.write(payload);
            writer.newLine();
            writer.flush();
            String response = reader.readLine();
            if (response == null || response.startsWith("Error:")) {
                throw new IllegalStateException("LSTM shadow prediction failed for " + feature.getName() + ": "
                                + response);
            }
            Prediction prediction = JsonParser.fromJsonObject(new JSONObject(response).getJSONObject("prediction"), Prediction.class, null);
            int registered = registerFuturePrediction(feature, prediction);
            int required = SimulationSettings.get().getLifecycleSettings()
                    .toPolicy().getMinimumMatchedSamples();
            logLifecycle("Shadow registered " + feature.getName() + "="
                    + registered + " future points; required=" + required);
            if (registered < required) {
                throw new IllegalStateException("LSTM returned only "
                        + registered + " future points for " + feature.getName());
            }
        }
    }

    private int registerFuturePrediction(Feature feature, Prediction prediction) {
        Prediction.Data originalData = prediction.getOriginalData();
        Prediction.Data predictionFuture = prediction.getPredictionFuture();
        if (originalData == null || originalData.getTimestamp() == null || predictionFuture == null
                || predictionFuture.getTimestamp() == null || predictionFuture.getData() == null) {
            return 0;
        }
        int windowStartTimestamp = feature.getValues().size() - originalData.getTimestamp().size();
        int pointCount = Math.min(predictionFuture.getTimestamp().size(),
                predictionFuture.getData().size());
        int registered = 0;
        for (int index = 0; index < pointCount; index++) {
            Object predictedValue = ((List<?>) predictionFuture.getData()).get(index);
            if (!(predictedValue instanceof Number)) {
                continue;
            }
            int targetTimestamp = windowStartTimestamp + predictionFuture.getTimestamp().get(index);
            LifecycleManager.getInstance().registerPrediction(new ShadowPrediction(PREDICTOR_NAME,
                                    feature.getName(), prediction.getPredictionNumber(), targetTimestamp,
                                    ((Number) predictedValue).doubleValue()));
            registered++;
        }
        return registered;
    }

    private boolean areAllCandidateFeaturesActive() {
        if (candidateFeatures.isEmpty()) {
            return false;
        }
        LifecycleManager lifecycleManager = LifecycleManager.getInstance();
        for (Feature feature : candidateFeatures) {
            if (!lifecycleManager.isActive(PREDICTOR_NAME, feature.getName())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasCompletedEvaluation() {
        if (candidateFeatures.isEmpty()) {
            return false;
        }
        LifecycleManager lifecycleManager = LifecycleManager.getInstance();
        for (Feature feature : candidateFeatures) {
            LifecycleCoordinator coordinator = lifecycleManager.getCoordinator(PREDICTOR_NAME, feature.getName());
            if (coordinator == null || !coordinator.hasCompleteEvaluationWindow()) {
                return false;
            }
        }
        return true;
    }

    private boolean areAllCandidateFeaturesAccepted() {
        LifecycleManager lifecycleManager = LifecycleManager.getInstance();
        for (Feature feature : candidateFeatures) {
            LifecycleCoordinator coordinator = lifecycleManager.getCoordinator(
                    PREDICTOR_NAME, feature.getName());
            if (coordinator == null || !coordinator.hasCompleteEvaluationWindow()
                    || !coordinator.isPolicySatisfied() || (coordinator.getState() != LifecycleState.READY
                    && coordinator.getState() != LifecycleState.ACTIVE)) {
                return false;
            }
        }
        return true;
    }

    private void activateAcceptedCandidateFeatures() {
        LifecycleManager lifecycleManager = LifecycleManager.getInstance();
        for (Feature feature : candidateFeatures) {
            LifecycleCoordinator coordinator = lifecycleManager.getCoordinator(
                    PREDICTOR_NAME, feature.getName());
            if (coordinator.getState() == LifecycleState.READY) {
                lifecycleManager.promote(PREDICTOR_NAME, feature.getName());
            }
        }
    }

    private String describeFeatureValues() {
        StringBuilder description = new StringBuilder();
        for (Feature feature : candidateFeatures) {
            if (description.length() > 0) {
                description.append(", ");
            }
            description.append(feature.getName()).append('=').append(feature.getValues().size());
        }
        return description.toString();
    }

    private String describeEvaluation() {
        StringBuilder description = new StringBuilder();
        LifecycleManager lifecycleManager = LifecycleManager.getInstance();
        int required = SimulationSettings.get().getLifecycleSettings().toPolicy().getMinimumMatchedSamples();
        for (Feature feature : candidateFeatures) {
            LifecycleCoordinator coordinator = lifecycleManager.getCoordinator(
                    PREDICTOR_NAME, feature.getName());
            EvaluationMetrics metrics = coordinator == null ? null : coordinator.getLatestMetrics();
            if (description.length() > 0) {
                description.append(", ");
            }
            description.append(feature.getName()).append('=')
                   .append(metrics == null ? 0 : metrics.getMatchedSamples())
                   .append('/').append(required).append('(')
                   .append(coordinator == null ? "MISSING" : coordinator.getState()).append(')');
        }
        return description.toString();
    }

    private String describeServingPredictors() {
        StringBuilder description = new StringBuilder();
        for (PairPredictionSettings settings : SimulationSettings.get().getPredictionSettings()) {
            if (description.length() > 0) {
                description.append(", ");
            }
            description.append(settings.getPredictorName());
        }
        return description.toString();
    }

    private void logLifecycle(String message) {
        String entry = "time=" + Timed.getFireCount() + " phase=" + phase + " " + message;
        PredictionLogger.info("LSTM lifecycle", entry);
        SimLogger.logRun("LSTM lifecycle " + entry);
    }

    private void failCandidate(Exception exception) {
        removeCandidateFromPredictionSettings();
        LifecycleManager lifecycleManager = LifecycleManager.getInstance();
        lifecycleManager.fallbackPredictorToBaselines(PREDICTOR_NAME);
        try {
            deactivateRegistryModel();
        } catch (Exception cleanupException) {
            PredictionLogger.error("LSTM lifecycle", "Could not clear registry ACTIVE pointer: "
                    + cleanupException.getMessage());
        }
        for (Feature feature : candidateFeatures) {
            LifecycleCoordinator coordinator = lifecycleManager.getCoordinator(
                    PREDICTOR_NAME, feature.getName());
            if (coordinator != null) {
                coordinator.markFailed();
            }
            PredictionConfigurator.sqLiteManager.syncLifecycleData(feature.getName());
        }
        closePredictor();
        phase = Phase.FAILED;
        logLifecycle("Candidate failed: " + exception.getMessage()
                + "; serving predictors: " + describeServingPredictors());
    }

    private boolean hasDegradedFeature() {
        LifecycleManager lifecycleManager = LifecycleManager.getInstance();
        for (Feature feature : candidateFeatures) {
            LifecycleCoordinator coordinator = lifecycleManager.getCoordinator(PREDICTOR_NAME, feature.getName());
            if (coordinator != null && coordinator.getState() == LifecycleState.DEGRADED) {
                return true;
            }
        }
        return false;
    }

    private void addCandidateToPredictionSettings() {
        if (addedToPredictionSettings) {
            return;
        }
        SimulationSettings.get().getPredictionSettings().add(candidateSettings);
        addedToPredictionSettings = true;
    }

    private void removeCandidateFromPredictionSettings() {
        if (SimulationSettings.get() == null || SimulationSettings.get().getPredictionSettings() == null) {
            return;
        }
        SimulationSettings.get().getPredictionSettings().removeIf(settings ->
                PREDICTOR_NAME.equals(settings.getPredictorName()));
        addedToPredictionSettings = false;
    }

    private void openPredictor(PairPredictionSettings settings) throws Exception {
        File scriptsDirectory = ProjectPathResolver.getPredictorScriptsDirectory().toFile();
        File errorLog = new File(ScenarioBase.resultDirectory, "prediction_error_logs.txt");
        JSONObject arguments = new JSONObject().put("predictor-settings", JsonParser.toJson(settings,
                                        PairPredictionSettings.class));
        ProcessBuilder processBuilder = new ProcessBuilder(resolvePythonExecutable(scriptsDirectory),
                        new File(scriptsDirectory, "main.py").getAbsolutePath(),
                        buildPredictorSettingsArgument(arguments));
        processBuilder.directory(scriptsDirectory);
        processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(errorLog));
        PredictionConfigurator.addPredictorProcess(PREDICTOR_NAME, processBuilder.start());
    }

    private String buildPredictorSettingsArgument(JSONObject arguments) {
        String json = arguments.toString();
        if (SystemUtils.IS_OS_WINDOWS) {
            return json.replace("\"", "\\\"");
        }
        return json;
    }

    private String resolvePythonExecutable(File scriptsDirectory) {
        String[] candidates;
        if (SystemUtils.IS_OS_WINDOWS) {
            candidates = new String[]{
                "venv" + File.separator + "Scripts" + File.separator + "python.exe", "venv" + File.separator
                        + "python.exe"
            };
        } else {
            candidates = new String[]{
                "venv" + File.separator + "bin" + File.separator + "python"
            };
        }
        for (String candidate : candidates) {
            File executable = new File(scriptsDirectory, candidate);
            if (executable.isFile()) {
                return executable.getAbsolutePath();
            }
        }
        throw new IllegalStateException("Python executable was not found in " + scriptsDirectory.getAbsolutePath());
    }

    private void activateRegistryModel(JSONObject result) throws Exception {
        File scriptsDirectory = ProjectPathResolver.getPredictorScriptsDirectory().toFile();
        ProcessBuilder processBuilder = new ProcessBuilder(resolvePythonExecutable(scriptsDirectory),
                        new File(scriptsDirectory, "model_registry.py").getAbsolutePath(), "activate",
                        result.getString("feature_name"), result.getString("model_type"), result.getString("model_version"));
        processBuilder.directory(scriptsDirectory);
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(ScenarioBase.resultDirectory,
                                "model_registry.log")));
        int exitCode = processBuilder.start().waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Model registry activation failed with exit code " + exitCode);
        }
    }

    private void deactivateRegistryModel() throws Exception {
        File scriptsDirectory = ProjectPathResolver.getPredictorScriptsDirectory().toFile();
        ProcessBuilder processBuilder = new ProcessBuilder(resolvePythonExecutable(scriptsDirectory),
                        new File(scriptsDirectory, "model_registry.py").getAbsolutePath(), "deactivate",
                        REGISTRY_FEATURE_NAME, REGISTRY_MODEL_TYPE);
        processBuilder.directory(scriptsDirectory);
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(ScenarioBase.resultDirectory,
                                "model_registry.log")));
        int exitCode = processBuilder.start().waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Model registry deactivation failed with exit code " + exitCode);
        }
        logLifecycle("Registry ACTIVE pointer cleared");
    }

    private void closePredictor() {
        BufferedWriter writer = PredictionConfigurator.predictor_writer.remove(PREDICTOR_NAME);
        BufferedReader reader = PredictionConfigurator.predictor_reader.remove(PREDICTOR_NAME);
        Process process = PredictionConfigurator.predictor.remove(PREDICTOR_NAME);
        try {
            if (writer != null) {
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (process != null && !process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (Exception exception) {
            PredictionLogger.error("LSTM lifecycle", "Failed to close predictor: " + exception.getMessage());
        }
    }
}