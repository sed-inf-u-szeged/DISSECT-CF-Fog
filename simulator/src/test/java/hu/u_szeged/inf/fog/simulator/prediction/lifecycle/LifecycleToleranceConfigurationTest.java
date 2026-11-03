package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import hu.u_szeged.inf.fog.simulator.prediction.settings.LifecycleSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LifecycleToleranceConfigurationTest {

    @Test
    void configuredToleranceReachesLifecycleEvaluation() {
        LifecycleSettings settings = settings();
        settings.setAbsoluteTolerance(5.0);
        LifecycleCoordinator coordinator = candidate(settings);

        evaluate(coordinator, 1, 4, 0);

        assertEquals(5.0, settings.toPolicy().getAbsoluteTolerance(), 1e-9);
        assertEquals(100.0, coordinator.getLatestMetrics().getAccuracy(), 1e-9);
        assertEquals(LifecycleState.READY, coordinator.getState());
    }

    @Test
    void omittedTolerancePreservesLegacyBehavior() {
        LifecycleCoordinator coordinator = candidate(settings());

        evaluate(coordinator, 1, 4, 0);

        assertEquals(0.0, coordinator.getLatestMetrics().getAccuracy(), 1e-9);
        assertEquals(LifecycleState.EVALUATING, coordinator.getState());
    }

    @Test
    void explicitZeroIsNotTreatedAsMissing() {
        LifecycleSettings settings = settings();
        settings.setAbsoluteTolerance(0.0);
        LifecycleCoordinator coordinator = candidate(settings);

        evaluate(coordinator, 1, 0.05, 0);

        assertEquals(0.0, settings.toPolicy().getAbsoluteTolerance(), 1e-9);
        assertEquals(0.0, coordinator.getLatestMetrics().getAccuracy(), 1e-9);
        assertEquals(LifecycleState.EVALUATING, coordinator.getState());
    }

    @Test
    void activeModelUsesConfiguredToleranceForDegradation() {
        LifecycleSettings settings = settings();
        settings.setAbsoluteTolerance(5.0);
        LifecycleCoordinator coordinator = candidate(settings());

        // Use the configured settings for this candidate.
        coordinator = candidate(settings);
        evaluate(coordinator, 1, 4, 0);
        coordinator.activate();

        evaluate(coordinator, 2, 6, 0);

        assertEquals(6.0, coordinator.getLatestMetrics().getMae(), 1e-9);
        assertEquals(0.0, coordinator.getLatestMetrics().getAccuracy(), 1e-9);
        assertEquals(LifecycleState.DEGRADED, coordinator.getState());
    }

    @Test
    void legacyPolicyConstructorPreservesTolerance() {
        LifecyclePolicy policy = new LifecyclePolicy(
                1, 10, 100, 10, 200, 100, 0.1);

        assertEquals(0.1, policy.getAbsoluteTolerance(), 1e-9);
    }

    @Test
    void newSettingsConstructorPassesAbsoluteTolerance() {
        LifecycleSettings settings = new LifecycleSettings(
                true, "Arima128", 1, 10, 100, 10, 200, 100, 0.1, 5.0);

        assertEquals(5.0, settings.toPolicy().getAbsoluteTolerance(), 1e-9);
        assertEquals(0.1, settings.toPolicy().getAccuracyTolerance(), 1e-9);
    }

    private LifecycleSettings settings() {
        return new LifecycleSettings(
                true, "Arima128", 1, 10, 100, 10, 200, 100, 0.1);
    }

    private LifecycleCoordinator candidate(LifecycleSettings settings) {
        LifecycleCoordinator coordinator =
                new LifecycleCoordinator("LSTM256-v1", "node", settings.toPolicy());
        coordinator.startTraining();
        coordinator.startEvaluation();
        return coordinator;
    }

    private void evaluate(LifecycleCoordinator coordinator, int timestamp,
            double predicted, double actual) {
        coordinator.registerPrediction(new ShadowPrediction(
                "LSTM256-v1", "node", timestamp, timestamp, predicted));
        coordinator.resolveActualValue(timestamp, actual);
        coordinator.evaluate();
    }
}