package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleCoordinator;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.ShadowPrediction;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.LifecycleSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Manages features, predictions and dataset exports.
 */
public class FeatureManager {

    private static FeatureManager featureManager;

    @Getter
    private List<Feature> features;

    private FeatureManager() {
        this.features = new ArrayList<>();
    }

    public static FeatureManager getInstance() {
        if (featureManager == null) {
            featureManager = new FeatureManager();
        }
        return featureManager;
    }

    public FeatureManager addFeature(Feature feature) {
        if (getFeatureByName(feature.getName()) == null) {
            features.add(feature);
            PredictionConfigurator.sqLiteManager.createTable(feature.getName());
            initializeLifecycleCoordinators(feature);
        }
        return this;
    }

    private void initializeLifecycleCoordinators(Feature feature) {
        SimulationSettings simulationSettings = SimulationSettings.get();
        if (simulationSettings == null) {
            return;
        }

        LifecycleSettings lifecycleSettings = simulationSettings.getLifecycleSettings();
        if (lifecycleSettings == null || !lifecycleSettings.isEnabled()
                || simulationSettings.getPredictionSettings() == null) {
            return;
        }

        LifecycleManager lifecycleManager = LifecycleManager.getInstance();
        String baselinePredictorName = lifecycleSettings.getBaselinePredictorName();
        boolean baselineConfigured = baselinePredictorName != null
                && !baselinePredictorName.isBlank();
        boolean baselineFound = false;

        for (PairPredictionSettings settings : simulationSettings.getPredictionSettings()) {
            String predictorName = settings.getPredictorName();
            if (lifecycleManager.getCoordinator(predictorName, feature.getName()) != null) {
                continue;
            }

            LifecycleCoordinator coordinator = new LifecycleCoordinator(
                    predictorName, feature.getName(), lifecycleSettings.toPolicy());
            lifecycleManager.registerCoordinator(coordinator);

            if (lifecycleSettings.isBaselinePredictor(predictorName)) {
                lifecycleManager.activateBaseline(predictorName, feature.getName());
                baselineFound = true;
            } else {
                coordinator.startTraining();
                coordinator.startEvaluation();
            }
        }

        if (baselineConfigured && !baselineFound) {
            throw new IllegalArgumentException(
                    "Configured baseline predictor " + baselinePredictorName
                            + " is not present in prediction settings.");
        }
    }

    public Feature getFeatureByName(String name) {
        for (Feature feature : features) {
            if (feature.getName().equals(name)) {
                return feature;
            }
        }
        return null;
    }

    /**
     * Exports a fixed snapshot of the newest points of each selected feature.
     * Each column is an independent training series, as expected by the trainer.
     * Full feature histories remain unchanged so global timestamps stay valid.
     */
    public void exportTrainingWindowToCsv(String directory, String fileName,
            List<Feature> trainingFeatures, int windowPoints) throws IOException {
        if (trainingFeatures == null || trainingFeatures.isEmpty() || windowPoints < 1) {
            throw new IllegalArgumentException(
                    "Training requires selected features and a positive window size.");
        }

        List<String> names = new ArrayList<>();
        List<List<Double>> snapshots = new ArrayList<>();
        List<String> ranges = new ArrayList<>();

        // Copy and validate the complete snapshot before opening the output file.
        for (Feature feature : trainingFeatures) {
            if (feature == null) {
                throw new IllegalArgumentException("Training feature must not be null.");
            }

            String name = feature.getName();
            if (name == null || name.isBlank() || names.contains(name)
                    || name.contains(";") || name.contains("\"")
                    || name.contains("\n") || name.contains("\r")) {
                throw new IllegalArgumentException(
                        "Training feature names must be unique and CSV-compatible.");
            }

            int endExclusive = feature.getValues().size();
            if (endExclusive < windowPoints) {
                throw new IllegalArgumentException(
                        "Not enough training data for " + name + ": "
                                + endExclusive + " < " + windowPoints);
            }

            int startInclusive = endExclusive - windowPoints;
            List<Double> snapshot = new ArrayList<>(
                    feature.getValues().subList(startInclusive, endExclusive));

            for (Double value : snapshot) {
                if (value == null || !Double.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "Non-finite training value in feature " + name);
                }
            }

            names.add(name);
            snapshots.add(snapshot);
            ranges.add(name + "=[" + startInclusive + "," + endExclusive + ")");
        }

        Path outputDirectory = Paths.get(directory);
        Files.createDirectories(outputDirectory);
        Path outputFile = outputDirectory.resolve(fileName);

