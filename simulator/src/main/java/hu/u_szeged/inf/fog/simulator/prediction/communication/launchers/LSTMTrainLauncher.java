package hu.u_szeged.inf.fog.simulator.prediction.communication.launchers;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.TrainSettings;
import hu.u_szeged.inf.fog.simulator.util.ProjectPathResolver;
import java.io.File;
import java.io.IOException;

/**
 * The class provides implementations for starting the Python-based LSTM trainer application.
 */
public class LSTMTrainLauncher extends Launcher {

    public LSTMTrainLauncher() throws IOException {
        if (SimulationSettings.get().getTrainSettings().isCollectAllCsvAndTrainOnIt()) {
            TrainSettings.copyCSVFromDatasetDirToPath(
                    SimulationSettings.get().getTrainSettings().getDatasetsDirectory(),
                    ScenarioBase.resultDirectory + File.separator + "datasets"
            );

            SimulationSettings.get().getTrainSettings().setDatasetsDirectory(
                    ScenarioBase.resultDirectory + File.separator + "datasets"
            );
        }
    }

    @Override
    public String getProjectLocation() {
        return ProjectPathResolver.getPredictorScriptsDirectory().toString();
    }

    @Override
    public Process openWindows() throws Exception {
        return openTrainer(
                resolvePythonExecutable(
                        "venv" + File.separator + "Scripts"
                                + File.separator + "python.exe",
                        "venv" + File.separator + "python.exe"
                ),
                true
        );
    }

    @Override
    public Process openLinux() throws Exception {
        return openTrainer(
                resolvePythonExecutable(
                        "venv" + File.separator + "bin"
                                + File.separator + "python"
                ),
                false
        );
    }

    private Process openTrainer(
            String pythonExecutable,
            boolean windows
    ) throws Exception {
        File projectDirectory = new File(getProjectLocation());
        File trainerScript = new File(
                projectDirectory,
                "predictor_models" + File.separator + "lstm_trainer.py"
        );
        File trainingLog = new File(
                ScenarioBase.resultDirectory,
                "lstm_training.log"
        );

        String trainSettings = JsonParser.toJson(
                SimulationSettings.get().getTrainSettings(),
                TrainSettings.class
        ).toString();

        ProcessBuilder processBuilder;

        if (windows) {
            String relativePythonExecutable = projectDirectory.toPath()
                    .toAbsolutePath()
                    .normalize()
                    .relativize(
                            new File(pythonExecutable).toPath()
                                    .toAbsolutePath()
                                    .normalize()
                    )
                    .toString();

            String relativeTrainerScript = "predictor_models"
                    + File.separator
                    + "lstm_trainer.py";

            String command = String.format(
                    "%s %s \"%s\"",
                    relativePythonExecutable,
                    relativeTrainerScript,
                    trainSettings.replace("\"", "\\\"")
            );

            processBuilder = new ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    command
            );
        } else {
            processBuilder = new ProcessBuilder(
                    pythonExecutable,
                    trainerScript.getAbsolutePath(),
                    trainSettings
            );
        }

        processBuilder.directory(projectDirectory);
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(trainingLog);

        PredictionLogger.info(
                "LSTM trainer",
                "Opening trainer"
        );

        return processBuilder.start();
    }

    private String resolvePythonExecutable(String... relativePaths) {
        String projectLocation = getProjectLocation();

        for (String relativePath : relativePaths) {
            File candidate = new File(
                    projectLocation,
                    relativePath
            );

            if (candidate.isFile()) {
                return candidate.getAbsolutePath();
            }
        }

        throw new IllegalStateException(
                "Python executable was not found. Checked paths: "
                        + String.join(", ", relativePaths)
        );
    }
}