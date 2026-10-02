package hu.u_szeged.inf.fog.simulator.prediction.communication.launchers;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import java.io.File;
import java.util.Locale;
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
        String parentPath = new File(System.getProperty("user.dir"))
                .getParentFile()
                .getAbsolutePath();

        return new StringBuilder(parentPath)
                .append(File.separator)
                .append("predictor-ui")
                .append(File.separator)
                .append("scripts")
                .toString();
    }

    @Override
    public Process openWindows() throws Exception {
        return openPredictors(
                resolvePythonExecutable(
                        "venv" + File.separator + "Scripts" + File.separator + "python.exe",
                        "venv" + File.separator + "python.exe"
                )
        );
    }

    @Override
    public Process openLinux() throws Exception {
        return openPredictors(
                resolvePythonExecutable(
                        "venv" + File.separator + "bin" + File.separator + "python"
                )
        );
    }

    private Process openPredictors(String pythonExecutable) throws Exception {
        File projectDirectory = new File(getProjectLocation());
        File predictorScript = new File(projectDirectory, "main.py");
        File errorLog = new File(
                ScenarioBase.resultDirectory,
                "prediction_error_logs.txt"
        );

        for (var predictionSetting : SimulationSettings.get().getPredictionSettings()) {
            JSONObject predictorSettings = new JSONObject().put(
                    "predictor-settings",
                    JsonParser.toJson(predictionSetting, PairPredictionSettings.class)
            );

            ProcessBuilder pb = new ProcessBuilder(
                    pythonExecutable,
                    predictorScript.getAbsolutePath(),
                    buildPredictorSettingsArgument(predictorSettings)
            );

            pb.directory(projectDirectory);
            pb.redirectError(errorLog);

            PredictionLogger.info(
                    "Predictor-opening",
                    "Opening \"" + predictionSetting + "\" named predictor"
            );

            Process predictorProcess = pb.start();

            PredictionConfigurator.addPredictorProcess(
                    predictionSetting.getPredictorName(),
                    predictorProcess
            );
        }

        return null;
    }

    private String buildPredictorSettingsArgument(JSONObject predictorSettings) {
        String json = predictorSettings.toString();

        boolean runningOnWindows = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT)
                .contains("win");

        if (runningOnWindows) {
            return json.replace("\"", "\\\"");
        }

        return json;
    }

    private String resolvePythonExecutable(String... relativePaths) {
        String projectLocation = getProjectLocation();

        for (String relativePath : relativePaths) {
            File candidate = new File(projectLocation, relativePath);

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