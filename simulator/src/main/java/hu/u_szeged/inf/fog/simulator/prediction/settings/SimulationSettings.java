package hu.u_szeged.inf.fog.simulator.prediction.settings;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.TableBuilder;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SimulationSettings {
    private static SimulationSettings SIMULATION_SETTINGS;
    private ExportSettings export;
    private List<PairPredictionSettings> predictionSettings;
    private TrainSettings trainSettings;
    private LifecycleSettings lifecycleSettings;

    public SimulationSettings(ExportSettings export,
            List<PairPredictionSettings> predictionSettings,
            TrainSettings trainSettings) {
        this(export, predictionSettings, trainSettings, null);
    }

    public static SimulationSettings get() {
        return SimulationSettings.SIMULATION_SETTINGS;
    }

    public static void set(SimulationSettings simulationSettings) {
        SimulationSettings.SIMULATION_SETTINGS = simulationSettings;
    }

    public void exportToJson(String path, String fileName) throws IOException {
        Files.createDirectories(Paths.get(path));
        String fullPath = path + fileName;
        PredictionLogger.info("SimulationSettings-exportToJSON",
                String.format("Exporting simulation settings to %s...", fullPath));
        try (FileWriter fileWriter = new FileWriter(fullPath)) {
            fileWriter.write(JsonParser.toJson(
                    this, SimulationSettings.class).toString());
            PredictionLogger.info("SimulationSettings-exportToJSON", "Export done!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void printInfo() throws Exception {
        for (var setting : predictionSettings) {
            TableBuilder table = new TableBuilder();
            table.addHeader("Name", "Value");
            table.addRow("Predictor name", setting.getPredictorName());
            table.addRow("Predictor model",
                    setting.getPredictorSettings().getPredictor());
            table.addRow("Batch size",
                    setting.getPredictionSettings().getBatchSize());
            table.addRow("Test size",
                    setting.getPredictionSettings().getTestSize());
            table.addRow("Prediction size",
                    setting.getPredictionSettings().getLength());

            var smoothing = setting.getPredictionSettings().getSmoothing();
            table.addRow("Smoothing", smoothing == null ? "Disabled"
                    : smoothing.getWindowSize() + ", "
                    + smoothing.getPolynomialDegree());

            table.addRow("Selection metric", "MAE (lower is better)");
            table.addRow("Times selected by MAE", setting.getBestPredictor());
            table.addRow("Metric source", "Historical test, raw units");
            table.addRow("Aggregation", "Equal weight per valid prediction request");
            table.addRow("Average MAE", displayMetric(setting.getAverageMae()));
            table.addRow("Average MSE", displayMetric(setting.getAverageMse()));
            table.addRow("Average RMSE", displayMetric(setting.getAverageRmse()));
            table.addRow("Valid metric requests", setting.getValidMetricCount());
            table.addRow("Invalid metric requests", setting.getInvalidMetricCount());
            table.addRow("Number of predictions", setting.getNumberOfPredictions());
            System.out.println(table);
        }
    }

    private static String displayMetric(Double value) {
        return value == null ? "N/A" : Double.toString(value);
    }
}