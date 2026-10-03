package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LifecycleCoordinatorTest {

    @Test
    void shouldStartInCollectingDataState() {
        LifecycleCoordinator coordinator =
                createCoordinator(createPolicy());

        assertEquals(
                "linear-regression",
                coordinator.getPredictorName()
        );
        assertEquals("cpu", coordinator.getFeatureName());
        assertEquals(
                LifecycleState.COLLECTING_DATA,
                coordinator.getState()
        );
        assertNull(coordinator.getLatestMetrics());
    }

    @Test
    void shouldFollowTheInitialLifecycleOrder() {
        LifecycleCoordinator coordinator =
                createCoordinator(createPolicy());

        coordinator.startTraining();

        assertEquals(
                LifecycleState.TRAINING,
                coordinator.getState()
        );

        coordinator.startEvaluation();

        assertEquals(
                LifecycleState.EVALUATING,
                coordinator.getState()
        );
    }

    @Test
    void shouldBecomeReadyWhenEveryPolicyConditionPasses() {
        LifecycleCoordinator coordinator =
                createCoordinator(createPolicy());

        moveToReady(coordinator);

        EvaluationMetrics metrics =
                coordinator.getLatestMetrics();

        assertEquals(2, metrics.getMatchedSamples());
        assertEquals(
                LifecycleState.READY,
                coordinator.getState()
        );
    }

    @Test
    void shouldRemainEvaluatingWhenPolicyDoesNotPass() {
        LifecycleCoordinator coordinator =
                createCoordinator(createPolicy());

        coordinator.startTraining();
        coordinator.startEvaluation();

        coordinator.registerPrediction(new ShadowPrediction(
                "linear-regression",
                "cpu",
                1,
                1000,
                100.0
        ));

        coordinator.resolveActualValue(1000, 100.0);
        coordinator.evaluate();

        assertEquals(
                LifecycleState.EVALUATING,
                coordinator.getState()
        );
    }

    @Test
    void shouldRejectInvalidStateTransitionAndForeignPrediction() {
        LifecycleCoordinator coordinator =
                createCoordinator(createPolicy());

        assertThrows(
                IllegalStateException.class,
                coordinator::startEvaluation
        );

        coordinator.startTraining();
        coordinator.startEvaluation();

        ShadowPrediction foreignPrediction = new ShadowPrediction(
                "lstm",
                "cpu",
                1,
                1000,
                100.0
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.registerPrediction(
                        foreignPrediction
                )
        );
    }

    @Test
    void shouldActivateReadyPredictor() {
        LifecycleCoordinator coordinator =
                createCoordinator(createPolicy());

        moveToReady(coordinator);
        coordinator.activate();

        assertEquals(
                LifecycleState.ACTIVE,
                coordinator.getState()
        );
    }

    @Test
    void shouldRejectActivationBeforePredictorIsReady() {
        LifecycleCoordinator coordinator =
                createCoordinator(createPolicy());

        coordinator.startTraining();
        coordinator.startEvaluation();

        assertThrows(
                IllegalStateException.class,
                coordinator::activate
        );

        assertEquals(
                LifecycleState.EVALUATING,
                coordinator.getState()
        );
    }

    @Test
    void shouldReturnActivePredictorToReadyWhenDeactivated() {
        LifecycleCoordinator coordinator =
                createCoordinator(createPolicy());

        moveToReady(coordinator);
        coordinator.activate();
        coordinator.deactivate();

        assertEquals(
                LifecycleState.READY,
                coordinator.getState()
        );
    }

    @Test
    void shouldEnterFailedStateAfterTechnicalFailure() {
        LifecycleCoordinator coordinator =
                createCoordinator(createPolicy());

        coordinator.markFailed();

        assertEquals(
                LifecycleState.FAILED,
                coordinator.getState()
        );
    }

    private void moveToReady(
            LifecycleCoordinator coordinator
    ) {
        coordinator.startTraining();
        coordinator.startEvaluation();

        coordinator.registerPrediction(new ShadowPrediction(
                "linear-regression",
                "cpu",
                1,
                1000,
                100.0
        ));

        coordinator.registerPrediction(new ShadowPrediction(
                "linear-regression",
                "cpu",
                2,
                2000,
                100.0
        ));

        coordinator.resolveActualValue(1000, 100.0);
        coordinator.resolveActualValue(2000, 100.0);
        coordinator.evaluate();
    }

    private LifecycleCoordinator createCoordinator(
            LifecyclePolicy policy
    ) {
        return new LifecycleCoordinator(
                "linear-regression",
                "cpu",
                policy
        );
    }

    private LifecyclePolicy createPolicy() {
        return new LifecyclePolicy(
                2,
                10.0,
                100.0,
                10.0,
                20.0,
                90.0,
                0.10
        );
    }
}