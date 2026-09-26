package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

public enum LifecycleState {
    COLLECTING_DATA,
    TRAINING,
    EVALUATING,
    READY,
    ACTIVE,
    DEGRADED,
    RETRAINING,
    FAILED
}
