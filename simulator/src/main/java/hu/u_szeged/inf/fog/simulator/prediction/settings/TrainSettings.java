package hu.u_szeged.inf.fog.simulator.prediction.settings;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.FromJsonFieldAliases;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonFieldName;
import hu.u_szeged.inf.fog.simulator.prediction.parser.annotations.ToJsonParseIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class TrainSettings {

    private String modelName;
    private String datasetsDirectory;
    private String modelOutputLocation;

    private Boolean scale;
    @ToJsonFieldName("smoothing")
    @FromJsonFieldAliases(fieldNames = {"smoothing"})
    private PredictionSettings.SmoothingSettings smoothingSettings;
    private int inputSize;
    private int outputSize;
    private String lossFunction;
    private String optimizer;
    private int epochs;

    @ToJsonParseIgnore
    private boolean collectAllCsvAndTrainOnIt;

    public static TrainSettings getDefault() {
        return new TrainSettings(
                "model_i256_o64.h5",
                ScenarioBase.resultDirectory + File.separator  + "..",
                ScenarioBase.resultDirectory,
                true,
                new PredictionSettings.SmoothingSettings(
                        48,
                        5
                ),
                192,
                64,
                "mean_squared_error",
                "Adam",
                3,
                true
        );
    }

    public static void copyCSVFromDatasetDirToPath(String datasetDirPath, String resultPath) throws IOException {
        List<Path> files = Files.walk(
                Paths.get(datasetDirPath)
        ).filter(path -> path.toString().endsWith(".csv")).collect(Collectors.toList());

        Files.createDirectory(Paths.get(resultPath));

        try {
            for (Path path : files) {
                if (path.toString().contains(File.separator + "datasets" + File.separator)) {
                    continue;
                }

                Files.copy(path, Paths.get(resultPath + File.separator + path.getFileName()));
            }
        } catch (IOException e) {
            PredictionLogger.error("LSTM Training", "Failed to copy dataset: " + e.getMessage());
        }

        SimulationSettings.get().getTrainSettings().setDatasetsDirectory(
                ScenarioBase.resultDirectory + File.separator + "datasets"
        );
    }
}
