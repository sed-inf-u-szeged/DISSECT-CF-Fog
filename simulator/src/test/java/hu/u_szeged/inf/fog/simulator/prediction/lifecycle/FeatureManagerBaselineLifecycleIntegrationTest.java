package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hu.u_szeged.inf.fog.simulator.prediction.Feature;
import hu.u_szeged.inf.fog.simulator.prediction.FeatureManager;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite.SqLiteManager;
import hu.u_szeged.inf.fog.simulator.prediction.settings.LifecycleSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictorSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies baseline activation and automatic candidate promotion
 * through the complete feature manager integration path.
 */
class FeatureManagerBaselineLifecycleIntegrationTest {

    private static final String BASELINE_PREDICTOR_NAME = "Arima128";
    private static final String CANDIDATE_PREDICTOR_NAME =
            "LinerRegression128";
    private static final String FEATURE_NAME = "test-feature";

    private LifecycleManager lifecycleManager;
    private FeatureManager featureManager;

    /**
     * Creates clean shared prediction, lifecycle, and database state.
     */
    @BeforeEach
    void setUp() {
        lifecycleManager = LifecycleManager.getInstance();
        lifecycleManager.reset();

        featureManager = FeatureManager.getInstance();
        featureManager.getFeatures().clear();

        PredictionConfigurator.CREATE_DATABASE = false;
        PredictionConfigurator.sqLiteManager = new SqLiteManager();
        PredictionConfigurator.predictor_reader = new HashMap<>();
        PredictionConfigurator.predictor_writer = new HashMap<>();
        SqLiteManager.setEnabled(false);

        SimulationSettings.set(
                new SimulationSettings(
                        null,
                        List.of(
                                createPairPredictionSettings(
                                        BASELINE_PREDICTOR_NAME
                                ),
                                createPairPredictionSettings(
                                        CANDIDATE_PREDICTOR_NAME
                                )
                        ),
                        null,
                        new LifecycleSettings(
                                true,
                                BASELINE_PREDICTOR_NAME,
                                1,
                                0.0,
                                0.0,
                                0.0,
                                0.0,
                                100.0,
                                0.0
                        )
                )
        );
    }

    /**
     * Removes shared state after every test.
     */
    @AfterEach
    void tearDown() {
        lifecycleManager.reset();
        featureManager.getFeatures().clear();

        PredictionConfigurator.predictor_reader.clear();
        PredictionConfigurator.predictor_writer.clear();

        SimulationSettings.set(null);
        SqLiteManager.setEnabled(false);
    }

    /**
     * Verifies that the configured baseline starts active and an
     * eligible candidate automatically becomes the new active model.
     */
    @Test
    void baselineStartsActiveAndCandidateAutomaticallyTakesOver()
            throws Exception {

        Feature feature = new Feature(FEATURE_NAME) {
            @Override
            public double compute() {
                return 10.0;
            }
        };

        featureManager.addFeature(feature);

        LifecycleCoordinator baselineCoordinator =
                lifecycleManager.getCoordinator(
                        BASELINE_PREDICTOR_NAME,
                        FEATURE_NAME
                );

        LifecycleCoordinator candidateCoordinator =
                lifecycleManager.getCoordinator(
                        CANDIDATE_PREDICTOR_NAME,
                        FEATURE_NAME
                );

        assertNotNull(baselineCoordinator);
        assertNotNull(candidateCoordinator);

        assertEquals(
                LifecycleState.ACTIVE,
                baselineCoordinator.getState()
        );

        assertEquals(
                LifecycleState.EVALUATING,
                candidateCoordinator.getState()
        );

        assertEquals(
                BASELINE_PREDICTOR_NAME,
                lifecycleManager.getActivePredictorName(FEATURE_NAME)
        );

        feature.computeValue();
        feature.computeValue();
        feature.computeValue();

        PredictionConfigurator.predictor_reader.put(
                CANDIDATE_PREDICTOR_NAME,
                new BufferedReader(
                        new StringReader(
                                createPredictorResponse()
                        )
                )
        );

        PredictionConfigurator.predictor_writer.put(
                CANDIDATE_PREDICTOR_NAME,
                new BufferedWriter(new StringWriter())
        );

        featureManager.predict(
                List.of(feature),
                CANDIDATE_PREDICTOR_NAME
        );

        feature.computeValue();

        EvaluationMetrics metrics =
                candidateCoordinator.getLatestMetrics();

        assertEquals(
                1,
                metrics.getMatchedSamples()
        );

        assertEquals(
                0.0,
                metrics.getMae(),
                0.0
        );

        assertEquals(
                100.0,
                metrics.getAccuracy(),
                0.0
        );

        assertEquals(
                LifecycleState.ACTIVE,
                candidateCoordinator.getState()
        );

        assertEquals(
                LifecycleState.READY,
                baselineCoordinator.getState()
        );

        assertEquals(
                CANDIDATE_PREDICTOR_NAME,
                lifecycleManager.getActivePredictorName(FEATURE_NAME)
        );
    }

