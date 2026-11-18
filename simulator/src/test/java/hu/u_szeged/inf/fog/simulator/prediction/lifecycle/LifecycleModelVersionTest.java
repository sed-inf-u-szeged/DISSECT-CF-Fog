package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LifecycleModelVersionTest {

    private LifecycleManager manager;

    @BeforeEach
    void setUp() {
        manager = LifecycleManager.getInstance();
        manager.reset();
    }

    @AfterEach
    void tearDown() {
        manager.reset();
    }

    @Test
    void rejectedOrFailedCandidateDoesNotStopServingModel() {
        LifecycleCoordinator serving = startServing("LSTM256-v1", "A");
        LifecycleCoordinator candidate = createCandidate("LSTM256-v2", "A");

        register("LSTM256-v1", "A", 2, 20, 100.0);
        register("LSTM256-v2", "A", 1, 20, 200.0);
        manager.resolveActualValue("A", 20, 100.0);

        assertTrue(candidate.hasCompleteEvaluationWindow());
        assertFalse(candidate.isPolicySatisfied());
        assertEquals(LifecycleState.EVALUATING, candidate.getState());
        assertEquals(LifecycleState.ACTIVE, serving.getState());
        assertTrue(manager.isActive("LSTM256-v1", "A"));

        candidate.markFailed();

        assertEquals(LifecycleState.FAILED, candidate.getState());
        assertEquals(LifecycleState.ACTIVE, serving.getState());
        assertTrue(manager.isActive("LSTM256-v1", "A"));
        assertFalse(manager.isActive("LSTM256-v2", "A"));
    }

    @Test
    void promotingOneNodeKeepsOtherNodeOnServingVersion() {
        LifecycleCoordinator oldA = startServing("LSTM256-v1", "A");
        LifecycleCoordinator oldB = startServing("LSTM256-v1", "B");
        LifecycleCoordinator newA = createCandidate("LSTM256-v2", "A");
        LifecycleCoordinator newB = createCandidate("LSTM256-v2", "B");

        register("LSTM256-v2", "A", 1, 20, 100.0);
        manager.resolveActualValue("A", 20, 100.0);

        assertEquals(LifecycleState.READY, newA.getState());
        assertTrue(manager.isActive("LSTM256-v1", "A"));

        manager.promote("LSTM256-v2", "A");

        assertTrue(manager.isActive("LSTM256-v2", "A"));
        assertFalse(manager.isActive("LSTM256-v1", "A"));
        assertEquals(LifecycleState.READY, oldA.getState());
        assertTrue(manager.isActive("LSTM256-v1", "B"));
        assertEquals(LifecycleState.ACTIVE, oldB.getState());
        assertEquals(LifecycleState.EVALUATING, newB.getState());

        register("LSTM256-v2", "B", 1, 21, 100.0);
        manager.resolveActualValue("B", 21, 100.0);
        manager.promote("LSTM256-v2", "B");

        assertTrue(manager.isActive("LSTM256-v2", "B"));
        assertFalse(manager.isActive("LSTM256-v1", "B"));
        assertTrue(manager.isActive("LSTM256-v2", "A"));
    }

    @Test
    void versionsKeepSeparateEvaluationRecords() {
        LifecycleCoordinator oldModel = createCandidate("LSTM256-v1", "A");
        LifecycleCoordinator newModel = createCandidate("LSTM256-v2", "A");

        // Same node, prediction number and target time; different versions.
        register("LSTM256-v1", "A", 1, 20, 100.0);
        register("LSTM256-v2", "A", 1, 20, 200.0);

        int resolved = manager.resolveActualValue("A", 20, 100.0);

        assertEquals(2, resolved);
        assertEquals(1, oldModel.getLatestMetrics().getMatchedSamples());
        assertEquals(1, newModel.getLatestMetrics().getMatchedSamples());
        assertTrue(oldModel.isPolicySatisfied());
        assertFalse(newModel.isPolicySatisfied());
        assertEquals(LifecycleState.READY, oldModel.getState());
        assertEquals(LifecycleState.EVALUATING, newModel.getState());
        assertFalse(manager.isActive("LSTM256-v1", "A"));
        assertFalse(manager.isActive("LSTM256-v2", "A"));
    }

    private LifecycleCoordinator createCandidate(String name, String feature) {
        LifecyclePolicy policy =
                new LifecyclePolicy(1, 1.0, 1.0, 1.0, 5.0, 100.0, 0.1);
        LifecycleCoordinator coordinator =
                new LifecycleCoordinator(name, feature, policy);
        coordinator.setAutomaticPromotionAllowed(false);
        manager.registerCoordinator(coordinator);
        coordinator.startTraining();
        coordinator.startEvaluation();
        return coordinator;
    }

    private LifecycleCoordinator startServing(String name, String feature) {
        LifecycleCoordinator coordinator = createCandidate(name, feature);
        register(name, feature, 1, 10, 100.0);
        manager.resolveActualValue(feature, 10, 100.0);
        manager.promote(name, feature);
        return coordinator;
    }

    private void register(String name, String feature,
            int predictionNumber, int timestamp, double value) {
        manager.registerPrediction(new ShadowPrediction(
                name, feature, predictionNumber, timestamp, value));
    }
}