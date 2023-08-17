package hu.u_szeged.inf.fog.simulator.demo.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionSimulation;
import hu.u_szeged.inf.fog.simulator.prediction.communication.applications.ElectronApplication;
import hu.u_szeged.inf.fog.simulator.prediction.communication.applications.PredictorApplication;

public class PredictionSimWithEUI {
    public static void main(String[] args) throws Exception {
        PredictionSimulation predictionSimulation = new PredictionSimulation(new PredictionBaseSimulation());
        predictionSimulation.addApplications(
                new PredictorApplication(),
                new ElectronApplication()
        );
        predictionSimulation.simulate();
    }
}
