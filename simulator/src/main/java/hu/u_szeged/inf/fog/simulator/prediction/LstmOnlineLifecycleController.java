package hu.u_szeged.inf.fog.simulator.prediction;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.EvaluationMetrics;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleCoordinator;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleState;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.PairedShadowEvaluation;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.ShadowPrediction;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.LifecycleSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.OnlineTrainingSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Manages versioned models and promotes them independently per feature.
 * Paired comparisons are diagnostic and do not change promotion policy.
 */
public class LstmOnlineLifecycleController {

    public enum Phase {
        IDLE, TRAINING, EVALUATING, ACTIVE, FAILED
    }

    private static final LstmOnlineLifecycleController INSTANCE =
            new LstmOnlineLifecycleController();

    private final OnlineModelAdapter modelAdapter;
    private final OnlineTrainingSettings onlineSettings;
    private final Map<String, ModelVersion> versions = new LinkedHashMap<>();
    private final Map<String, Integer> lastAttemptSizes = new HashMap<>();
    private final Map<String, Integer> lastEvaluationSizes = new HashMap<>();
    private final Set<String> retryFeatures = new HashSet<>();
    private final List<PendingComparison> pairedComparisons = new ArrayList<>();

    private ModelVersion latestVersion;
    private Phase phase = Phase.IDLE;
    private int versionNumber;
    private int trainingWindowPoints;
    private String registryVersion;

    private static class ModelVersion {
        private final String name;
        private final PairPredictionSettings settings;
        private final JSONObject result;
        private final Map<String, Feature> features = new LinkedHashMap<>();
        private final Map<String, Integer> lastShadowRequestSizes = new HashMap<>();
        private final Set<String> pending = new HashSet<>();
        private final Set<String> handledDegradation = new HashSet<>();
        private boolean windowGrown;

        private ModelVersion(String name, PairPredictionSettings settings,
                JSONObject result) {
            this.name = name;
            this.settings = settings;
            this.result = result;
        }
    }

    /**
     * Keeps issued forecasts even if their model version is later retired.
     */
    private static class PendingComparison {
        private final Feature feature;
        private final PairedShadowEvaluation evaluation;
        private final double relativeTolerance;
        private final double absoluteTolerance;
        private int nextActual;

        private PendingComparison(Feature feature,
                PairedShadowEvaluation evaluation, LifecycleSettings settings) {
            this.feature = feature;
            this.evaluation = evaluation;
            this.relativeTolerance = settings.getAccuracyTolerance();
            this.absoluteTolerance = settings.getAbsoluteTolerance() == null
                    ? relativeTolerance : settings.getAbsoluteTolerance();
            this.nextActual = evaluation.getOrigin();
        }
    }

    private LstmOnlineLifecycleController() {
        this(new LstmModelAdapter(), OnlineTrainingSettings.getDefault());
    }

    LstmOnlineLifecycleController(OnlineModelAdapter modelAdapter,
            OnlineTrainingSettings onlineSettings) {
        if (modelAdapter == null || onlineSettings == null) {
            throw new IllegalArgumentException(
                    "Model adapter and online training settings are required.");
        }
        this.modelAdapter = modelAdapter;
        this.onlineSettings = onlineSettings;
    }

    public static LstmOnlineLifecycleController getInstance() {
        return INSTANCE;
    }

    public Phase getPhase() {
        return phase;
    }

