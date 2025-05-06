package hu.u_szeged.inf.fog.simulator.prediction.communication.launchers;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
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
        String command = "cd /d " +
                getProjectLocation() +
                String.format(
                        " && %s/venv/Scripts/python.exe %s/main.py %s",
                        getProjectLocation(),
                        getProjectLocation(),
                        new JSONObject().put(
                                "simulation-settings",
                                JsonParser.toJson(SimulationSettings.get(), SimulationSettings.class)
                        ).toString().replace("\"", "\\\"")
                );

        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
        pb.redirectError(new File(String.format("%s\\prediction_error_logs.txt", ScenarioBase.resultDirectory)));

        return pb.start();
    }

    @Override
    public Process openLinux() throws Exception {
        String command = "cd " +
                getProjectLocation() +
                String.format(
                        " && %s/venv/Scripts/python3 %s/main.py %s",
                        getProjectLocation(),
                        getProjectLocation(),
                        new JSONObject().put(
                                "simulation-settings",
                                JsonParser.toJson(SimulationSettings.get(), SimulationSettings.class)
                        ).toString().replace("\"", "\\\"")
                );

        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.redirectError(new File(String.format("%s\\prediction_error_logs.txt", ScenarioBase.resultDirectory)));

        return pb.start();
    }
}