package hu.u_szeged.inf.fog.simulator.demo;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.ElectronLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.PredictorLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.settings.ExportSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictorSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;

@SuppressWarnings("unused")
public class PredictionSimulation {
    
    public static void main(String[] args) throws Exception {
        /** run only with prediction **/
        // runPredictionOnly();
        /** run with prediction and UI **/
        runPredictionWithUI();
    }
    
    private static void runPredictionOnly() throws Exception {
        PredictionConfigurator predictionConfigurator = new PredictionConfigurator(new PredictionSimulationDefinition());
        
        predictionConfigurator.addSimulationSettings(new SimulationSettings(
                new ExportSettings(true, ScenarioBase.resultDirectory, true, true, true, true),
                new PredictionSettings(
                        64,
                        64,
                        256,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true,
                        0
                ),
                PredictorSettings.getPredictorSettings(PredictorSettings.PredictorEnum.ARIMA)
                //PredictorSettings.getPredictorSettings(PredictorSettings.PredictorEnum.HOLT_WINTERS)
                //PredictorSettings.getPredictorSettings(PredictorSettings.PredictorEnum.RANDOM_FOREST)
                //PredictorSettings.getPredictorSettings(PredictorSettings.PredictorEnum.LINEAR_REGRESSION)
                //PredictorSettings.getPredictorSettings(PredictorSettings.PredictorEnum.SVR)
                //PredictorSettings.getPredictorSettings(PredictorSettings.PredictorEnum.LSTM)
                //PredictorSettings.getPredictorSettings(PredictorSettings.PredictorEnum.ONLY_SIMULATION)
        ));
        predictionConfigurator.addLaunchers(
                new PredictorLauncher()
        );
        predictionConfigurator.execute();
    }
    
    private static void runPredictionWithUI() throws Exception {
        PredictionConfigurator predictionConfigurator = new PredictionConfigurator(new PredictionSimulationDefinition());
        
        predictionConfigurator.addLaunchers(
                new PredictorLauncher(),
                new ElectronLauncher()
        );
        predictionConfigurator.execute();
    }
}