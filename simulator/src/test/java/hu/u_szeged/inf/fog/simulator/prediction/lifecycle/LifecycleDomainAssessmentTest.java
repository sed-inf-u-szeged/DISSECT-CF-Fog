package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LifecycleDomainAssessmentTest {
    static final class Source implements LifecycleAssessmentSource {
        int count;
        boolean pass;
        public LifecycleAssessment assess() {
            return new LifecycleAssessment(new EvaluationMetrics(count, 2, 4, 2, Double.NaN, Double.NaN),
                    pass, "POSITION_ERROR_METRES", "TEST_ASSESSMENT");
        }
        public void clear() { count = 0; pass = false; }
    }

    private LifecycleCoordinator coordinator(Source source) {
        // Intentionally impossible scalar thresholds: domain decisions do not use load percentages.
        return new LifecycleCoordinator("candidate", "position", new LifecyclePolicy(64, 0, 0, 0, 0, 100, .2), source);
    }

    @Test
    void cannotBypassMinimumAndUsesSharedReadyActiveDegradedTransitions() {
        Source source = new Source();
        LifecycleCoordinator coordinator = coordinator(source);
        coordinator.startTraining(); coordinator.startEvaluation();
        source.count = 63; source.pass = true;
        coordinator.evaluate();
        assertFalse(coordinator.isPolicySatisfied());
        assertEquals(LifecycleState.EVALUATING, coordinator.getState());
        source.count = 64;
        coordinator.evaluate();
        assertEquals(LifecycleState.READY, coordinator.getState());
        assertTrue(Double.isNaN(coordinator.getLatestMetrics().getAccuracy()));
        coordinator.activate();
        source.pass = false;
        assertTrue(coordinator.isPolicySatisfied(), "Decisions are snapshots until explicitly evaluated");
        coordinator.evaluate();
        assertEquals(LifecycleState.DEGRADED, coordinator.getState());
        coordinator.startRetraining();
        assertEquals(0, source.count);
        assertNull(coordinator.getLatestMetrics());
        assertFalse(coordinator.isPolicySatisfied());
    }

    @Test
    void scalarCallsAndImplicitPromotionAreRejectedForDomainSources() {
        LifecycleCoordinator coordinator = coordinator(new Source());
        coordinator.startTraining(); coordinator.startEvaluation();
        assertFalse(coordinator.isAutomaticPromotionAllowed());
        assertThrows(IllegalStateException.class, () -> coordinator.setAutomaticPromotionAllowed(true));
        assertThrows(IllegalStateException.class, () -> coordinator.resolveActualValue(1, 1));
        assertThrows(IllegalStateException.class, () -> coordinator.registerPrediction(new ShadowPrediction("candidate", "position", 1, 1, 1)));
    }

    @Test
    void invalidDomainMetricsFailBeforeChangingState() {
        assertThrows(IllegalArgumentException.class, () -> new LifecycleAssessment(
                new EvaluationMetrics(64, Double.NaN, 0, 0, Double.NaN, Double.NaN), true, "position", "bad"));
        assertThrows(IllegalArgumentException.class, () -> new LifecycleAssessment(
                new EvaluationMetrics(-1, 0, 0, 0, Double.NaN, Double.NaN), true, "position", "bad"));
        LifecycleCoordinator coordinator = new LifecycleCoordinator("candidate", "position",
                new LifecyclePolicy(64, 1, 1, 1, 1, 1, .2), new LifecycleAssessmentSource() {
                    public LifecycleAssessment assess() { return null; }
                    public void clear() { }
                });
        coordinator.startTraining(); coordinator.startEvaluation();
        assertThrows(IllegalStateException.class, coordinator::evaluate);
        assertNull(coordinator.getLatestMetrics());
        assertEquals(LifecycleState.EVALUATING, coordinator.getState());
    }
}
