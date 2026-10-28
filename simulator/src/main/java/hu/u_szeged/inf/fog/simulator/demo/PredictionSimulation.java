package hu.u_szeged.inf.fog.simulator.demo;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.ElectronLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.PredictorLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.settings.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PredictionSimulation {

    public static void main(String[] args) throws Exception {
        boolean predictionOnly = false;
        String lifecycleProfile = "observe";

        for (String argument : args) {
            if ("--prediction-only".equalsIgnoreCase(argument)) {
                predictionOnly = true;
            } else if (argument.startsWith("--lifecycle-profile=")) {
                lifecycleProfile = argument.substring(
                        "--lifecycle-profile=".length());
            } else {
                throw new IllegalArgumentException(
                        "Unknown argument: " + argument);
            }
        }

        LifecycleSettings lifecycleSettings =
                createLifecycleSettings(lifecycleProfile);

        PredictionConfigurator configurator = new PredictionConfigurator(
                new PredictionSimulationDefinition());

        List<PairPredictionSettings> predictionSettings = new ArrayList<>();

        predictionSettings.add(new PairPredictionSettings(
                "Arima128",
                new PredictionSettings(
                        64, 64, 128,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true, 0),
                PredictorSettings.getPredictorSettings(
                        PredictorSettings.PredictorEnum.ARIMA)));

        predictionSettings.add(new PairPredictionSettings(
                "LinerRegression128",
                new PredictionSettings(
                        64, 64, 128,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true, 0),
                PredictorSettings.getPredictorSettings(
                        PredictorSettings.PredictorEnum.LINEAR_REGRESSION)));

        predictionSettings.add(new PairPredictionSettings(
                "SVR256",
                new PredictionSettings(
                        64, 64, 256,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true, 0),
                PredictorSettings.getPredictorSettings(
                        PredictorSettings.PredictorEnum.SVR)));

        predictionSettings.add(new PairPredictionSettings(
                "RandomForest256",
                new PredictionSettings(
                        64, 64, 256,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true, 0),
                PredictorSettings.getPredictorSettings(
                        PredictorSettings.PredictorEnum.RANDOM_FOREST)));

        predictionSettings.add(new PairPredictionSettings(
                "HoltWinters256",
                new PredictionSettings(
                        64, 64, 256,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true, 0),
                PredictorSettings.getPredictorSettings(
                        PredictorSettings.PredictorEnum.HOLT_WINTERS)));

        configurator.addSimulationSettings(new SimulationSettings(
                new ExportSettings(
                        true,
                        ScenarioBase.resultDirectory,
                        true, true, true, true),
                predictionSettings,
                new TrainSettings(
                        "model_i192_o64.keras",
                        ScenarioBase.resultDirectory + File.separator + "..",
                        ScenarioBase.resultDirectory,
                        false,
                        null,
                        192,
                        64,
                        "combined_mae_mean10",
                        "Adam",
                        40,
                        true),
                lifecycleSettings));

        if (predictionOnly) {
            configurator.addLaunchers(new PredictorLauncher());
        } else {
            configurator.addLaunchers(
                    new PredictorLauncher(), new ElectronLauncher());
        }

        configurator.execute();
    }

    /**
     * Separates workflow checks from an uncalibrated quality candidate.
     * No profile represents a validated production quality standard.
     */
    private static LifecycleSettings createLifecycleSettings(String profile) {
        if ("observe".equalsIgnoreCase(profile)) {
            System.out.println(
                    "Lifecycle profile: observe (permissive thresholds)");
            return new LifecycleSettings(
                    true,
                    "Arima128",
                    64,
                    Double.MAX_VALUE,
                    Double.MAX_VALUE,
                    Double.MAX_VALUE,
                    200.0,
                    0.0,
                    0.1);
        }

        if ("candidate".equalsIgnoreCase(profile)) {
            System.out.println(
                    "Lifecycle profile: candidate (experimental, uncalibrated)");
            System.out.println(
                    "Requires 64 matched samples and accuracy >= 80%; "
                    + "allowed error = max(0.1, 0.20 * abs(actual)).");
            return new LifecycleSettings(
                    true,
                    "Arima128",
                    64,
                    Double.MAX_VALUE,
                    Double.MAX_VALUE,
                    Double.MAX_VALUE,
                    200.0,
                    80.0,
                    0.20,
                    0.10);
        }

        if ("retrain-test".equalsIgnoreCase(profile)) {
            System.out.println(
                    "Lifecycle profile: retrain-test (exact predictions required)");
            return new LifecycleSettings(
                    true,
                    "Arima128",
                    64,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    100.0,
                    0.1);
        }

        throw new IllegalArgumentException(
                "Unknown lifecycle profile: " + profile
                        + ". Use observe, candidate or retrain-test.");
    }
}