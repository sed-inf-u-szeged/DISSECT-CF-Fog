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
            fileWriter.write(JsonParser.toJson(this, SimulationSettings.class).toString());
            PredictionLogger.info("SimulationSettings-exportToJSON", "Export done!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void printInfo() throws Exception {
        for (var predictionSetting : predictionSettings) {
            TableBuilder table = new TableBuilder();
            table.addHeader("Name", "Value");
            table.addRow("Batch size", predictionSetting.getPredictionSettings().getBatchSize());
            table.addRow("Predictor model", predictionSetting.getPredictorSettings().getPredictor());
            table.addRow("Test size", predictionSetting.getPredictionSettings().getTestSize());
            table.addRow("Prediction size", predictionSetting.getPredictionSettings().getLength());
            table.addRow("Smoothing", predictionSetting.getPredictionSettings().getSmoothing().getWindowSize()
                    + ", " + predictionSetting.getPredictionSettings().getSmoothing().getPolynomialDegree());
            table.addRow("Best prediction", predictionSetting.getBestPredictor());
            table.addRow("Average sum of error metrics", 
                predictionSetting.getSumOfErrorMetrics() / predictionSetting.getNumberOfPredictions());
            table.addRow("Number of predictions", predictionSetting.getNumberOfPredictions());
            System.out.println(table);
        }
    }
}
