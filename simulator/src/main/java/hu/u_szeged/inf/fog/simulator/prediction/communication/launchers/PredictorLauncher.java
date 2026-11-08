package hu.u_szeged.inf.fog.simulator.prediction.communication.launchers;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import hu.u_szeged.inf.fog.simulator.util.ProjectPathResolver;
import java.io.File;
import org.json.JSONObject;

/**
 * The class provides implementations for starting the Python-based prediction application.
 */
public class PredictorLauncher extends Launcher {

    public PredictorLauncher() {
        Launcher.predictionApplications.add(this);
    }

    @Override
    public String getProjectLocation() {
        return ProjectPathResolver.getPredictorScriptsDirectory().toString();
    }

    @Override
    public Process openWindows() throws Exception {
        return openWindowsPredictors(
                resolvePythonExecutable(
                        "venv" + File.separator + "Scripts"
                                + File.separator + "python.exe",
                        "venv" + File.separator + "python.exe"
                )
        );
    }

    @Override
    public Process openLinux() throws Exception {
        return openLinuxPredictors(
                resolvePythonExecutable(
                        "venv" + File.separator + "bin"
                                + File.separator + "python"
                )
        );
    }

    private Process openWindowsPredictors(
            String pythonExecutable
    ) throws Exception {
        File projectDirectory = new File(getProjectLocation());
        File errorLog = new File(
                ScenarioBase.resultDirectory,
                "prediction_error_logs.txt"
        );

        for (var predictionSetting
                : SimulationSettings.get().getPredictionSettings()) {
            JSONObject predictorSettings = new JSONObject().put(
                    "predictor-settings",
                    JsonParser.toJson(
                            predictionSetting,
                            PairPredictionSettings.class
                    )
            );

            String relativePythonExecutable = projectDirectory.toPath()
                    .toAbsolutePath()
                    .normalize()
                    .relativize(
                            new File(pythonExecutable).toPath()
                                    .toAbsolutePath()
                                    .normalize()
                    )
                    .toString();

            String command = String.format(
                    "%s main.py %s",
                    relativePythonExecutable,
                    predictorSettings.toString()
                            .replace("\"", "\\\"")
            );

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    command
            );

            processBuilder.directory(projectDirectory);
            processBuilder.redirectError(errorLog);

            PredictionLogger.info(
                    "Predictor-opening",
                    "Opening \""
                            + predictionSetting
                            + "\" named predictor"
            );

            Process predictorProcess = processBuilder.start();

            PredictionConfigurator.addPredictorProcess(
                    predictionSetting.getPredictorName(),
                    predictorProcess
            );
        }

        return null;
    }

    private Process openLinuxPredictors(
            String pythonExecutable
    ) throws Exception {
        File projectDirectory = new File(getProjectLocation());
        File predictorScript = new File(
                projectDirectory,
                "main.py"
        );
        File errorLog = new File(
                ScenarioBase.resultDirectory,
                "prediction_error_logs.txt"
        );

        for (var predictionSetting
                : SimulationSettings.get().getPredictionSettings()) {
            JSONObject predictorSettings = new JSONObject().put(
                    "predictor-settings",
                    JsonParser.toJson(
                            predictionSetting,
                            PairPredictionSettings.class
                    )
            );

            ProcessBuilder processBuilder = new ProcessBuilder(
                    pythonExecutable,
                    predictorScript.getAbsolutePath(),
                    predictorSettings.toString()
            );

            processBuilder.directory(projectDirectory);
            processBuilder.redirectError(errorLog);

            PredictionLogger.info(
                    "Predictor-opening",
                    "Opening \""
                            + predictionSetting
                            + "\" named predictor"
            );

            Process predictorProcess = processBuilder.start();

            PredictionConfigurator.addPredictorProcess(
                    predictionSetting.getPredictorName(),
                    predictorProcess
            );
        }

        return null;
    }

    private String resolvePythonExecutable(
            String... relativePaths
    ) {
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