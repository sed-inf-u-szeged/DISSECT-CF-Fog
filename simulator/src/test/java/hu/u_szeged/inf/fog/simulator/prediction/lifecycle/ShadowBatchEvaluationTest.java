package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowBatchEvaluationTest {

    @Test
    void accumulatesSamplesAcrossBatchesBeforeBecomingReady() {
        LifecycleCoordinator coordinator = candidate(4);

        register(coordinator, 1, 100, 100);
        register(coordinator, 1, 101, 100);
        assertEquals(2, coordinator.getPendingShadowPredictionCount());

        coordinator.resolveActualValue(100, 100);
        coordinator.resolveActualValue(101, 100);
        coordinator.evaluate();

        assertEquals(0, coordinator.getPendingShadowPredictionCount());
        assertEquals(2, coordinator.getLatestMetrics().getMatchedSamples());
        assertFalse(coordinator.hasCompleteEvaluationWindow());
        assertEquals(LifecycleState.EVALUATING, coordinator.getState());

        register(coordinator, 2, 102, 100);
        register(coordinator, 2, 103, 100);
        coordinator.resolveActualValue(102, 100);
        coordinator.resolveActualValue(103, 100);
        coordinator.evaluate();

        assertEquals(0, coordinator.getPendingShadowPredictionCount());
        assertEquals(4, coordinator.getLatestMetrics().getMatchedSamples());
        assertTrue(coordinator.hasCompleteEvaluationWindow());
        assertEquals(LifecycleState.READY, coordinator.getState());
    }

    @Test
    void duplicateTargetKeepsFirstPredictionAndCountsOnce() {
        LifecycleCoordinator coordinator = candidate(2);

        register(coordinator, 1, 100, 80);
        register(coordinator, 2, 100, 100);
        assertEquals(1, coordinator.getPendingShadowPredictionCount());

        assertEquals(1, coordinator.resolveActualValue(100, 100));
        coordinator.evaluate();

        assertEquals(1, coordinator.getLatestMetrics().getMatchedSamples());
        assertEquals(20.0, coordinator.getLatestMetrics().getMae(), 0.000001);
        assertFalse(coordinator.hasCompleteEvaluationWindow());
    }

    @Test
    void resolvedTargetCannotBeCountedAgainDuringShadow() {
        LifecycleCoordinator coordinator = candidate(2);

        register(coordinator, 1, 100, 100);
        coordinator.resolveActualValue(100, 100);
        coordinator.evaluate();

        register(coordinator, 2, 100, 100);
        assertEquals(0, coordinator.getPendingShadowPredictionCount());
        assertEquals(0, coordinator.resolveActualValue(100, 100));

        coordinator.evaluate();
        assertEquals(1, coordinator.getLatestMetrics().getMatchedSamples());
        assertEquals(LifecycleState.EVALUATING, coordinator.getState());
    }

    @Test
    void unrelatedActualValueDoesNotCompletePendingPrediction() {
        LifecycleCoordinator coordinator = candidate(2);

        register(coordinator, 1, 100, 100);
        assertEquals(0, coordinator.resolveActualValue(99, 100));
        assertEquals(1, coordinator.getPendingShadowPredictionCount());

        coordinator.evaluate();
        assertEquals(0, coordinator.getLatestMetrics().getMatchedSamples());
    }

    @Test
    void retrainingClearsPreviousTargetsAndPendingPredictions() {
        LifecycleCoordinator coordinator = candidate(2);

        register(coordinator, 1, 100, 100);
        register(coordinator, 1, 101, 100);
        coordinator.resolveActualValue(100, 100);
        coordinator.evaluate();

        coordinator.startRetraining();
        assertNull(coordinator.getLatestMetrics());
        assertEquals(0, coordinator.getPendingShadowPredictionCount());

        coordinator.startEvaluation();
        register(coordinator, 2, 100, 100);
        assertEquals(1, coordinator.getPendingShadowPredictionCount());
        assertEquals(1, coordinator.resolveActualValue(100, 100));

        coordinator.evaluate();
        assertEquals(1, coordinator.getLatestMetrics().getMatchedSamples());
    }

    private LifecycleCoordinator candidate(int minimumSamples) {
        LifecyclePolicy policy = new LifecyclePolicy(
                minimumSamples, 0, 0, 0, 0, 100, 0, 0);
        LifecycleCoordinator coordinator =
                new LifecycleCoordinator("candidate-v1", "cpu", policy);
        coordinator.setAutomaticPromotionAllowed(false);
        coordinator.startTraining();
        coordinator.startEvaluation();
        return coordinator;
    }

    private void register(LifecycleCoordinator coordinator,
            int batch, int target, double predicted) {
        coordinator.registerPrediction(new ShadowPrediction(
                "candidate-v1", "cpu", batch, target, predicted));
    }
}