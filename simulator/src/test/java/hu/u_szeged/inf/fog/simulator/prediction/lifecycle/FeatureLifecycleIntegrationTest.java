package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hu.u_szeged.inf.fog.simulator.prediction.Feature;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite.SqLiteManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the integration between Feature value computation
 * and lifecycle shadow evaluation.
 */
class FeatureLifecycleIntegrationTest {

    private static final String PREDICTOR_NAME = "test-predictor";
    private static final String FEATURE_NAME = "test-feature";

    private LifecycleManager lifecycleManager;

    /**
     * Creates clean lifecycle and database state before each test.
     */
    @BeforeEach
    void setUp() {
        lifecycleManager = LifecycleManager.getInstance();
        lifecycleManager.reset();

        PredictionConfigurator.CREATE_DATABASE = false;
        PredictionConfigurator.sqLiteManager = new SqLiteManager();
        SqLiteManager.setEnabled(false);
    }

    /**
     * Removes shared lifecycle state after each test.
     */
    @AfterEach
    void tearDown() {
        lifecycleManager.reset();
        SqLiteManager.setEnabled(false);
    }

    /**
     * Verifies that Feature.computeValue routes the generated actual
     * value to lifecycle shadow evaluation and automatically activates
     * an eligible predictor.
     */
    @Test
    void computeValueRoutesActualValueToLifecycleEvaluation() {
        LifecyclePolicy policy = new LifecyclePolicy(
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

        lifecycleManager.registerPrediction(
                new ShadowPrediction(
                        PREDICTOR_NAME,
                        FEATURE_NAME,
                        1,
                        0,
                        42.0
                )
        );

        Feature feature = new Feature(FEATURE_NAME) {
            @Override
            public double compute() {
                return 42.0;
            }
        };

        feature.computeValue();

        EvaluationMetrics metrics =
                coordinator.getLatestMetrics();

        assertEquals(1, feature.getValues().size());
        assertEquals(42.0, feature.getValues().get(0), 0.0);
        assertEquals(1, metrics.getMatchedSamples());
        assertEquals(0.0, metrics.getMae(), 0.0);
        assertEquals(0.0, metrics.getMse(), 0.0);
        assertEquals(0.0, metrics.getRmse(), 0.0);
        assertEquals(0.0, metrics.getSmape(), 0.0);
        assertEquals(100.0, metrics.getAccuracy(), 0.0);

        assertEquals(
                LifecycleState.ACTIVE,
                coordinator.getState()
        );

        assertEquals(
                PREDICTOR_NAME,
                lifecycleManager.getActivePredictorName(FEATURE_NAME)
        );
    }
}