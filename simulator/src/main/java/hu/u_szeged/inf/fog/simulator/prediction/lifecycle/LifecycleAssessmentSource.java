package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

/** Supplies causally matched, domain-specific metrics to the shared state machine. */
public interface LifecycleAssessmentSource {
    LifecycleAssessment assess();

    /** Discards this version's observations when explicitly starting retraining. */
    void clear();
}
