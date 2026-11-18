package hu.u_szeged.inf.fog.simulator.demo.simple;

import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.MobilityUiLauncher;

/** Click Run to choose a mobility strategy and start the simulation from the UI. */
public final class MobilityPredictorUi {
    private MobilityPredictorUi() { }

    public static void main(String[] args) throws Exception {
        int exit = MobilityUiLauncher.configure().waitFor();
        if (exit != 0) {
            throw new IllegalStateException("Predictor UI exited with code " + exit
                    + ". See simulator/sim_res/mobility-ui/electron.log");
        }
    }
}
