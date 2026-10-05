package hu.u_szeged.inf.fog.simulator.demo;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.ElectronLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.PredictorLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.settings.*;
import hu.u_szeged.inf.fog.simulator.util.ProjectPathResolver;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("unused")
public class PredictionSimulation {

    public static void main(String[] args) throws Exception {
        /** run only with prediction **/
        if (args.length > 0
                && "--prediction-only".equalsIgnoreCase(args[0])) {
            runPredictionOnly();
            return;
        }

        /** run with prediction and UI **/
        runPredictionWithUI();
    }

    private static void runPredictionOnly() throws Exception {
        PredictionConfigurator predictionConfigurator =
                new PredictionConfigurator(
                        new PredictionSimulationDefinition()
                );

        //List of predictor setting's for multiple launch
        List<PairPredictionSettings> predictionSettings =
                new ArrayList<>();

        predictionSettings.add(new PairPredictionSettings(
                "Arima128",
                new PredictionSettings(
                        64,
                        64,
                        128,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true,
                        0
                ),
                PredictorSettings.getPredictorSettings(
                        PredictorSettings.PredictorEnum.ARIMA
                )
        ));

        predictionSettings.add(new PairPredictionSettings(
                "LinerRegression128",
                new PredictionSettings(
                        64,
                        64,
                        128,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true,
                        0
                ),
                PredictorSettings.getPredictorSettings(
                        PredictorSettings.PredictorEnum.LINEAR_REGRESSION
                )
        ));

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
                        new TrainSettings(
                                "model_i192_o64.h5",
                                ScenarioBase.resultDirectory
                                        + File.separator + "..",
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
                        ),
                        new LifecycleSettings(
                                true,
                                "Arima128",
                                64,
                                Double.MAX_VALUE,
                                Double.MAX_VALUE,
                                Double.MAX_VALUE,
                                200.0,
                                0.0,
                                0.1
                        )
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

        //List of predictor setting's for multiple launch
        List<PairPredictionSettings> predictionSettings =
                new ArrayList<>();

        predictionSettings.add(new PairPredictionSettings(
                "Arima128",
                new PredictionSettings(
                        64,
                        64,
                        128,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true,
                        0
                ),
                PredictorSettings.getPredictorSettings(
                        PredictorSettings.PredictorEnum.ARIMA
                )
        ));

        predictionSettings.add(new PairPredictionSettings(
                "LinerRegression128",
                new PredictionSettings(
                        64,
                        64,
                        128,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true,
                        0
                ),
                PredictorSettings.getPredictorSettings(
                        PredictorSettings.PredictorEnum.LINEAR_REGRESSION
                )
        ));

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
                        new TrainSettings(
                                "model_i192_o64.h5",
                                ScenarioBase.resultDirectory
                                        + File.separator + "..",
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
                        ),
                        new LifecycleSettings(
                                true,
                                "Arima128",
                                64,
                                Double.MAX_VALUE,
                                Double.MAX_VALUE,
                                Double.MAX_VALUE,
                                200.0,
                                0.0,
                                0.1
                        )
                )
        );

        predictionConfigurator.addLaunchers(
                new PredictorLauncher(),
                new ElectronLauncher()
        );

        predictionConfigurator.execute();
    }

    private static void addLstmPredictionSettingsIfAvailable(
            List<PairPredictionSettings> predictionSettings
    ) throws Exception {
        if (!hasActiveLstmModel()) {
            return;
        }

        predictionSettings.add(new PairPredictionSettings(
                "LSTM256",
                new PredictionSettings(
                        64,
                        64,
                        256,
                        new PredictionSettings.SmoothingSettings(48, 5),
                        true,
                        0
                ),
                PredictorSettings.getPredictorSettings(
                        PredictorSettings.PredictorEnum.LSTM
                )
        ));
    }

    private static boolean hasActiveLstmModel() throws Exception {
        Path modelStore = resolveModelStoreDirectory();
        Path registryPath = modelStore.resolve("registry.json");

        if (!Files.isRegularFile(registryPath)) {
            return false;
        }

        JSONObject registry = new JSONObject(
                Files.readString(
                        registryPath,
                        StandardCharsets.UTF_8
                )
        );

        JSONObject active = registry.optJSONObject("active");

        if (active == null
                || active.optJSONObject("shared") == null) {
            return false;
        }

        String activeVersion = active
                .getJSONObject("shared")
                .optString("LSTM", "")
                .trim();

        if (activeVersion.isEmpty()) {
            return false;
        }

        JSONObject modelEntry = registry
                .getJSONObject("models")
                .getJSONObject("shared")
                .getJSONObject("LSTM")
                .getJSONObject(activeVersion);

        Path normalizedStore =
                modelStore.toAbsolutePath().normalize();

        Path artifactPath = normalizedStore
                .resolve(modelEntry.getString("artifact_path"))
                .normalize();

        if (!artifactPath.startsWith(normalizedStore)) {
            throw new IllegalStateException(
                    "The active LSTM artifact path "
                            + "escapes the model store"
            );
        }

        return Files.isRegularFile(artifactPath);
    }

    private static Path resolveModelStoreDirectory() {
        return ProjectPathResolver.getModelStoreDirectory();
    }
}
