package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the lifecycle acceptance policy.
 */
public class LifecyclePolicyTest {

    private static final LifecyclePolicy POLICY = new LifecyclePolicy(
            10,
            2.0,
            4.0,
            2.0,
            15.0,
            80.0,
            0.10
    );

    /**
     * Verifies that metrics satisfying every requirement are accepted.
     */
    @Test
    public void acceptsMetricsWhenAllRequirementsAreSatisfied() {
        EvaluationMetrics metrics = new EvaluationMetrics(
                10,
                1.0,
                2.0,
                1.5,
                10.0,
                90.0
        );

        assertTrue(POLICY.isSatisfiedBy(metrics));
    }

    /**
     * Verifies that exceeding one error threshold rejects the metrics.
     */
    @Test
    public void rejectsMetricsWhenOneErrorThresholdIsExceeded() {
        EvaluationMetrics metrics = new EvaluationMetrics(
                10,
                1.0,
                2.0,
                2.1,
                10.0,
                90.0
        );

        assertFalse(POLICY.isSatisfiedBy(metrics));
    }

    /**
     * Verifies that evaluation is rejected when too few samples are matched.
     */
    @Test
    public void rejectsMetricsWhenMatchedSamplesAreInsufficient() {
        EvaluationMetrics metrics = new EvaluationMetrics(
                9,
                1.0,
                2.0,
                1.5,
                10.0,
                90.0
        );

        assertFalse(POLICY.isSatisfiedBy(metrics));
    }

    /**
     * Verifies that a missing evaluation result is rejected safely.
     */
    @Test
    public void rejectsNullMetrics() {
        assertFalse(POLICY.isSatisfiedBy(null));
    }
}