package hu.u_szeged.inf.fog.simulator.demo.prediction;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.communication.applications.PredictorApplication;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionSimulation;
import hu.u_szeged.inf.fog.simulator.prediction.settings.simulation.*;

public class PredictionSim {
    public static void main(String[] args) throws Exception {
        PredictionSimulation predictionSimulation = new PredictionSimulation(new PredictionBaseSimulation());
        predictionSimulation.addSimulationSettings(getSimulationSettings());
        predictionSimulation.addApplications(
                new PredictorApplication()
        );
        predictionSimulation.simulate();
    }
    
    public static SimulationSettings getSimulationSettings() throws Exception {
        return new SimulationSettings(
                new ExportSettings(true, ScenarioBase.resultDirectory, true, true, false, true),
                new PredictionSettings(
                        64,
                        64,
                        256,
                        new PredictionSettings.SmoothingSettings(
                                48,
                                5
                        ),
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
        );
    }
}