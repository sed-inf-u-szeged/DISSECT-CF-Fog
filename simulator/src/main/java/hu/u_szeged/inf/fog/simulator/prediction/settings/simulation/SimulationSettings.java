package hu.u_szeged.inf.fog.simulator.prediction.settings.simulation;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.TableBuilder;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SimulationSettings {
    private static SimulationSettings SIMULATION_SETTINGS;
    private ExportSettings export;
    private PredictionSettings prediction;
    private PredictorSettings predictor;

    public SimulationSettings(ExportSettings export, PredictionSettings prediction, PredictorSettings predictor) {
        this.export = export;
        this.prediction = prediction;
        this.predictor = predictor;
    }

    public SimulationSettings(JSONObject jsonObject) throws JSONException {
        fromJSONObject(jsonObject);
    }

    private void fromJSONObject(JSONObject jsonObject) throws JSONException {
        this.export = new ExportSettings(jsonObject.getJSONObject("export"));
        this.prediction = new PredictionSettings(jsonObject.getJSONObject("prediction"));
        this.predictor = new PredictorSettings(jsonObject.getJSONObject("predictor"));
    }

    public ExportSettings getExport() {
        return export;
    }

    public void setExport(ExportSettings export) {
        this.export = export;
    }

    public PredictionSettings getPrediction() {
        return prediction;
    }

    public void setPrediction(PredictionSettings prediction) {
        this.prediction = prediction;
    }

    public PredictorSettings getPredictor() {
        return predictor;
    }

    public void setPredictor(PredictorSettings predictor) {
        this.predictor = predictor;
    }

    public JSONObject toJSON() throws JSONException {
        return new JSONObject()
                .put("export", export.toJSON())
                .put("prediction", prediction.toJSON())
                .put("predictor", predictor.toJSON());
    }

    public static SimulationSettings get() {
        return SimulationSettings.SIMULATION_SETTINGS;
    }

    public static void set(SimulationSettings simulationSettings) {
        SimulationSettings.SIMULATION_SETTINGS = simulationSettings;
    }

    public void exportToJSON(String path, String fileName) throws IOException {
        Files.createDirectories(Paths.get(path));
        String fullPath = path + fileName;
        PredictionLogger.info("SimulationSettings-exportToJSON", String.format("Exporting simulation settings to %s...", fullPath));
        try (FileWriter fileWriter = new FileWriter(fullPath)) {
            fileWriter.write(toJSON().toString());
            PredictionLogger.info("SimulationSettings-exportToJSON", "Export done!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void printInfo() throws Exception {
        TableBuilder table = new TableBuilder();
        table.addHeader("Name", "Value");
        table.addRow("Predictor model", predictor.getPredictor());
        table.addRow("Batch size", prediction.getBatchSize());
        table.addRow("Test size", prediction.getTestSize());
        table.addRow("Prediction size", prediction.getLength());
        table.addRow("Smoothing", prediction.getSmoothing().getWindowSize() + ", " + prediction.getSmoothing().getPolynomialDegree());
        System.out.println(table);
    }
}
