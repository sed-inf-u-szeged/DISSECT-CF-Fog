package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LifecycleActivationOwnershipTest {

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
    void controlledCandidateWaitsForExplicitPromotion() {
        LifecycleCoordinator candidate = createCandidate(false);

        resolve(100.0);

        assertEquals(LifecycleState.READY, candidate.getState());
        assertFalse(manager.isActive("candidate", "node::load"));

        manager.promote("candidate", "node::load");

        assertEquals(LifecycleState.ACTIVE, candidate.getState());
        assertTrue(manager.isActive("candidate", "node::load"));
    }

    @Test
    void ordinaryCandidateStillPromotesAutomatically() {
        LifecycleCoordinator candidate = createCandidate(true);

        resolve(100.0);

        assertEquals(LifecycleState.ACTIVE, candidate.getState());
        assertTrue(manager.isActive("candidate", "node::load"));
    }

    @Test
    void controlledCandidateStillEvaluatesAndRejectsBadPrediction() {
        LifecycleCoordinator candidate = createCandidate(false);

        resolve(200.0);

        assertTrue(candidate.hasCompleteEvaluationWindow());
        assertFalse(candidate.isPolicySatisfied());
        assertEquals(LifecycleState.EVALUATING, candidate.getState());
        assertFalse(manager.isActive("candidate", "node::load"));
    }

    @Test
    void degradedControlledCandidateImmediatelyRestoresItsBaseline() {
        LifecyclePolicy policy =
                new LifecyclePolicy(1, 1.0, 1.0, 1.0, 5.0, 100.0, 0.1);
        LifecycleCoordinator baseline =
                new LifecycleCoordinator("baseline", "node::load", policy);
        LifecycleCoordinator otherBaseline =
                new LifecycleCoordinator("baseline", "other::load", policy);

        manager.registerCoordinator(baseline);
        manager.registerCoordinator(otherBaseline);
        manager.activateBaseline("baseline", "node::load");
        manager.activateBaseline("baseline", "other::load");

        LifecycleCoordinator candidate = createCandidate(false);
        resolve(100.0);
        manager.promote("candidate", "node::load");
        assertTrue(manager.isActive("candidate", "node::load"));

        manager.registerPrediction(
                new ShadowPrediction("candidate", "node::load", 2, 11, 100.0));
        manager.resolveActualValue("node::load", 11, 200.0);

        assertEquals(LifecycleState.DEGRADED, candidate.getState());
        assertFalse(manager.isActive("candidate", "node::load"));
        assertTrue(manager.isActive("baseline", "node::load"));
        assertEquals(LifecycleState.ACTIVE, baseline.getState());
        assertTrue(manager.isActive("baseline", "other::load"));
    }

    private LifecycleCoordinator createCandidate(boolean automatic) {
        LifecyclePolicy policy =
                new LifecyclePolicy(1, 1.0, 1.0, 1.0, 5.0, 100.0, 0.1);
        LifecycleCoordinator candidate =
                new LifecycleCoordinator("candidate", "node::load", policy);

        if (!automatic) {
            candidate.setAutomaticPromotionAllowed(false);
        }
        manager.registerCoordinator(candidate);
        candidate.startTraining();
        candidate.startEvaluation();
        return candidate;
    }

    private void resolve(double actual) {
        manager.registerPrediction(
                new ShadowPrediction("candidate", "node::load", 1, 10, 100.0));
        manager.resolveActualValue("node::load", 10, actual);
    }
}