    /**
     * Creates a predictor configuration for one named model.
     *
     * @param predictorName configured predictor name
     * @return prediction configuration pair
     */
    private PairPredictionSettings createPairPredictionSettings(
            String predictorName
    ) {
        PredictionSettings predictionSettings =
                new PredictionSettings(
                        1,
                        1,
                        3,
                        new PredictionSettings.SmoothingSettings(
                                1,
                                0
                        ),
                        false,
                        0
                );

        PredictorSettings predictorSettings =
                new PredictorSettings(
                        "LINEAR_REGRESSION",
                        null,
                        null
                );

        return new PairPredictionSettings(
                predictorName,
                predictionSettings,
                predictorSettings
        );
    }

    /**
     * Creates the JSON response returned by the candidate predictor.
     *
     * @return complete predictor response
     * @throws Exception when JSON creation fails
     */
    private String createPredictorResponse() throws Exception {
        JSONObject predictionSettings =
                new JSONObject()
                        .put("length", 1)
                        .put("testSize", 1)
                        .put("batchSize", 3)
                        .put(
                                "smoothing",
                                new JSONObject()
                                        .put("windowSize", 1)
                                        .put("polynomialDegree", 0)
                        )
                        .put("scale", false)
                        .put("minPredictionTime", 0);

        JSONObject predictorSettings =
                new JSONObject()
                        .put(
                                "predictor",
                                "LINEAR_REGRESSION"
                        );

        JSONObject pairPredictionSettings =
                new JSONObject()
                        .put(
                                "predictor_name",
                                CANDIDATE_PREDICTOR_NAME
                        )
                        .put(
                                "prediction",
                                predictionSettings
                        )
                        .put(
                                "predictor",
                                predictorSettings
                        );

        JSONObject prediction =
                new JSONObject()
                        .put(
                                "feature_name",
                                FEATURE_NAME
                        )
                        .put(
                                "prediction_number",
                                0
                        )
                        .put(
                                "prediction_settings",
                                pairPredictionSettings
                        )
                        .put(
                                "original_data",
                                createData(
                                        List.of(0, 1, 2),
                                        List.of(
                                                10.0,
                                                10.0,
                                                10.0
                                        )
                                )
                        )
                        .put(
                                "preprocessed_data",
                                createData(
                                        List.of(0, 1, 2),
                                        List.of(
                                                10.0,
                                                10.0,
                                                10.0
                                        )
                                )
                        )
                        .put(
                                "test_data_beginning",
                                createData(
                                        List.of(0, 1),
                                        List.of(
                                                10.0,
                                                10.0
                                        )
                                )
                        )
                        .put(
                                "test_data_end",
                                createData(
                                        List.of(2),
                                        List.of(10.0)
                                )
                        )
                        .put(
                                "prediction_future",
                                createData(
                                        List.of(3),
                                        List.of(10.0)
                                )
                        )
                        .put(
                                "prediction_test",
                                createData(
                                        List.of(2),
                                        List.of(10.0)
                                )
                        )
                        .put(
                                "error_metrics",
                                new JSONObject()
                                        .put("RMSE", 0.0)
                                        .put("MSE", 0.0)
                                        .put("MAE", 0.0)
                        )
                        .put(
                                "prediction_time",
                                1.0
                        );

        return new JSONObject()
                .put(
                        "prediction",
                        prediction
                )
                .toString();
    }

    /**
     * Creates one timestamp-data JSON object.
     *
     * @param timestamps timestamp values
     * @param values data values
     * @return JSON timestamp-data object
     * @throws Exception when JSON creation fails
     */
    private JSONObject createData(
            List<Integer> timestamps,
            List<Double> values
    ) throws Exception {
        return new JSONObject()
                .put(
                        "timestamp",
                        new JSONArray(timestamps)
                )
                .put(
                        "data",
                        new JSONArray(values)
                );
    }
}