        try (BufferedWriter writer = Files.newBufferedWriter(
                outputFile, StandardCharsets.UTF_8)) {
            for (String name : names) {
                writer.write(name);
                writer.write(';');
            }
            writer.newLine();

            for (int row = 0; row < windowPoints; row++) {
                for (List<Double> snapshot : snapshots) {
                    writer.write(Double.toString(snapshot.get(row)));
                    writer.write(';');
                }
                writer.newLine();
            }
        }

        PredictionLogger.info("Training snapshot",
                "file=" + outputFile.toAbsolutePath()
                        + " pointsPerFeature=" + windowPoints
                        + " ranges=" + String.join(", ", ranges));
    }

    public void exportDatasetToCsv(String path, String fileName) throws IOException {
        Files.createDirectories(Paths.get(path));
        String fullPath = path + fileName;
        PredictionLogger.info("FeatureManager-exportDatasetToCSV",
                String.format("Exporting dataset to %s...", fullPath));

        try (PrintWriter writer = new PrintWriter(fullPath)) {
            StringBuilder sb = new StringBuilder();
            for (Feature feature : features) {
                sb.append(feature.getName()).append(";");
            }
            sb.append("\n");

            for (int i = 0; i < getFeatureValuesMaxLength(); i++) {
                for (Feature feature : features) {
                    if (i < feature.getValues().size()) {
                        sb.append(String.format("%.16f", feature.getValues().get(i))
                                .replace("\\.", ",")).append(";");
                    } else {
                        sb.append(";");
                    }
                }
                sb.append("\n");
            }

            writer.write(sb.toString());
            PredictionLogger.info("FeatureManager-exportDatasetToCSV", "Export done!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void exportErrorMetricsToCsv(String path, String fileName) throws IOException {
        Files.createDirectories(Paths.get(path));
        String fullPath = path + fileName;
        PredictionLogger.info("FeatureManager-exportMetricsToCSV",
                String.format("Exporting metrics to %s...", fullPath));

        try (PrintWriter writer = new PrintWriter(fullPath)) {
            StringBuilder sb = new StringBuilder();
            for (Feature feature : features) {
                sb.append(feature.getName()).append("::RMSE;");
                sb.append(feature.getName()).append("::MSE;");
                sb.append(feature.getName()).append("::MAE;");
            }
            sb.append("\n");

            for (int i = 0; i < getFeaturePredictionsMaxLength(); i++) {
                for (Feature feature : features) {
                    if (i < feature.getPredictions().size()
                            && feature.getPredictions().get(i).getErrorMetrics() != null) {
                        sb.append(String.format("%.16f", feature.getPredictions()
                                .get(i).getErrorMetrics().getRmse())
                                .replace("\\.", ",")).append(";");
                        sb.append(String.format("%.16f", feature.getPredictions()
                                .get(i).getErrorMetrics().getMse())
                                .replace("\\.", ",")).append(";");
                        sb.append(String.format("%.16f", feature.getPredictions()
                                .get(i).getErrorMetrics().getMae())
                                .replace("\\.", ",")).append(";");
                    } else {
                        sb.append(";;;");
                    }
                }
                sb.append("\n");
            }

            writer.write(sb.toString());
            PredictionLogger.info("FeatureManager-exportErrorMetricsToCSV", "Export done!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void exportPredictionsToCsv(String path, String fileName) throws IOException {
        Files.createDirectories(Paths.get(path));
        String fullPath = path + fileName;
        PredictionLogger.info("FeatureManager-exportPredictionsToCSV",
                String.format("Exporting predictions to %s...", fullPath));

        try (PrintWriter writer = new PrintWriter(fullPath)) {
            StringBuilder sb = new StringBuilder();
            for (Feature feature : features) {
                sb.append(feature.getName()).append(";");
            }
            sb.append("\n");

            for (int i = 0; i < getFeaturePredictionsMaxLength(); i++) {
                for (Feature feature : features) {
                    if (i < feature.getPredictions().size()) {
                        sb.append(String.format("%.16f",
                                feature.getPredictions().get(i).getPredictionTime())
                                .replace("\\.", ",")).append(";");
                    } else {
                        sb.append(";");
                    }
                }
                sb.append("\n");
            }

            writer.write(sb.toString());
            PredictionLogger.info("FeatureManager-exportPredictionsToCSV", "Export done!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Prediction> predict(List<Feature> features, String predictorName) throws Exception {
        PredictionLogger.info("FeatureManager-sendFeatures", "Send features for prediction");
        List<Prediction> predictions = new ArrayList<>();

        for (Feature feature : features) {
            int batchSize = PairPredictionSettings
                    .getPredictionSettingsByName(predictorName)
                    .getPredictionSettings().getBatchSize();

            int historySize = feature.getValues().size();
            if (batchSize <= 0 || historySize < batchSize) {
                throw new IllegalArgumentException(
                        "Invalid prediction window for " + feature.getName()
                                + ": history=" + historySize + ", batch=" + batchSize);
            }

            int windowStartTimestamp = historySize - batchSize;
            List<Double> windowValues = new ArrayList<>(
                    feature.getValues().subList(windowStartTimestamp, historySize));

            String payload = new JSONObject().put("feature", new JSONObject()
                    .put("name", feature.getName())
                    .put("values", new JSONArray(windowValues))).toString();

            PredictionConfigurator.predictor_writer.get(predictorName).write(payload);
            PredictionConfigurator.predictor_writer.get(predictorName).newLine();
            PredictionConfigurator.predictor_writer.get(predictorName).flush();

            String predictionString =
                    PredictionConfigurator.predictor_reader.get(predictorName).readLine();
            PredictionLogger.info("Predictor-message", predictionString);

            Prediction result = JsonParser.fromJsonObject(
                    new JSONObject(predictionString).getJSONObject("prediction"),
                    Prediction.class, null);

            result.setWindowStartTimestamp(windowStartTimestamp);

            for (Prediction existingPrediction : feature.getPredictions()) {
                result.setPredictionNumber(Math.max(
                        result.getPredictionNumber(),
                        existingPrediction.getPredictionNumber()));
            }

            SimulationSettings.get().getPredictionSettings().stream().filter(
                    settings -> settings.getPredictorName()
                            .equals(result.getPredictionSettings().getPredictorName())
            ).findFirst().orElseThrow()
                    .recordPredictionMetrics(result.getErrorMetrics());

            feature.addPrediction(result);
            predictions.add(result);
            registerShadowPredictions(predictorName, feature, result);
            PredictionConfigurator.sqLiteManager.addPredictionDataToTable(
                    feature.getName(), result);
        }
        return predictions;
    }

    private void registerShadowPredictions(String predictorName,
            Feature feature, Prediction prediction) {
        Prediction.Data originalData = prediction.getOriginalData();
        Prediction.Data predictionFuture = prediction.getPredictionFuture();

        if (originalData == null || originalData.getTimestamp() == null
                || predictionFuture == null || predictionFuture.getTimestamp() == null
                || predictionFuture.getData() == null) {
            return;
        }

        int windowStartTimestamp =
                feature.getValues().size() - originalData.getTimestamp().size();
        int pointCount = Math.min(
                predictionFuture.getTimestamp().size(),
                predictionFuture.getData().size());

        for (int i = 0; i < pointCount; i++) {
            int targetTimestamp =
                    windowStartTimestamp + predictionFuture.getTimestamp().get(i);
            Object value = ((List<?>) predictionFuture.getData()).get(i);
            if (!(value instanceof Number)) {
                continue;
            }

            ShadowPrediction shadowPrediction = new ShadowPrediction(
                    predictorName, feature.getName(), prediction.getPredictionNumber(),
                    targetTimestamp, ((Number) value).doubleValue());
            LifecycleManager.getInstance()
                    .registerPredictionIfEvaluating(shadowPrediction);
        }
    }

    private int getFeatureValuesMaxLength() {
        int max = Integer.MIN_VALUE;
        for (Feature feature : features) {
            if (feature.getValues().size() > max) {
                max = feature.getValues().size();
            }
        }
        return max;
    }

    private int getFeaturePredictionsMaxLength() {
        int max = Integer.MIN_VALUE;
        for (Feature feature : features) {
            if (feature.getPredictions().size() > max) {
                max = feature.getPredictions().size();
            }
        }
        return max;
    }

    public List<Feature> getFeaturesWithEnoughData(int windowSize) {
        List<Feature> result = new ArrayList<>();
        for (Feature feature : features) {
            if (feature.getValues().isEmpty()) {
                continue;
            }
            if (feature.getValues().size() >= windowSize && feature.getHasNewValue()) {
                result.add(feature);
            }
        }
        return result;
    }

    public void printInfo() throws Exception {
        TableBuilder table = new TableBuilder();
        table.addHeader("Feature name", "Dataset length", "Number of predictions");
        for (Feature feature : features) {
            table.addRow(feature.getName(), feature.getValues().size(),
                    feature.getPredictions().size());
        }
        System.out.println(table);
    }

    public int getTotalNumOfPredictions() {
        int total = 0;
        for (Feature feature : features) {
            total += feature.getPredictions().size();
        }
        return total;
    }

    public List<String> getFeatureNames(String separator) {
        List<String> result = new ArrayList<>();
        for (Feature feature : features) {
            String onlyName = feature.getName().split(separator)[1];
            if (!result.contains(onlyName)) {
                result.add(onlyName);
            }
        }
        return result;
    }
}