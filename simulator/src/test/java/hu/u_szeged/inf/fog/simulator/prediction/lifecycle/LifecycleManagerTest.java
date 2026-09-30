package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LifecycleManagerTest {

    private LifecycleManager manager;

    @BeforeEach
    void resetManager() {
        manager = LifecycleManager.getInstance();
        manager.reset();
    }

    @Test
    void shouldRegisterCoordinatorsForDifferentModelsAndFeatures() {
        LifecycleCoordinator lstmCpu =
                createCoordinator("lstm", "fog-1::cpu");

        LifecycleCoordinator gruCpu =
                createCoordinator("gru", "fog-1::cpu");

        LifecycleCoordinator lstmMemory =
                createCoordinator("lstm", "fog-1::memory");

        manager.registerCoordinator(lstmCpu);
        manager.registerCoordinator(gruCpu);
        manager.registerCoordinator(lstmMemory);

        assertSame(
                lstmCpu,
                manager.getCoordinator("lstm", "fog-1::cpu")
        );

        assertSame(
                gruCpu,
                manager.getCoordinator("gru", "fog-1::cpu")
        );

        assertSame(
                lstmMemory,
                manager.getCoordinator("lstm", "fog-1::memory")
        );
    }

    @Test
    void shouldRejectDuplicateCoordinator() {
        manager.registerCoordinator(
                createCoordinator("lstm", "fog-1::cpu")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.registerCoordinator(
                        createCoordinator("lstm", "fog-1::cpu")
                )
        );
    }

    @Test
    void shouldRoutePredictionsAndActualValues() {
        LifecycleCoordinator lstm =
                createCoordinator("lstm", "fog-1::cpu");

        LifecycleCoordinator gru =
                createCoordinator("gru", "fog-1::cpu");

        manager.registerCoordinator(lstm);
        manager.registerCoordinator(gru);

        startEvaluation(lstm);
        startEvaluation(gru);

        manager.registerPrediction(new ShadowPrediction(
                "lstm",
                "fog-1::cpu",
                1,
                64,
                100.0
        ));

        manager.registerPrediction(new ShadowPrediction(
                "gru",
                "fog-1::cpu",
                1,
                64,
                105.0
        ));

        int resolvedCount = manager.resolveActualValue(
                "fog-1::cpu",
                64,
                100.0
        );

        EvaluationMetrics lstmMetrics =
                manager.evaluate("lstm", "fog-1::cpu");

        EvaluationMetrics gruMetrics =
                manager.evaluate("gru", "fog-1::cpu");

        assertEquals(2, resolvedCount);
        assertEquals(1, lstmMetrics.getMatchedSamples());
        assertEquals(1, gruMetrics.getMatchedSamples());
        assertEquals(0.0, lstmMetrics.getMae());
        assertEquals(5.0, gruMetrics.getMae());
        assertEquals(LifecycleState.READY, lstm.getState());
        assertEquals(LifecycleState.READY, gru.getState());
    }

    @Test
    void shouldRejectPredictionWithoutCoordinator() {
        ShadowPrediction prediction = new ShadowPrediction(
                "lstm",
                "fog-1::cpu",
                1,
                64,
                100.0
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.registerPrediction(prediction)
        );
    }

    @Test
    void shouldRegisterPredictionWhenCoordinatorIsEvaluating() {
        LifecycleCoordinator coordinator =
                createCoordinator("lstm", "fog-1::cpu");

        manager.registerCoordinator(coordinator);
        startEvaluation(coordinator);

        boolean registered =
                manager.registerPredictionIfEvaluating(
                        new ShadowPrediction(
                                "lstm",
                                "fog-1::cpu",
                                1,
                                64,
                                100.0
                        )
                );

        int resolvedCount = manager.resolveActualValue(
                "fog-1::cpu",
                64,
                100.0
        );

        assertTrue(registered);
        assertEquals(1, resolvedCount);
    }

    @Test
    void shouldIgnorePredictionWithoutCoordinator() {
        boolean registered =
                manager.registerPredictionIfEvaluating(
                        new ShadowPrediction(
                                "lstm",
                                "fog-1::cpu",
                                1,
                                64,
                                100.0
                        )
                );

        assertFalse(registered);
    }

    @Test
    void shouldIgnorePredictionWhenCoordinatorIsNotEvaluating() {
        LifecycleCoordinator coordinator =
                createCoordinator("lstm", "fog-1::cpu");

        manager.registerCoordinator(coordinator);

        boolean registered =
                manager.registerPredictionIfEvaluating(
                        new ShadowPrediction(
                                "lstm",
                                "fog-1::cpu",
                                1,
                                64,
                                100.0
                        )
                );

        assertFalse(registered);
    }

    @Test
    void shouldRejectNullPredictionForSafeRegistration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.registerPredictionIfEvaluating(null)
        );
    }

    @Test
    void shouldRemoveCoordinatorsWhenReset() {
        manager.registerCoordinator(
                createCoordinator("lstm", "fog-1::cpu")
        );

        manager.reset();

        assertEquals(
                null,
                manager.getCoordinator("lstm", "fog-1::cpu")
        );
    }

    private LifecycleCoordinator createCoordinator(
            String predictorName,
            String featureName
    ) {
        return new LifecycleCoordinator(
                predictorName,
                featureName,
                new LifecyclePolicy(
                        1,
                        10.0,
                        100.0,
                        10.0,
                        20.0,
                        90.0,
                        0.10
                )
        );
    }

    private void startEvaluation(
            LifecycleCoordinator coordinator
    ) {
        coordinator.startTraining();
        coordinator.startEvaluation();
    }
}