package hu.u_szeged.inf.fog.simulator.prediction.communication.launchers;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.TrainSettings;
import java.io.File;
import java.io.IOException;

/**
 * Configures the shared Python launcher for LSTM training.
 */
public class LSTMTrainLauncher extends PythonTrainLauncher {

    public LSTMTrainLauncher() throws IOException {
        super("predictor_models/lstm_trainer.py", "lstm_training.log",
                prepareSettings());
    }

    private static TrainSettings prepareSettings() throws IOException {
        TrainSettings settings = SimulationSettings.get().getTrainSettings();
        if (settings == null) {
            throw new IllegalArgumentException("Training settings must not be null.");
        }

        if (settings.isCollectAllCsvAndTrainOnIt()) {
            String datasetsDirectory = ScenarioBase.resultDirectory
                    + File.separator + "datasets";

            TrainSettings.copyCSVFromDatasetDirToPath(
                    settings.getDatasetsDirectory(), datasetsDirectory);
            settings.setDatasetsDirectory(datasetsDirectory);
        }
        return settings;
    }
}