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
                115.0
        ));

        int resolvedCount = manager.resolveActualValue(
                "fog-1::cpu",
                64,
                100.0
        );

        EvaluationMetrics lstmMetrics =
                lstm.getLatestMetrics();

        EvaluationMetrics gruMetrics =
                gru.getLatestMetrics();

        assertEquals(2, resolvedCount);
        assertEquals(1, lstmMetrics.getMatchedSamples());
        assertEquals(1, gruMetrics.getMatchedSamples());
        assertEquals(0.0, lstmMetrics.getMae());
        assertEquals(15.0, gruMetrics.getMae());

        assertEquals(LifecycleState.ACTIVE, lstm.getState());
        assertEquals(LifecycleState.EVALUATING, gru.getState());
        assertEquals(
                "lstm",
                manager.getActivePredictorName("fog-1::cpu")
        );
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
    void shouldEvaluateActiveBaselinePrediction() {
        LifecycleCoordinator baseline =
                createCoordinator("baseline", "fog-1::cpu");

        manager.registerCoordinator(baseline);
        manager.activateBaseline("baseline", "fog-1::cpu");

        boolean registered =
                manager.registerPredictionIfEvaluating(
                        new ShadowPrediction(
                                "baseline",
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
        assertEquals(
                1,
                baseline.getLatestMetrics().getMatchedSamples()
        );
        assertEquals(0.0, baseline.getLatestMetrics().getMae());
        assertEquals(LifecycleState.ACTIVE, baseline.getState());
    }

    @Test
    void shouldCompareCandidateWithUpdatedBaselineMetrics() {
        LifecycleCoordinator baseline =
                createCoordinator("baseline", "fog-1::cpu");

        LifecycleCoordinator candidate =
                createCoordinator("candidate", "fog-1::cpu");

        manager.registerCoordinator(baseline);
        manager.registerCoordinator(candidate);
        manager.activateBaseline("baseline", "fog-1::cpu");
        startEvaluation(candidate);

        manager.registerPredictionIfEvaluating(
                new ShadowPrediction(
                        "baseline",
                        "fog-1::cpu",
                        1,
                        64,
                        100.0
                )
        );

        manager.registerPredictionIfEvaluating(
                new ShadowPrediction(
                        "candidate",
                        "fog-1::cpu",
                        1,
                        64,
                        105.0
                )
        );

        manager.resolveActualValue(
                "fog-1::cpu",
                64,
                100.0
        );

        assertEquals(LifecycleState.ACTIVE, baseline.getState());
        assertEquals(LifecycleState.READY, candidate.getState());
        assertEquals(
                "baseline",
                manager.getActivePredictorName("fog-1::cpu")
        );
        assertEquals(0.0, baseline.getLatestMetrics().getMae());
        assertEquals(5.0, candidate.getLatestMetrics().getMae());
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

    @Test
    void shouldPromoteReadyPredictor() {
        LifecycleCoordinator candidate =
                createCoordinator("candidate", "fog-1::cpu");

        manager.registerCoordinator(candidate);
        moveToReady(candidate);

        manager.promote("candidate", "fog-1::cpu");

        assertEquals(LifecycleState.ACTIVE, candidate.getState());
        assertEquals(
                "candidate",
                manager.getActivePredictorName("fog-1::cpu")
        );
        assertTrue(manager.isActive("candidate", "fog-1::cpu"));
    }

    @Test
    void shouldReplaceActivePredictor() {
        LifecycleCoordinator baseline =
                createCoordinator("baseline", "fog-1::cpu");

        LifecycleCoordinator candidate =
                createCoordinator("candidate", "fog-1::cpu");

        manager.registerCoordinator(baseline);
        manager.registerCoordinator(candidate);

        moveToReady(baseline);
        moveToReady(candidate);

        manager.promote("baseline", "fog-1::cpu");
        manager.promote("candidate", "fog-1::cpu");

        assertEquals(LifecycleState.READY, baseline.getState());
        assertEquals(LifecycleState.ACTIVE, candidate.getState());
        assertEquals(
                "candidate",
                manager.getActivePredictorName("fog-1::cpu")
        );
        assertFalse(manager.isActive("baseline", "fog-1::cpu"));
        assertTrue(manager.isActive("candidate", "fog-1::cpu"));
    }

    @Test
    void shouldKeepBaselineActiveWhenCandidateIsNotReady() {
        LifecycleCoordinator baseline =
                createCoordinator("baseline", "fog-1::cpu");

        LifecycleCoordinator candidate =
                createCoordinator("candidate", "fog-1::cpu");

        manager.registerCoordinator(baseline);
        manager.registerCoordinator(candidate);

        manager.activateBaseline("baseline", "fog-1::cpu");

        assertThrows(
                IllegalStateException.class,
                () -> manager.promote("candidate", "fog-1::cpu")
        );

        assertEquals(LifecycleState.ACTIVE, baseline.getState());
        assertEquals(
                LifecycleState.COLLECTING_DATA,
                candidate.getState()
        );
        assertEquals(
                "baseline",
                manager.getActivePredictorName("fog-1::cpu")
        );
    }

    @Test
    void shouldPromoteBetterCandidateOverCurrentActivePredictor() {
        LifecycleCoordinator baseline =
                createCoordinator("baseline", "fog-1::cpu");

        LifecycleCoordinator worseCandidate =
                createCoordinator("worse", "fog-1::cpu");

        LifecycleCoordinator betterCandidate =
                createCoordinator("better", "fog-1::cpu");

        manager.registerCoordinator(baseline);
        manager.registerCoordinator(worseCandidate);
        manager.registerCoordinator(betterCandidate);

        manager.activateBaseline("baseline", "fog-1::cpu");

        moveToReady(worseCandidate, 105.0, 100.0);

        assertTrue(
                manager.promoteIfBetter("worse", "fog-1::cpu")
        );

        moveToReady(betterCandidate, 101.0, 100.0);

        assertTrue(
                manager.promoteIfBetter("better", "fog-1::cpu")
        );

        assertEquals(
                LifecycleState.READY,
                worseCandidate.getState()
        );
        assertEquals(
                LifecycleState.ACTIVE,
                betterCandidate.getState()
        );
        assertEquals(
                "better",
                manager.getActivePredictorName("fog-1::cpu")
        );
    }

    @Test
    void shouldKeepCurrentActivePredictorWhenCandidateIsWorse() {
        LifecycleCoordinator baseline =
                createCoordinator("baseline", "fog-1::cpu");

        LifecycleCoordinator betterCandidate =
                createCoordinator("better", "fog-1::cpu");

        LifecycleCoordinator worseCandidate =
                createCoordinator("worse", "fog-1::cpu");

        manager.registerCoordinator(baseline);
        manager.registerCoordinator(betterCandidate);
        manager.registerCoordinator(worseCandidate);

        manager.activateBaseline("baseline", "fog-1::cpu");

        moveToReady(betterCandidate, 101.0, 100.0);

        assertTrue(
                manager.promoteIfBetter("better", "fog-1::cpu")
        );

        moveToReady(worseCandidate, 105.0, 100.0);

        assertFalse(
                manager.promoteIfBetter("worse", "fog-1::cpu")
        );

        assertEquals(
                LifecycleState.ACTIVE,
                betterCandidate.getState()
        );
        assertEquals(
                LifecycleState.READY,
                worseCandidate.getState()
        );
        assertEquals(
                "better",
                manager.getActivePredictorName("fog-1::cpu")
        );
    }

    @Test
    void shouldUsePredictorNameForDeterministicTieBreak() {
        LifecycleCoordinator baseline =
                createCoordinator("baseline", "fog-1::cpu");

        LifecycleCoordinator zetaCandidate =
                createCoordinator("zeta", "fog-1::cpu");

        LifecycleCoordinator alphaCandidate =
                createCoordinator("alpha", "fog-1::cpu");

        manager.registerCoordinator(baseline);
        manager.registerCoordinator(zetaCandidate);
        manager.registerCoordinator(alphaCandidate);

        manager.activateBaseline("baseline", "fog-1::cpu");

        moveToReady(zetaCandidate, 100.0, 100.0);

        assertTrue(
                manager.promoteIfBetter("zeta", "fog-1::cpu")
        );

        moveToReady(alphaCandidate, 100.0, 100.0);

        assertTrue(
                manager.promoteIfBetter("alpha", "fog-1::cpu")
        );

        assertEquals(
                LifecycleState.READY,
                zetaCandidate.getState()
        );
        assertEquals(
                LifecycleState.ACTIVE,
                alphaCandidate.getState()
        );
        assertEquals(
                "alpha",
                manager.getActivePredictorName("fog-1::cpu")
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

    private void moveToReady(
            LifecycleCoordinator coordinator
    ) {
        moveToReady(coordinator, 100.0, 100.0);
    }

    private void moveToReady(
            LifecycleCoordinator coordinator,
            double predictedValue,
            double actualValue
    ) {
        startEvaluation(coordinator);

        coordinator.registerPrediction(new ShadowPrediction(
                coordinator.getPredictorName(),
                coordinator.getFeatureName(),
                1,
                64,
                predictedValue
        ));

        coordinator.resolveActualValue(64, actualValue);
        coordinator.evaluate();
    }
}