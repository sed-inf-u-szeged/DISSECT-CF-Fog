package hu.u_szeged.inf.fog.simulator.prediction.communication.launchers;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import org.json.JSONObject;

import java.io.File;

/**
 * The class provides implementations for starting the Python-based prediction application.
 */
public class PredictorLauncher extends Launcher {
    
    public PredictorLauncher() {
        Launcher.predictionApplications.add(this);
    }

    @Override
    public String getProjectLocation() {
        String parentPath = new File(System.getProperty("user.dir")).getParentFile().getAbsolutePath();

        String str = new StringBuilder(parentPath).append(File.separator)
                .append("predictor-ui").append(File.separator).append("scripts").toString();

        return str;
    }

    @Override
    public Process openWindows() throws Exception {
        for (var predictionSetting : SimulationSettings.get().getPredictionSettings()) {
            String command = "cd /d " +
                    getProjectLocation() +
                    String.format(
                            " && %s/venv/Scripts/python.exe %s/main.py %s",
                            getProjectLocation(),
                            getProjectLocation(),
                            new JSONObject().put(
                                    "predictor-settings",
                                    JsonParser.toJson(predictionSetting, PairPredictionSettings.class)
                            ).toString().replace("\"", "\\\"")
                    );

            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
            pb.redirectError(new File(String.format("%s\\prediction_error_logs.txt", ScenarioBase.resultDirectory)));

            PredictionLogger.info("Predictor-opening", "Opening \"" + predictionSetting + "\" named predictor");
            Process predictorProcess = pb.start();

            PredictionConfigurator.addPredictorProcess(
                    predictionSetting.getPredictorName(),
                    predictorProcess
            );
        }

        return null;
    }

    @Override
    public Process openLinux() throws Exception {
        for (var predictionSetting : SimulationSettings.get().getPredictionSettings()) {
            String command = "cd /d " +
                    getProjectLocation() +
                    String.format(
                            " && %s/venv/Scripts/python.exe %s/main.py %s",
                            getProjectLocation(),
                            getProjectLocation(),
                            new JSONObject().put(
                                    "predictor-settings",
                                    JsonParser.toJson(predictionSetting, PairPredictionSettings.class)
                            ).toString().replace("\"", "\\\"")
                    );

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectError(new File(String.format("%s\\prediction_error_logs.txt", ScenarioBase.resultDirectory)));

            PredictionLogger.info("Predictor-opening", "Opening \"" + predictionSetting + "\" named predictor");
            Process predictorProcess = pb.start();

            PredictionConfigurator.addPredictorProcess(
                    predictionSetting.getPredictorName(),
                    predictorProcess
            );
        }

        return null;
    }
}