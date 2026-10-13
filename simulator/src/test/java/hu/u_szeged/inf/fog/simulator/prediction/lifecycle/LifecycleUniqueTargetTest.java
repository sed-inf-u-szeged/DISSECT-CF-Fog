package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LifecycleUniqueTargetTest {

    @Test
    void shadowDuplicatesMustNotCompleteTheWindow() {
        LifecycleCoordinator coordinator = createCoordinator();
        coordinator.startTraining();
        coordinator.startEvaluation();

        register(coordinator, 1, 1000, 100.0);
        register(coordinator, 2, 1000, 500.0);

        assertEquals(1, coordinator.getPendingShadowPredictionCount());
        assertEquals(1, coordinator.resolveActualValue(1000, 100.0));

        EvaluationMetrics metrics = coordinator.evaluate();

        assertEquals(1, metrics.getMatchedSamples());
        assertEquals(0.0, metrics.getMae(), 0.000001);
        assertFalse(coordinator.hasCompleteEvaluationWindow());
        assertEquals(LifecycleState.EVALUATING, coordinator.getState());
    }

    @Test
    void activeDuplicatesMustKeepTheFirstForecast() {
        LifecycleCoordinator coordinator = createCoordinator();
        coordinator.activateAsBaseline();

        register(coordinator, 1, 1000, 100.0);
        register(coordinator, 2, 1000, 500.0);

        assertEquals(1, coordinator.resolveActualValue(1000, 100.0));

        EvaluationMetrics metrics = coordinator.evaluate();

        assertEquals(1, metrics.getMatchedSamples());
        assertEquals(0.0, metrics.getMae(), 0.000001);
        assertEquals(100.0, metrics.getAccuracy(), 0.000001);
        assertFalse(coordinator.hasCompleteEvaluationWindow());
    }

    @Test
    void activationMustPreserveUniqueTargetsAndRollTheWindow() {
        LifecycleCoordinator coordinator = createCoordinator();
        coordinator.startTraining();
        coordinator.startEvaluation();

        register(coordinator, 1, 1000, 100.0);
        register(coordinator, 1, 2000, 100.0);
        coordinator.resolveActualValue(1000, 100.0);
        coordinator.resolveActualValue(2000, 100.0);
        coordinator.evaluate();

        assertEquals(LifecycleState.READY, coordinator.getState());
        coordinator.activate();

        // Re-registering a shadow target after activation must be ignored.
        register(coordinator, 2, 2000, 500.0);
        assertEquals(0, coordinator.resolveActualValue(2000, 100.0));

        // Only the first forecast for this new target should count.
        register(coordinator, 2, 3000, 104.0);
        register(coordinator, 3, 3000, 900.0);
        assertEquals(1, coordinator.resolveActualValue(3000, 100.0));

        EvaluationMetrics metrics = coordinator.evaluate();

        // Latest two distinct targets: 2000 and 3000; errors: 0 and 4.
        assertEquals(2, metrics.getMatchedSamples());
        assertEquals(2.0, metrics.getMae(), 0.000001);
        assertEquals(8.0, metrics.getMse(), 0.000001);
        assertEquals(Math.sqrt(8.0), metrics.getRmse(), 0.000001);
        assertEquals(100.0, metrics.getAccuracy(), 0.000001);
        assertEquals(LifecycleState.ACTIVE, coordinator.getState());
    }

    @Test
    void retrainingMustResetTheTargetHistory() {
        LifecycleCoordinator coordinator = createCoordinator();
        coordinator.startTraining();
        coordinator.startEvaluation();

        register(coordinator, 1, 1000, 100.0);
        coordinator.resolveActualValue(1000, 100.0);
        coordinator.evaluate();

        coordinator.startRetraining();
        coordinator.startEvaluation();

        // A fresh evaluation cycle must accept this target again.
        register(coordinator, 2, 1000, 104.0);
        assertEquals(1, coordinator.getPendingShadowPredictionCount());
        assertEquals(1, coordinator.resolveActualValue(1000, 100.0));

        EvaluationMetrics metrics = coordinator.evaluate();

        assertEquals(1, metrics.getMatchedSamples());
        assertEquals(4.0, metrics.getMae(), 0.000001);
        assertEquals(0, coordinator.getPendingShadowPredictionCount());
    }

    private LifecycleCoordinator createCoordinator() {
        LifecyclePolicy policy = new LifecyclePolicy(
                2, 10.0, 100.0, 10.0, 20.0, 90.0, 0.10);
        return new LifecycleCoordinator("test-model", "cpu", policy);
    }

    private void register(LifecycleCoordinator coordinator,
            int predictionNumber, int targetTimestamp, double value) {
        coordinator.registerPrediction(new ShadowPrediction(
                "test-model", "cpu", predictionNumber, targetTimestamp, value));
    }
}