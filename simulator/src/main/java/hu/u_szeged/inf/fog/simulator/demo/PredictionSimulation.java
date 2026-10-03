package hu.u_szeged.inf.fog.simulator.demo;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.ElectronLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.PredictorLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.settings.ExportSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.LifecycleSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictorSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.TrainSettings;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class PredictionSimulation {

    public static void main(String[] args) throws Exception {
        if (args.length > 0
                && "--prediction-only".equalsIgnoreCase(args[0])) {
            runPredictionOnly();
        } else {
            runPredictionWithUI();
        }
    }

    private static void runPredictionOnly() throws Exception {
        PredictionConfigurator predictionConfigurator =
                new PredictionConfigurator(
                        new PredictionSimulationDefinition()
                );

        List<PairPredictionSettings> predictionSettings =
                createPredictionSettings();

        predictionConfigurator.addSimulationSettings(
                new SimulationSettings(
                        new ExportSettings(
                                true,
                                ScenarioBase.resultDirectory,
                                true,
                                true,
                                true,
                                true
                        ),
                        predictionSettings,
                        createTrainSettings(),
                        createLifecycleSettings()
                )
        );

        predictionConfigurator.addLaunchers(
                new PredictorLauncher()
        );

        predictionConfigurator.execute();
    }

    private static void runPredictionWithUI() throws Exception {
        PredictionConfigurator predictionConfigurator =
                new PredictionConfigurator(
                        new PredictionSimulationDefinition()
                );

        List<PairPredictionSettings> predictionSettings =
                createPredictionSettings();

        predictionConfigurator.addSimulationSettings(
                new SimulationSettings(
                        new ExportSettings(
                                true,
                                ScenarioBase.resultDirectory,
                                true,
                                true,
                                true,
                                true
                        ),
                        predictionSettings,
                        createTrainSettings(),
                        createLifecycleSettings()
                )
        );

        predictionConfigurator.addLaunchers(
                new PredictorLauncher(),
                new ElectronLauncher()
        );

        predictionConfigurator.execute();
    }

    private static List<PairPredictionSettings>
            createPredictionSettings() throws Exception {
        List<PairPredictionSettings> predictionSettings =
                new ArrayList<>();

        predictionSettings.add(
                new PairPredictionSettings(
                        "Arima128",
                        new PredictionSettings(
                                64,
                                64,
                                128,
                                new PredictionSettings.SmoothingSettings(
                                        48,
                                        5
                                ),
                                true,
                                0
                        ),
                        PredictorSettings.getPredictorSettings(
                                PredictorSettings.PredictorEnum.ARIMA
                        )
                )
        );

        predictionSettings.add(
                new PairPredictionSettings(
                        "LinerRegression128",
                        new PredictionSettings(
                                64,
                                64,
                                128,
                                new PredictionSettings.SmoothingSettings(
                                        48,
                                        5
                                ),
                                true,
                                0
                        ),
                        PredictorSettings.getPredictorSettings(
                                PredictorSettings.PredictorEnum
                                        .LINEAR_REGRESSION
                        )
                )
        );

        return predictionSettings;
    }

    private static TrainSettings createTrainSettings() {
        return new TrainSettings(
                "model_i256_o64.h5",
                ScenarioBase.resultDirectory
                        + File.separator
                        + "..",
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

    private static LifecycleSettings createLifecycleSettings() {
        return new LifecycleSettings(
                true,
                "Arima128",
                64,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                200.0,
                0.0,
                0.1
        );
    }
}