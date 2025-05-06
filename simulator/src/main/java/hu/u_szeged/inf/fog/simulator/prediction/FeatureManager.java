package hu.u_szeged.inf.fog.simulator.prediction;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import lombok.Getter;
import org.json.JSONObject;

/**
 * Manages a collection of features for time series analysis.
 * Provides methods to add and retrieve features, and their export data.
 */
public class FeatureManager {
    
    private static FeatureManager featureManager;
    @Getter
    private List<Feature> features;

    /**
     * Private constructor to enforce singleton pattern.
     */
    private FeatureManager() {
        this.features = new ArrayList<>();
    }
    
    /**
     * Returns the singleton instance of the FeatureManager.
     *
     * @return the singleton instance
     */
    public static FeatureManager getInstance() {
        if (featureManager == null) {
            featureManager = new FeatureManager();
        }
        return featureManager;
    }

    /**
     * Adds a feature to the list if it does not already exist.
     *
     * @param feature the feature to be added
     */
    public FeatureManager addFeature(Feature feature) {
        if (getFeatureByName(feature.getName()) == null) {
            features.add(feature);
        }
        return this;
    }

    /**
     * Retrieves a feature by its name.
     *
     * @param name the name of the feature
     * @return the feature with the specified name, or null if it does not exist
     */
    public Feature getFeatureByName(String name) {
        for (Feature feature : features) {
            if (feature.getName().equals(name)) {
                return feature;
            }
        }
        return null;
    }

