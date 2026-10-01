package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hu.u_szeged.inf.fog.simulator.prediction.Feature;
import hu.u_szeged.inf.fog.simulator.prediction.FeatureManager;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite.SqLiteManager;
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
 * Tests the complete registration path from a predictor response
 * to lifecycle shadow evaluation.
 */
class FeatureManagerLifecycleIntegrationTest {

    private static final String PREDICTOR_NAME = "test-predictor";
    private static final String FEATURE_NAME = "test-feature";
    private static final double FEATURE_VALUE = 10.5;

    private LifecycleManager lifecycleManager;
    private FeatureManager featureManager;

    /**
     * Creates clean prediction, lifecycle, and database state.
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

        PairPredictionSettings pairPredictionSettings =
                new PairPredictionSettings(
                        PREDICTOR_NAME,
                        predictionSettings,
                        predictorSettings
                );

        SimulationSettings.set(
                new SimulationSettings(
                        null,
                        List.of(pairPredictionSettings),
                        null
                )
        );
    }

    /**
     * Removes shared state after each test.
     */
    @AfterEach
    void tearDown() {
        lifecycleManager.reset();
        featureManager.getFeatures().clear();

        PredictionConfigurator.predictor_reader.clear();
        PredictionConfigurator.predictor_writer.clear();

        SqLiteManager.setEnabled(false);
    }

    /**
     * Verifies that FeatureManager receives a predictor result,
     * registers its future value, and later matches the actual value.
     */
    @Test
    void predictorResultIsAutomaticallyRegisteredForLifecycle()
            throws Exception {

        Feature feature = new Feature(FEATURE_NAME) {
            @Override
            public double compute() {
                return FEATURE_VALUE;
            }
        };

        feature.computeValue();
        feature.computeValue();
        feature.computeValue();

        LifecyclePolicy policy =
                new LifecyclePolicy(
                        1,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        100.0,
                        0.0
                );

        LifecycleCoordinator coordinator =
                new LifecycleCoordinator(
                        PREDICTOR_NAME,
                        FEATURE_NAME,
                        policy
                );

        lifecycleManager.registerCoordinator(coordinator);
        coordinator.startTraining();
        coordinator.startEvaluation();

        PredictionConfigurator.predictor_reader.put(
                PREDICTOR_NAME,
                new BufferedReader(
                        new StringReader(
                                createPredictorResponse()
                        )
                )
        );

        PredictionConfigurator.predictor_writer.put(
                PREDICTOR_NAME,
                new BufferedWriter(new StringWriter())
        );

        featureManager.predict(
                List.of(feature),
                PREDICTOR_NAME
        );

        assertEquals(
                1,
                feature.getPredictions().size()
        );

        assertEquals(
                LifecycleState.EVALUATING,
                coordinator.getState()
        );

        feature.computeValue();

        EvaluationMetrics metrics =
                lifecycleManager.evaluate(
                        PREDICTOR_NAME,
                        FEATURE_NAME
                );

        assertEquals(
                4,
                feature.getValues().size()
        );

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
                0.0,
                metrics.getMse(),
                0.0
        );

        assertEquals(
                0.0,
                metrics.getRmse(),
                0.0
        );

        assertEquals(
                0.0,
                metrics.getSmape(),
                0.0
        );

        assertEquals(
                100.0,
                metrics.getAccuracy(),
                0.0
        );

        assertEquals(
                LifecycleState.READY,
                coordinator.getState()
        );
    }

    /**
     * Creates the JSON returned by the Python predictor process.
     *
     * @return the complete predictor response
     * @throws Exception when the JSON response cannot be created
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
                                PREDICTOR_NAME
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
                                                FEATURE_VALUE,
                                                FEATURE_VALUE,
                                                FEATURE_VALUE
                                        )
                                )
                        )
                        .put(
                                "preprocessed_data",
                                createData(
                                        List.of(0, 1, 2),
                                        List.of(
                                                FEATURE_VALUE,
                                                FEATURE_VALUE,
                                                FEATURE_VALUE
                                        )
                                )
                        )
                        .put(
                                "test_data_beginning",
                                createData(
                                        List.of(0, 1),
                                        List.of(
                                                FEATURE_VALUE,
                                                FEATURE_VALUE
                                        )
                                )
                        )
                        .put(
                                "test_data_end",
                                createData(
                                        List.of(2),
                                        List.of(FEATURE_VALUE)
                                )
                        )
                        .put(
                                "prediction_future",
                                createData(
                                        List.of(3),
                                        List.of(FEATURE_VALUE)
                                )
                        )
                        .put(
                                "prediction_test",
                                createData(
                                        List.of(2),
                                        List.of(FEATURE_VALUE)
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
     * Creates one timestamp-data object in predictor response format.
     *
     * @param timestamps prediction timestamps
     * @param values prediction values
     * @return the timestamp-data JSON object
     * @throws Exception when the JSON object cannot be created
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