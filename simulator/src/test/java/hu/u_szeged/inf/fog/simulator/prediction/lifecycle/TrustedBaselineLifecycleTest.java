package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrustedBaselineLifecycleTest {

    private final LifecycleManager manager = LifecycleManager.getInstance();
    private final LifecyclePolicy policy =
            new LifecyclePolicy(1, 0, 0, 0, 0, 100, 0.1);

    @BeforeEach
    void setUp() {
        manager.reset();
    }

    @AfterEach
    void tearDown() {
        manager.reset();
    }

    @Test
    void baselineKeepsServingAndContinuesReportingBadMetrics() {
        LifecycleCoordinator baseline = createBaseline();

        resolve("baseline", 1, 200, 100);

        assertEquals(LifecycleState.ACTIVE, baseline.getState());
        assertTrue(manager.isActive("baseline", "node"));
        assertFalse(baseline.isPolicySatisfied());
        assertEquals(100.0, baseline.getLatestMetrics().getMae(), 1e-9);

        resolve("baseline", 2, 400, 100);

        assertEquals(LifecycleState.ACTIVE, baseline.getState());
        assertEquals(300.0, baseline.getLatestMetrics().getMae(), 1e-9);
    }

    @Test
    void degradedCandidateRestoresBaselineDespiteItsEarlierBadMetrics() {
        LifecycleCoordinator baseline = createBaseline();
        resolve("baseline", 1, 200, 100);

        LifecycleCoordinator candidate =
                new LifecycleCoordinator("LSTM256-v1", "node", policy);
        candidate.setAutomaticPromotionAllowed(false);
        manager.registerCoordinator(candidate);
        candidate.startTraining();
        candidate.startEvaluation();

        resolve("LSTM256-v1", 2, 100, 100);
        assertEquals(LifecycleState.READY, candidate.getState());

        manager.promote("LSTM256-v1", "node");
        assertEquals(LifecycleState.READY, baseline.getState());

        resolve("LSTM256-v1", 3, 200, 100);

        assertEquals(LifecycleState.DEGRADED, candidate.getState());
        assertEquals(LifecycleState.ACTIVE, baseline.getState());
        assertEquals("baseline", manager.getActivePredictorName("node"));
        assertTrue(manager.isActive("baseline", "node"));
        assertFalse(manager.isActive("LSTM256-v1", "node"));

        resolve("baseline", 4, 300, 100);
        assertEquals(LifecycleState.ACTIVE, baseline.getState());
        assertFalse(baseline.isPolicySatisfied());
        assertEquals(200.0, baseline.getLatestMetrics().getMae(), 1e-9);
    }

    @Test
    void technicalFailureStillMarksBaselineFailed() {
        LifecycleCoordinator baseline = createBaseline();

        baseline.markFailed();

        assertEquals(LifecycleState.FAILED, baseline.getState());
        assertThrows(IllegalStateException.class, baseline::evaluate);
    }

    private LifecycleCoordinator createBaseline() {
        LifecycleCoordinator baseline =
                new LifecycleCoordinator("baseline", "node", policy);
        manager.registerCoordinator(baseline);
        manager.activateBaseline("baseline", "node");
        return baseline;
    }

    private void resolve(String predictor, int timestamp,
                         double predicted, double actual) {
        manager.registerPrediction(new ShadowPrediction(
                predictor, "node", timestamp, timestamp, predicted));
        manager.resolveActualValue("node", timestamp, actual);
    }
}