    /**
     * Exports the dataset to a CSV file.
     *
     * @param path the directory path to save the CSV file
     * @param fileName the name of the CSV file
     */
    public void exportDatasetToCsv(String path, String fileName) throws IOException {
        Files.createDirectories(Paths.get(path));
        String fullPath = path + fileName;
        PredictionLogger.info("FeatureManager-exportDatasetToCSV", 
                String.format("Exporting dataset to %s...", fullPath));
        try (PrintWriter printWriter = new PrintWriter(fullPath)) {
            StringBuilder sb = new StringBuilder();

            // Columns
            for (Feature feature : features) {
                sb.append(feature.getName()).append(";");
            }

            sb.append("\n");

            // Values
            for (int i = 0; i < getFeatureValuesMaxLength(); i++) {
                for (Feature feature : features) {
                    if (i < feature.getValues().size()) {
                        sb.append(String.format("%.16f", feature.getValues().get(i)).replace("\\.", ",")).append(";");
                    } else {
                        sb.append("").append(";");
                    }
                }
                sb.append("\n");
            }

            printWriter.write(sb.toString());
            PredictionLogger.info("FeatureManager-exportDatasetToCSV", "Export done!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Exports the metrics to a CSV file.
     *
     * @param path the directory path to save the CSV file
     * @param fileName the name of the CSV file
     */
    public void exportErrorMetricsToCsv(String path, String fileName) throws IOException {
        Files.createDirectories(Paths.get(path));
        String fullPath = path + fileName;
        PredictionLogger.info("FeatureManager-exportMetricsToCSV", 
                String.format("Exporting metrics to %s...", fullPath));
        try (PrintWriter printWriter = new PrintWriter(fullPath)) {
            StringBuilder sb = new StringBuilder();

            // Columns
            for (Feature feature : features) {
                sb.append(String.format("%s::%s", feature.getName(), "RMSE")).append(";");
                sb.append(String.format("%s::%s", feature.getName(), "MSE")).append(";");
                sb.append(String.format("%s::%s", feature.getName(), "MAE")).append(";");
            }

            sb.append("\n");

            // Values
            for (int i = 0; i < getFeaturePredictionsMaxLength(); i++) {
                for (Feature feature : features) {
                    if (i < feature.getPredictions().size() 
                            && feature.getPredictions().get(i).getErrorMetrics() != null) {
                        sb.append(String.format("%.16f", feature.getPredictions()
                                .get(i).getErrorMetrics().getRmse()).replace("\\.", ",")).append(";");
                        sb.append(String.format("%.16f", feature.getPredictions()
                                .get(i).getErrorMetrics().getMse()).replace("\\.", ",")).append(";");
                        sb.append(String.format("%.16f", feature.getPredictions()
                                .get(i).getErrorMetrics().getMae()).replace("\\.", ",")).append(";");
                    } else {
                        sb.append("").append(";")
                                .append("").append(";")
                                .append("").append(";");
                    }
                }
                sb.append("\n");
            }

            printWriter.write(sb.toString());
            PredictionLogger.info("FeatureManager-exportErrorMetricsToCSV", "Export done!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Exports the predictions to a CSV file.
     *
     * @param path the directory path to save the CSV file
     * @param fileName the name of the CSV file
     */
    public void exportPredictionsToCsv(String path, String fileName) throws IOException {
        Files.createDirectories(Paths.get(path));
        String fullPath = path + fileName;
        PredictionLogger.info("FeatureManager-exportPredictionsToCSV", 
                String.format("Exporting predictions to %s...", fullPath));
        try (PrintWriter printWriter = new PrintWriter(fullPath)) {
            StringBuilder sb = new StringBuilder();

            // Columns
            for (Feature feature : features) {
                sb.append(feature.getName()).append(";");
            }

            sb.append("\n");

            for (int i = 0; i < getFeaturePredictionsMaxLength(); i++) {
                for (Feature feature : features) {
                    if (i < feature.getPredictions().size()) {
                        sb.append(String.format("%.16f", 
                                feature.getPredictions().get(i).getPredictionTime()).replace("\\.", ",")).append(";");
                    } else {
                        sb.append("").append(";");
                    }
                }
                sb.append("\n");
            }

            printWriter.write(sb.toString());
            PredictionLogger.info("FeatureManager-exportPredictionsToCSV", "Export done!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends features for prediction.
     *
     * @param features the list of features to predict
     * @param windowSize the size of the window for prediction
     * @return the list of predictions
     */
    public List<Prediction> predict(List<Feature> features) throws Exception {
        PredictionLogger.info("FeatureManager-sendFeatures", "Send features for prediction");
        List<Prediction> predictions = new ArrayList<>();
        for (Feature feature : features) {
            feature.setHasNewValue(false);

            Prediction result;

            String payload = new JSONObject().put(
                    "feature",
                    JsonParser.toJson(feature, Feature.class)
            ).toString();

            PredictionConfigurator.predictor_writer.write(payload);
            PredictionConfigurator.predictor_writer.newLine();
            PredictionConfigurator.predictor_writer.flush();

            PredictionLogger.info("Predictor-message", PredictionConfigurator.predictor_reader.readLine());
            String predictionString = PredictionConfigurator.predictor_reader.readLine();
            PredictionLogger.info("FeatureManager-predictionRecived", predictionString);

            result = JsonParser.fromJsonObject(new JSONObject(predictionString).getJSONObject("prediction"), Prediction.class, null);

            feature.addPrediction(result);
            predictions.add(result);
        }

//TODO: refactor Electron to read from db file. (Could make db file WAL)

//        if (Launcher.hasApplication(ElectronLauncher.class.getSimpleName())) {
//            PredictionLogger.info("FeatureManager-sendFeatures", "Send features to UI");
//            for (Prediction prediction : predictions) {
//                ServerSocket.getInstance().sendAndGet(
//                        SocketMessage.SocketApplication.APPLICATION_INTERFACE,
//                        new SocketMessage(
//                                "prediction",
//                                new JSONObject().put("prediction", JsonParser.toJson(prediction, Prediction.class))
//                        )
//                );
//            }
//        }
        return predictions;
    }

    /**
     * Returns the maximum length of feature values.
     */
    private int getFeatureValuesMaxLength() {
        int max = Integer.MIN_VALUE;
        for (Feature feature : features) {
            if (feature.getValues().size() > max) {
                max = feature.getValues().size();
            }
        }
        return max;
    }

    /**
     * Returns the maximum length of feature predictions.
     */
    private int getFeaturePredictionsMaxLength() {
        int max = Integer.MIN_VALUE;
        for (Feature feature : features) {
            if (feature.getPredictions().size() > max) {
                max = feature.getPredictions().size();
            }
        }
        return max;
    }

    /**
     * Retrieves features with enough data based on the specified window size.
     *
     * @param windowSize the size of the window for data
     * @return the list of features with enough data
     */
    public List<Feature> getFeaturesWithEnoughData(int windowSize) {
        List<Feature> result = new ArrayList<>();

        for (Feature feature : features) {
            if (feature.getValues().size() >= windowSize && feature.getHasNewValue()) {
                result.add(feature);
            }
        }
        return result;
    }

    /**
     * Prints information about the features.
     */
    public void printInfo() throws Exception {
        TableBuilder table = new TableBuilder();
        table.addHeader("Feature name", "Dataset length", "Number of predictions");
        for (Feature feature : features) {
            table.addRow(feature.getName(), feature.getValues().size(), feature.getPredictions().size());
        }
        System.out.println(table);
    }

    /**
     * Returns the total number of predictions of all features.
     */
    public int getTotalNumOfPredictions() {
        int temp = 0;
        for (Feature feature : features) {
            temp += feature.getPredictions().size();
        }
        return temp;
    }

    /**
     * Retrieves the names of features.
     *
     * @param separator the separator to split the feature names
     */
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