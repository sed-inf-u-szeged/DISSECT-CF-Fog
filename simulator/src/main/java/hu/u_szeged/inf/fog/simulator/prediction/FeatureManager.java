package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.communication.ServerSocket;
import hu.u_szeged.inf.fog.simulator.prediction.communication.SocketMessage;
import hu.u_szeged.inf.fog.simulator.prediction.communication.applications.ElectronApplication;
import hu.u_szeged.inf.fog.simulator.prediction.communication.applications.IApplication;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FeatureManager {
    private static FeatureManager featureManager;
    private List<Feature> features;

    private FeatureManager() {
        this.features = new ArrayList<>();
    }

    public FeatureManager addFeature(Feature feature) {
        if (!featureExists(feature)) {
            features.add(feature);
        }
        return this;
    }

    public Feature getFeatureByName(String name) {
        for (Feature feature: features) {
            if (feature.getName().equals(name)) {
                return feature;
            }
        }
        return null;
    }

    public boolean featureExists(Feature feature) {
        return getFeatureByName(feature.getName()) != null;
    }

    public void exportDatasetToCSV(String path, String fileName) throws IOException {
        Files.createDirectories(Paths.get(path));
        String fullPath = path + fileName;
        PredictionLogger.info("FeatureManager-exportDatasetToCSV", String.format("Exporting dataset to %s...", fullPath));
        try (PrintWriter printWriter = new PrintWriter(fullPath)) {
            StringBuilder sb = new StringBuilder();

            // Columns
            for (Feature feature: features) {
                sb.append(feature.getName()).append(";");
            }

            sb.append("\n");

            // Values
            for (int i = 0; i < getFeatureValuesMaxLength(); i++) {
                for (Feature feature: features) {
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

    public void exportMetricsToCSV(String path, String fileName) throws IOException {
        Files.createDirectories(Paths.get(path));
        String fullPath = path + fileName;
        PredictionLogger.info("FeatureManager-exportMetricsToCSV", String.format("Exporting metrics to %s...", fullPath));
        try (PrintWriter printWriter = new PrintWriter(fullPath)) {
            StringBuilder sb = new StringBuilder();

            // Columns
            for (Feature feature: features) {
                sb.append(String.format("%s::%s", feature.getName(), "RMSE")).append(";");
                sb.append(String.format("%s::%s", feature.getName(), "MSE")).append(";");
                sb.append(String.format("%s::%s", feature.getName(), "MAE")).append(";");
            }

            sb.append("\n");

            // Values
            for (int i = 0; i < getFeaturePredictionsMaxLength(); i++) {
                for (Feature feature: features) {
                    if (i < feature.getPredictions().size() && feature.getPredictions().get(i).getErrorMetrics() != null) {
                        sb.append(String.format("%.16f", feature.getPredictions().get(i).getErrorMetrics().getRMSE()).replace("\\.", ",")).append(";");
                        sb.append(String.format("%.16f", feature.getPredictions().get(i).getErrorMetrics().getMSE()).replace("\\.", ",")).append(";");
                        sb.append(String.format("%.16f", feature.getPredictions().get(i).getErrorMetrics().getMAE()).replace("\\.", ",")).append(";");
                    } else {
                        sb.append("").append(";")
                                .append("").append(";")
                                .append("").append(";");
                    }
                }
                sb.append("\n");
            }

            printWriter.write(sb.toString());
            PredictionLogger.info("FeatureManager-exportMetricsToCSV", "Export done!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static FeatureManager getInstance() {
        if (featureManager == null) {
            featureManager = new FeatureManager();
        }
        return featureManager;
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public List<Prediction> predict(List<Feature> features, int windowSize) throws Exception {
        PredictionLogger.info("FeatureManager-sendFeatures", "Send features for prediction");
        List<Prediction> predictions = new ArrayList<>();
        for (Feature feature: features) {
            feature.setHasNewValue(false);
            SocketMessage message = ServerSocket.getInstance().sendAndGet(
                    SocketMessage.SocketApplication.APPLICATION_PREDICTOR,
                    new SocketMessage(
                            "predict-feature",
                            new JSONObject().put("feature", feature.toJSON(windowSize))
                    )
            );

            if (message.hasError()) {
                PredictionLogger.error("socket-prediction-result", message.getData().get("error").toString());
                continue;
            }

            Prediction result = new Prediction(message.getData().getJSONObject("prediction"));
            feature.addPrediction(result);
            predictions.add(result);
        }

        if (IApplication.hasApplication(ElectronApplication.class.getSimpleName())) {
            PredictionLogger.info("FeatureManager-sendFeatures", "Send features to UI");
            for (Prediction prediction: predictions) {
                ServerSocket.getInstance().sendAndGet(
                        SocketMessage.SocketApplication.APPLICATION_INTERFACE,
                        new SocketMessage(
                                "prediction",
                                new JSONObject().put("prediction", prediction.toJSON())
                        )
                );
            }
        }

        return predictions;
    }

    private int getFeatureValuesMaxLength() {
        int max = Integer.MIN_VALUE;
        for (Feature feature: features) {
            if (feature.getValues().size() > max) {
                max = feature.getValues().size();
            }
        }
        return max;
    }

    private int getFeaturePredictionsMaxLength() {
        int max = Integer.MIN_VALUE;
        for (Feature feature: features) {
            if (feature.getPredictions().size() > max) {
                max = feature.getPredictions().size();
            }
        }
        return max;
    }

    public List<Feature> getFeaturesWithEnoughData(int windowSize) {
        List<Feature> result = new ArrayList<>();

        for (Feature feature: features) {
            if (feature.getValues().size() >= windowSize && feature.getHasNewValue()) {
                result.add(feature);
            }
        }

        return result;
    }

    public void printInfo() throws Exception {
        TableBuilder table = new TableBuilder();
        table.addHeader("Feature name", "Dataset length", "Number of predictions");
        for (Feature feature: features) {
            table.addRow(feature.getName(), feature.getValues().size(), feature.getPredictions().size());
        }
        System.out.println(table);
    }

    public int getTotalNumOfPredictions(){
        int temp = 0;
        for (Feature feature: features) {
            temp += feature.getPredictions().size();
        }
        return temp;
    }
}