    public void checkLifecycle() throws Exception {
        if (SimulationSettings.get().getTrainSettings() == null) {
            return;
        }

        resolvePairedComparisons();

        for (ModelVersion version : new ArrayList<>(versions.values())) {
            Process process = PredictionConfigurator.predictor.get(version.name);
            if (process == null || !process.isAlive()) {
                failVersion(version,
                        new IllegalStateException("Prediction process stopped"));
                continue;
            }
            evaluateVersion(version);
            try {
                continueShadowEvaluation(version);
            } catch (Exception exception) {
                failVersion(version, exception);
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }

        if (latestVersion != null) {
            try {
                joinAvailableFeatures(latestVersion);
            } catch (Exception exception) {
                failVersion(latestVersion, exception);
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }

        for (Feature feature : FeatureManager.getInstance().getFeatures()) {
            String name = feature.getName();
            if (!hasServingModel(name) && !hasPendingEvaluation(name)
                    && (latestVersion == null
                    || latestVersion.features.containsKey(name))) {
                retryFeatures.add(name);
            }
        }

        retireUnusedVersions();
        startTrainingAndShadowEvaluation();
        syncRegistry();
        updatePhase();
    }

    public void startTrainingAndShadowEvaluation() throws Exception {
        if (phase == Phase.TRAINING) {
            return;
        }
        TrainSettings settings = SimulationSettings.get().getTrainSettings();
        if (settings == null) {
            return;
        }
        if (trainingWindowPoints == 0) {
            trainingWindowPoints =
                    modelAdapter.getInitialWindowPoints(settings, onlineSettings);
            logLifecycle("Initial training window=" + trainingWindowPoints);
        }

        List<Feature> trainingFeatures = getReadyTrainingFeatures();
        if (trainingFeatures.stream().noneMatch(this::needsTraining)) {
            return;
        }

        String name = modelAdapter.getVersionName(++versionNumber);
        for (Feature feature : trainingFeatures) {
            lastAttemptSizes.put(feature.getName(), feature.getValues().size());
        }
        phase = Phase.TRAINING;
        logLifecycle("Training started: " + name + "; window="
                + trainingWindowPoints + "; features=" + trainingFeatures.size());

        ModelVersion version = null;
        try {
            Path directory = new File(ScenarioBase.resultDirectory,
                    "online-training-data").toPath().toAbsolutePath().normalize();
            FeatureManager.getInstance().exportTrainingWindowToCsv(
                    directory.toString(), "dataset.csv",
                    trainingFeatures, trainingWindowPoints);
            settings.setDatasetsDirectory(directory.toString());
            settings.setCollectAllCsvAndTrainOnIt(false);

            JSONObject result = runTrainer();
            Files.writeString(new File(ScenarioBase.resultDirectory,
                    name + "-training-result.json").toPath(),
                    result.toString(2), StandardCharsets.UTF_8);

            version = new ModelVersion(name,
                    createSettings(name, result.getString("artifact_path")), result);
            versions.put(name, version);
            openPredictor(version);
            joinAvailableFeatures(version);

            latestVersion = version;
            retireUnusedVersions();
            phase = Phase.EVALUATING;
            logLifecycle("Shadow evaluation started: " + name
                    + "; features=" + version.pending.size());
        } catch (Exception exception) {
            if (version != null) {
                failVersion(version, exception);
            } else {
                phase = Phase.FAILED;
                logLifecycle("Training failed: " + name + "; "
                        + exception.getMessage() + "; retry requires new data");
            }
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw exception;
            }
        }
    }

    private boolean needsTraining(Feature feature) {
        String name = feature.getName();
        if (hasServingModel(name) || hasPendingEvaluation(name)
                || (versionNumber != 0 && !retryFeatures.contains(name))) {
            return false;
        }
        int previous = Math.max(lastAttemptSizes.getOrDefault(name, -1),
                lastEvaluationSizes.getOrDefault(name, -1));
        return previous < 0 || (long) feature.getValues().size() - previous
                >= onlineSettings.getMinimumNewDataPoints();
    }

    private List<Feature> getReadyTrainingFeatures() {
        List<Feature> result = new ArrayList<>();
        for (Feature feature : FeatureManager.getInstance().getFeatures()) {
            int size = feature.getValues().size();
            Integer previous = lastAttemptSizes.get(feature.getName());
            if (previous != null && (long) size - previous
                    < onlineSettings.getMinimumNewDataPoints()) {
                continue;
            }
            if (hasFiniteWindow(feature, trainingWindowPoints)) {
                result.add(feature);
            }
        }
        return result;
    }

    private boolean hasFiniteWindow(Feature feature, int size) {
        List<Double> values = feature.getValues();
        if (size < 1 || values.size() < size) {
            return false;
        }
        for (int i = values.size() - size; i < values.size(); i++) {
            Double value = values.get(i);
            if (value == null || !Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private void joinAvailableFeatures(ModelVersion version) throws Exception {
        int batchSize = version.settings.getPredictionSettings().getBatchSize();
        LifecycleManager manager = LifecycleManager.getInstance();

        for (Feature feature : FeatureManager.getInstance().getFeatures()) {
            String name = feature.getName();
            if (version.features.containsKey(name)
                    || hasPendingEvaluation(name)
                    || !hasFiniteWindow(feature, batchSize)) {
                continue;
            }

            LifecycleCoordinator coordinator =
                    manager.getCoordinator(version.name, name);
            if (coordinator == null) {
                coordinator = new LifecycleCoordinator(version.name, name,
                        SimulationSettings.get().getLifecycleSettings().toPolicy());
                manager.registerCoordinator(coordinator);
            }
            coordinator.setAutomaticPromotionAllowed(false);
            coordinator.startTraining();
            coordinator.startEvaluation();

            version.features.put(name, feature);
            version.pending.add(name);
            lastEvaluationSizes.put(name, feature.getValues().size());
            requestShadowPrediction(version, feature);

            syncFeature(name);
            logLifecycle("Feature joined shadow: " + version.name + "; " + name
                    + "; available points=" + feature.getValues().size());
        }
    }

    /**
     * Requests another batch only after previous targets have been resolved.
     */
    private void continueShadowEvaluation(ModelVersion version) throws Exception {
        LifecycleManager manager = LifecycleManager.getInstance();
        for (String name : new ArrayList<>(version.pending)) {
            LifecycleCoordinator coordinator =
                    manager.getCoordinator(version.name, name);
            if (coordinator == null
                    || coordinator.getState() != LifecycleState.EVALUATING
                    || coordinator.hasCompleteEvaluationWindow()
                    || coordinator.getPendingShadowPredictionCount() > 0) {
                continue;
            }

            Feature feature = version.features.get(name);
            Integer previousSize = version.lastShadowRequestSizes.get(name);
            if (feature == null || previousSize == null
                    || feature.getValues().size() <= previousSize) {
                continue;
            }
            int batchSize = version.settings.getPredictionSettings().getBatchSize();
            if (!hasFiniteWindow(feature, batchSize)) {
                continue;
            }
            requestShadowPrediction(version, feature);
            syncFeature(name);
        }
    }

    private void evaluateVersion(ModelVersion version) {
        LifecycleManager manager = LifecycleManager.getInstance();

        for (String name : new ArrayList<>(version.pending)) {
            LifecycleCoordinator coordinator =
                    manager.getCoordinator(version.name, name);
            if (!coordinator.hasCompleteEvaluationWindow()) {
                continue;
            }

            if (coordinator.getState() == LifecycleState.READY
                    && coordinator.isPolicySatisfied()) {
                if (SimulationSettings.get().getPredictionSettings().stream()
                        .noneMatch(item -> version.name.equals(item.getPredictorName()))) {
                    SimulationSettings.get().getPredictionSettings().add(version.settings);
                }
                String previous = manager.getActivePredictorName(name);
                manager.promote(version.name, name);
                version.pending.remove(name);
                retryFeatures.remove(name);
                logLifecycle("Feature entered ACTIVE: " + name
                        + "; previous=" + previous + "; version=" + version.name);
            } else if (coordinator.getState() == LifecycleState.EVALUATING
                    && !coordinator.isPolicySatisfied()) {
                version.pending.remove(name);
                coordinator.markFailed();
                if (!hasServingModel(name)) {
                    retryFeatures.add(name);
                    growWindow(version);
                }
                logLifecycle("Shadow rejected: " + version.name + "; " + name
                        + "; current=" + manager.getActivePredictorName(name));
            }
            syncFeature(name);
        }

        for (String name : version.features.keySet()) {
            LifecycleCoordinator coordinator =
                    manager.getCoordinator(version.name, name);
            if (coordinator.getState() == LifecycleState.DEGRADED
                    && version.handledDegradation.add(name)) {
                if (manager.isActive(version.name, name)) {
                    manager.fallbackToBaseline(name);
                }
                if (!hasServingModel(name)) {
                    retryFeatures.add(name);
                }
                syncFeature(name);
                logLifecycle("Feature degraded: " + version.name + "; " + name
                        + "; current=" + manager.getActivePredictorName(name));
            }
        }
    }

    private void growWindow(ModelVersion version) {
        if (!version.windowGrown) {
            trainingWindowPoints = (int) Math.min(
                    onlineSettings.getMaximumWindowPoints(),
                    (long) trainingWindowPoints + onlineSettings.getWindowGrowthPoints());
            version.windowGrown = true;
            logLifecycle("Next training window=" + trainingWindowPoints);
        }
    }

    private boolean hasServingModel(String featureName) {
        LifecycleManager manager = LifecycleManager.getInstance();
        for (ModelVersion version : versions.values()) {
            LifecycleCoordinator coordinator =
                    manager.getCoordinator(version.name, featureName);
            if (coordinator != null && coordinator.getState() == LifecycleState.ACTIVE
                    && manager.isActive(version.name, featureName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPendingEvaluation(String featureName) {
        return versions.values().stream()
                .anyMatch(version -> version.pending.contains(featureName));
    }

    private boolean isServing(ModelVersion version) {
        LifecycleManager manager = LifecycleManager.getInstance();
        return version.features.keySet().stream()
                .anyMatch(name -> manager.isActive(version.name, name));
    }

    private void retireUnusedVersions() {
        for (ModelVersion version : new ArrayList<>(versions.values())) {
            if (version != latestVersion && version.pending.isEmpty()
                    && !isServing(version)) {
                removeFromPredictionSettings(version.name);
                closePredictor(version.name);
                versions.remove(version.name);
                for (String name : version.features.keySet()) {
                    syncFeature(name);
                }
                logLifecycle("Unused version closed: " + version.name);
            }
        }
    }

    private void failVersion(ModelVersion version, Exception exception) {
        LifecycleManager manager = LifecycleManager.getInstance();
        for (String name : version.features.keySet()) {
            if (manager.isActive(version.name, name)) {
                manager.fallbackToBaseline(name);
            }
            LifecycleCoordinator coordinator =
                    manager.getCoordinator(version.name, name);
            if (coordinator != null) {
                coordinator.markFailed();
            }
        }
        version.pending.clear();
        removeFromPredictionSettings(version.name);

        for (String name : version.features.keySet()) {
            syncFeature(name);
        }
        closePredictor(version.name);
        versions.remove(version.name);
        if (latestVersion == version) {
            latestVersion = null;
        }
        for (String name : version.features.keySet()) {
            if (!hasServingModel(name)) {
                retryFeatures.add(name);
            }
        }
        phase = Phase.FAILED;
        logLifecycle("Version failed: " + version.name + "; " + exception.getMessage());
    }

    private void updatePhase() {
        if (versions.values().stream().anyMatch(version -> !version.pending.isEmpty())) {
            phase = Phase.EVALUATING;
        } else if (versions.values().stream().anyMatch(this::isServing)) {
            phase = Phase.ACTIVE;
        } else {
            phase = Phase.IDLE;
        }
    }

    private JSONObject runTrainer() throws Exception {
        return modelAdapter.train(
                SimulationSettings.get().getTrainSettings(),
                new File(ScenarioBase.resultDirectory).toPath());
    }

    private PairPredictionSettings createSettings(String name, String modelPath) {
        return modelAdapter.createPredictionSettings(
                name, modelPath, SimulationSettings.get().getTrainSettings());
    }

    private void requestShadowPrediction(ModelVersion version, Feature feature)
            throws Exception {
        LifecycleCoordinator coordinator = LifecycleManager.getInstance()
                .getCoordinator(version.name, feature.getName());
        if (coordinator == null
                || coordinator.getState() != LifecycleState.EVALUATING) {
            throw new IllegalStateException(
                    "Shadow coordinator is not evaluating: " + feature.getName());
        }

        int availablePoints = feature.getValues().size();
        Prediction prediction = requestPredictionAtOrigin(
                version.settings, feature, availablePoints);

        int previousPending = coordinator.getPendingShadowPredictionCount();
        registerFuturePrediction(version.name, feature, prediction);
        int registered =
                coordinator.getPendingShadowPredictionCount() - previousPending;
        if (registered == 0) {
            throw new IllegalStateException(
                    "No new valid future predictions for " + feature.getName());
        }

        version.lastShadowRequestSizes.put(feature.getName(), availablePoints);
        int matched = coordinator.getLatestMetrics() == null ? 0
                : coordinator.getLatestMetrics().getMatchedSamples();
        int required = SimulationSettings.get().getLifecycleSettings()
                .getMinimumMatchedSamples();
        logLifecycle("Shadow batch registered: " + version.name + "; "
                + feature.getName() + "; new=" + registered
                + "; matched=" + matched + "; required=" + required);

        startPairedComparison(version, feature, availablePoints, prediction);
    }

    /**
     * Uses only the input window ending immediately before the given origin.
     * Diagnostic requests do not enter UI history or prediction statistics.
     */
    private Prediction requestPredictionAtOrigin(PairPredictionSettings settings,
            Feature feature, int origin) throws Exception {
        String name = settings.getPredictorName();
        BufferedWriter writer = PredictionConfigurator.predictor_writer.get(name);
        BufferedReader reader = PredictionConfigurator.predictor_reader.get(name);
        if (writer == null || reader == null) {
            throw new IllegalStateException("Prediction streams are unavailable: " + name);
        }

        int batchSize = settings.getPredictionSettings().getBatchSize();
        if (batchSize < 1 || origin < batchSize
                || origin > feature.getValues().size()) {
            throw new IllegalArgumentException("Insufficient input data for " + name);
        }
        int start = origin - batchSize;
        List<Double> window = new ArrayList<>(
                feature.getValues().subList(start, origin));
        for (Double value : window) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Invalid input data for " + name);
            }
        }

        JSONObject payload = new JSONObject().put("feature", new JSONObject()
                .put("name", feature.getName()).put("values", new JSONArray(window)));
        writer.write(payload.toString());
        writer.newLine();
        writer.flush();

        String response = reader.readLine();
        if (response == null || response.startsWith("Error:")) {
            throw new IllegalStateException(
                    "Prediction failed for " + name + ": " + response);
        }
        Prediction prediction = JsonParser.fromJsonObject(
                new JSONObject(response).getJSONObject("prediction"),
                Prediction.class, null);
        if (!feature.getName().equals(prediction.getFeatureName())
                || prediction.getOriginalData() == null
                || prediction.getOriginalData().getTimestamp() == null
                || prediction.getOriginalData().getTimestamp().size() != batchSize) {
            throw new IllegalStateException("Prediction response window mismatch: " + name);
        }
        prediction.setWindowStartTimestamp(start);
        return prediction;
    }

    private void startPairedComparison(ModelVersion version, Feature feature,
            int origin, Prediction candidate) {
        LifecycleSettings lifecycle = SimulationSettings.get().getLifecycleSettings();
        String baselineName = lifecycle.getBaselinePredictorName();

        try {
            if (baselineName == null || baselineName.isBlank()) {
                throw new IllegalStateException("No baseline predictor is configured.");
            }
            PairPredictionSettings baselineSettings =
                    PairPredictionSettings.getPredictionSettingsByName(baselineName);
            Prediction baseline =
                    requestPredictionAtOrigin(baselineSettings, feature, origin);
            PairedShadowEvaluation evaluation = new PairedShadowEvaluation(
                    feature.getName(), origin, version.name, candidate,
                    baselineName, baseline);
            pairedComparisons.add(new PendingComparison(feature, evaluation, lifecycle));
            logLifecycle("Paired shadow registered: candidate=" + version.name
                    + "; baseline=" + baselineName + "; feature=" + feature.getName()
                    + "; origin=" + origin + "; targets=" + evaluation.getTargetCount());
        } catch (Exception exception) {
            logLifecycle("Paired shadow unavailable: candidate=" + version.name
                    + "; baseline=" + baselineName + "; feature=" + feature.getName()
                    + "; origin=" + origin + "; reason=" + exception.getMessage());
        }
    }

    /**
     * Consumes newly observed values before normal lifecycle decisions.
     */
    private void resolvePairedComparisons() {
        for (PendingComparison pending : new ArrayList<>(pairedComparisons)) {
            PairedShadowEvaluation evaluation = pending.evaluation;
            try {
                List<Double> values = pending.feature.getValues();
                while (pending.nextActual < values.size() && !evaluation.isComplete()) {
                    Double value = values.get(pending.nextActual);
                    if (value == null) {
                        throw new IllegalArgumentException("Actual value is null.");
                    }
                    evaluation.resolveActualValue(
                            evaluation.getFeatureName(), pending.nextActual, value);
                    pending.nextActual++;
                }

                if (evaluation.isComplete()) {
                    logLifecycle("Paired shadow completed: " + comparisonReport(pending));
                    pairedComparisons.remove(pending);
                }
            } catch (Exception exception) {
                pairedComparisons.remove(pending);
                logLifecycle("Paired shadow invalid: candidate="
                        + evaluation.getCandidateName() + "; feature="
                        + evaluation.getFeatureName() + "; origin="
                        + evaluation.getOrigin() + "; reason=" + exception.getMessage());
            }
        }
    }

    private JSONObject comparisonReport(PendingComparison pending) throws org.json.JSONException{
        PairedShadowEvaluation evaluation = pending.evaluation;
        Prediction.ModelStatus status = evaluation.getBaselineStatus();

        return new JSONObject()
                .put("feature", evaluation.getFeatureName())
                .put("origin", evaluation.getOrigin())
                .put("candidate", evaluation.getCandidateName())
                .put("baseline", evaluation.getBaselineName())
                .put("matched", evaluation.getMatchedSamples())
                .put("targets", evaluation.getTargetCount())
                .put("relative_tolerance", pending.relativeTolerance)
                .put("absolute_tolerance", pending.absoluteTolerance)
                .put("baseline_status", status == null || status.getStatus() == null
                        ? JSONObject.NULL : status.getStatus())
                .put("baseline_converged", status == null || status.getConverged() == null
                        ? JSONObject.NULL : status.getConverged())
                .put("baseline_finite", status == null || status.getFinite() == null
                        ? JSONObject.NULL : status.getFinite())
                .put("candidate_metrics", metricsJson(evaluation.getCandidateMetrics(
                        pending.relativeTolerance, pending.absoluteTolerance)))
                .put("baseline_metrics", metricsJson(evaluation.getBaselineMetrics(
                        pending.relativeTolerance, pending.absoluteTolerance)));
    }

    private JSONObject metricsJson(EvaluationMetrics metrics) throws org.json.JSONException{
        return new JSONObject()
                .put("MAE", metrics.getMae())
                .put("MSE", metrics.getMse())
                .put("RMSE", metrics.getRmse())
                .put("SMAPE", metrics.getSmape())
                .put("accuracy", metrics.getAccuracy());
    }

    private int registerFuturePrediction(String name, Feature feature,
            Prediction prediction) {
        if (prediction == null) {
            return 0;
        }
        Prediction.Data original = prediction.getOriginalData();
        Prediction.Data future = prediction.getPredictionFuture();
        if (original == null || original.getTimestamp() == null
                || future == null || future.getTimestamp() == null
                || future.getData() == null) {
            return 0;
        }
        int start = prediction.getWindowStartTimestamp();
        if (start < 0) {
            start = feature.getValues().size() - original.getTimestamp().size();
        }
        int count = Math.min(future.getTimestamp().size(), future.getData().size());
        int registered = 0;
        for (int i = 0; i < count; i++) {
            Object value = ((List<?>) future.getData()).get(i);
            Integer offset = future.getTimestamp().get(i);
            if (!(value instanceof Number) || offset == null) {
                continue;
            }
            double predicted = ((Number) value).doubleValue();
            long target = (long) start + offset;
            if (!Double.isFinite(predicted)
                    || target < feature.getValues().size()
                    || target > Integer.MAX_VALUE) {
                continue;
            }
            LifecycleManager.getInstance().registerPrediction(new ShadowPrediction(
                    name, feature.getName(), prediction.getPredictionNumber(),
                    (int) target, predicted));
            registered++;
        }
        return registered;
    }

    private void openPredictor(ModelVersion version) throws Exception {
        File directory = ProjectPathResolver.getPredictorScriptsDirectory().toFile();
        String arguments = new JSONObject().put("predictor-settings",
                JsonParser.toJson(version.settings, PairPredictionSettings.class)).toString();
        if (SystemUtils.IS_OS_WINDOWS) {
            arguments = arguments.replace("\"", "\\\"");
        }
        ProcessBuilder builder = new ProcessBuilder(resolvePythonExecutable(directory),
                new File(directory, "main.py").getAbsolutePath(), arguments);
        builder.directory(directory);
        builder.redirectError(ProcessBuilder.Redirect.appendTo(
                new File(ScenarioBase.resultDirectory, "prediction_error_logs.txt")));
        PredictionConfigurator.addPredictorProcess(version.name, builder.start());
    }

    /**
     * SQLite retains per-feature routing when multiple versions are serving.
     */
    private void syncRegistry() throws Exception {
        ModelVersion serving = null;
        int count = 0;
        for (ModelVersion version : versions.values()) {
            if (isServing(version)) {
                serving = version;
                count++;
            }
        }
        String desired = count == 1 ? serving.result.getString("model_version") : "";
        if (desired.equals(registryVersion)) {
            return;
        }

        File directory = ProjectPathResolver.getPredictorScriptsDirectory().toFile();
        List<String> command = new ArrayList<>();
        command.add(resolvePythonExecutable(directory));
        command.add(new File(directory, "model_registry.py").getAbsolutePath());
        if (count == 1) {
            command.add("activate");
            command.add(serving.result.getString("feature_name"));
            command.add(serving.result.getString("model_type"));
            command.add(desired);
        } else {
            command.add("deactivate");
            command.add(modelAdapter.getRegistryFeatureName());
            command.add(modelAdapter.getModelType());
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(directory);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(
                new File(ScenarioBase.resultDirectory, "model_registry.log")));
        Process process = builder.start();
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw exception;
        }
        if (exitCode != 0) {
            throw new IllegalStateException("Model registry exit code=" + exitCode);
        }
        registryVersion = desired;
    }

    private String resolvePythonExecutable(File directory) {
        String[] candidates = SystemUtils.IS_OS_WINDOWS
                ? new String[]{"venv/Scripts/python.exe", "venv/python.exe"}
                : new String[]{"venv/bin/python"};
        for (String candidate : candidates) {
            File executable = new File(directory, candidate);
            if (executable.isFile()) {
                return executable.getAbsolutePath();
            }
        }
        throw new IllegalStateException(
                "Python executable was not found in " + directory.getAbsolutePath());
    }

    private void removeFromPredictionSettings(String name) {
        SimulationSettings.get().getPredictionSettings()
                .removeIf(settings -> name.equals(settings.getPredictorName()));
    }

    private void syncFeature(String name) {
        if (PredictionConfigurator.sqLiteManager != null) {
            PredictionConfigurator.sqLiteManager.syncLifecycleData(name);
        }
    }

    private void logLifecycle(String message) {
        String entry = "time=" + Timed.getFireCount() + " phase=" + phase + " " + message;
        String category = modelAdapter.getModelType() + " lifecycle";
        PredictionLogger.info(category, entry);
        SimLogger.logRun(category + " " + entry);
    }

    public void finish() throws Exception {
        resolvePairedComparisons();
        for (PendingComparison pending : pairedComparisons) {
            PairedShadowEvaluation evaluation = pending.evaluation;
            logLifecycle("Paired shadow incomplete: candidate="
                    + evaluation.getCandidateName() + "; feature="
                    + evaluation.getFeatureName() + "; origin="
                    + evaluation.getOrigin() + "; matched="
                    + evaluation.getMatchedSamples() + "; targets="
                    + evaluation.getTargetCount());
        }
        pairedComparisons.clear();
        for (String name : new ArrayList<>(versions.keySet())) {
            closePredictor(name);
        }
    }

    private void closePredictor(String name) {
        BufferedWriter writer = PredictionConfigurator.predictor_writer.remove(name);
        BufferedReader reader = PredictionConfigurator.predictor_reader.remove(name);
        Process process = PredictionConfigurator.predictor.remove(name);
        try {
            if (process != null) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (Exception exception) {
                PredictionLogger.error(
                        modelAdapter.getModelType() + " lifecycle", exception.getMessage());
            }
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception exception) {
                PredictionLogger.error(
                        modelAdapter.getModelType() + " lifecycle", exception.getMessage());
            }
        }
    }
}