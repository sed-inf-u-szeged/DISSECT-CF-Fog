package hu.u_szeged.inf.fog.simulator.prediction.communication.launchers;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.TrainSettings;
import org.json.JSONObject;

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
        String parentPath = new File(System.getProperty("user.dir")).getParentFile().getAbsolutePath();

        String str = new StringBuilder(parentPath).append(File.separator)
                .append("predictor-ui").append(File.separator).append("scripts").toString();

        return str;
    }

    @Override
    public Process openWindows() throws Exception {
        String command = "cd /d " +
                getProjectLocation() +
                String.format(
                        " && start cmd.exe /k %s/venv/Scripts/python.exe %s/predictor_models/lstm_trainer.py %s",
                        getProjectLocation(),
                        getProjectLocation(),
                        JsonParser.toJson(
                            SimulationSettings.get().getTrainSettings(), TrainSettings.class
                        ).toString().replace("\"", "\\\"")
                );

        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
//        pb.redirectError(new File(String.format("%s\\train_error_logs.txt", ScenarioBase.resultDirectory)));

        PredictionLogger.info("LSTM trainer", "Opening trainer");
        return pb.start();
    }

    @Override
    public Process openLinux() throws Exception {
        String command = "cd /d " +
                getProjectLocation() +
                String.format(
                        " && start cmd.exe /k %s/venv/Scripts/python.exe %s/predictor_models/lstm_trainer.py %s",
                        getProjectLocation(),
                        getProjectLocation(),
                        JsonParser.toJson(
                                SimulationSettings.get().getTrainSettings(), TrainSettings.class
                        ).toString().replace("\"", "\\\"")
                );

        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
//        pb.redirectError(new File(String.format("%s\\train_error_logs.txt", ScenarioBase.resultDirectory)));

        PredictionLogger.info("LSTM trainer", "Opening trainer");
        return pb.start();
    }
}
