package hu.u_szeged.inf.fog.simulator.demo;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.ElectronLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.PredictorLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.settings.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("unused")
public class PredictionSimulation {
    
    public static void main(String[] args) throws Exception {
        /** run only with prediction **/
//         runPredictionOnly();
        /** run with prediction and UI **/
        runPredictionWithUI();
    }
    
    private static void runPredictionOnly() throws Exception {
        PredictionConfigurator predictionConfigurator = new PredictionConfigurator(new PredictionSimulationDefinition());

        //List of predictor setting's for multiple launch
        List<PairPredictionSettings> predictionSettings = new ArrayList<>();

        predictionSettings.add(new PairPredictionSettings(
                "Arima256",
                new PredictionSettings(
                        64,
                        64,
                        256,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true,
                        0
                ),
                PredictorSettings.getPredictorSettings(PredictorSettings.PredictorEnum.ARIMA)
        ));

        predictionSettings.add(new PairPredictionSettings(
                "LinerRegression256",
                new PredictionSettings(
                        64,
                        64,
                        256,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true,
                        0
                ),
                PredictorSettings.getPredictorSettings(PredictorSettings.PredictorEnum.LINEAR_REGRESSION)
        ));

        predictionConfigurator.addSimulationSettings(new SimulationSettings(
                new ExportSettings(true, ScenarioBase.resultDirectory, true, true, true, true),
                predictionSettings,
                new TrainSettings(
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
                )
        ));
        predictionConfigurator.addLaunchers(
                new PredictorLauncher()
        );
        predictionConfigurator.execute();
    }
    
    private static void runPredictionWithUI() throws Exception {
        PredictionConfigurator predictionConfigurator = new PredictionConfigurator(new PredictionSimulationDefinition());

        //List of predictor setting's for multiple launch
        List<PairPredictionSettings> predictionSettings = new ArrayList<>();

        predictionSettings.add(new PairPredictionSettings(
                "Arima256",
                new PredictionSettings(
                        64,
                        64,
                        256,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true,
                        0
                ),
                PredictorSettings.getPredictorSettings(PredictorSettings.PredictorEnum.ARIMA)
        ));

        predictionSettings.add(new PairPredictionSettings(
                "LinerRegression256",
                new PredictionSettings(
                        64,
                        64,
                        256,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true,
                        0
                ),
                PredictorSettings.getPredictorSettings(PredictorSettings.PredictorEnum.LINEAR_REGRESSION)
        ));

        predictionConfigurator.addSimulationSettings(new SimulationSettings(
                new ExportSettings(true, ScenarioBase.resultDirectory, true, true, true, true),
                predictionSettings,
                new TrainSettings(
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
                )
        ));

        predictionConfigurator.addLaunchers(
                new PredictorLauncher(),
                new ElectronLauncher()
        );
        predictionConfigurator.execute();
    }
}