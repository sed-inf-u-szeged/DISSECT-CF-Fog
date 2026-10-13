package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShadowEvaluatorToleranceTest {

    @Test
    void absoluteToleranceHandlesZeroAndSmallActualValues() {
        ShadowEvaluator evaluator = new ShadowEvaluator();
        add(evaluator, 1, 4, 0);
        add(evaluator, 2, 24, 20);

        EvaluationMetrics metrics =
                evaluator.calculateMetrics("model", "node", 0.1, 5.0, 64);

        assertEquals(2, metrics.getMatchedSamples());
        assertEquals(100.0, metrics.getAccuracy(), 1e-9);
        assertEquals(4.0, metrics.getMae(), 1e-9);
        assertEquals(16.0, metrics.getMse(), 1e-9);
        assertEquals(4.0, metrics.getRmse(), 1e-9);
    }

    @Test
    void relativeToleranceHandlesLargerActualValues() {
        ShadowEvaluator evaluator = new ShadowEvaluator();
        add(evaluator, 1, 109, 100);
        add(evaluator, 2, 111, 100);

        EvaluationMetrics metrics =
                evaluator.calculateMetrics("model", "node", 0.1, 5.0, 64);

        assertEquals(50.0, metrics.getAccuracy(), 1e-9);
        assertEquals(10.0, metrics.getMae(), 1e-9);
    }

    @Test
    void errorAtToleranceBoundaryIsAccepted() {
        ShadowEvaluator evaluator = new ShadowEvaluator();
        add(evaluator, 1, 5, 0);
        add(evaluator, 2, 110, 100);

        assertEquals(100.0,
                evaluator.calculateMetrics("model", "node", 0.1, 5.0, 64)
                        .getAccuracy(), 1e-9);
    }

    @Test
    void legacyInterfacePreservesItsOriginalTolerance() {
        ShadowEvaluator evaluator = new ShadowEvaluator();
        add(evaluator, 1, 0.05, 0);
        add(evaluator, 2, 0.2, 0);
        add(evaluator, 3, 109, 100);

        EvaluationMetrics metrics =
                evaluator.calculateMetrics("model", "node", 0.1, 64);

        assertEquals(200.0 / 3.0, metrics.getAccuracy(), 1e-9);
    }

    @Test
    void changingToleranceDoesNotChangeErrorMetrics() {
        ShadowEvaluator evaluator = new ShadowEvaluator();
        add(evaluator, 1, 4, 0);

        EvaluationMetrics strict =
                evaluator.calculateMetrics("model", "node", 0.1, 0.1, 64);
        EvaluationMetrics relaxed =
                evaluator.calculateMetrics("model", "node", 0.1, 5.0, 64);

        assertEquals(0.0, strict.getAccuracy(), 1e-9);
        assertEquals(100.0, relaxed.getAccuracy(), 1e-9);
        assertEquals(strict.getMae(), relaxed.getMae(), 1e-9);
        assertEquals(strict.getMse(), relaxed.getMse(), 1e-9);
        assertEquals(strict.getRmse(), relaxed.getRmse(), 1e-9);
        assertEquals(strict.getSmape(), relaxed.getSmape(), 1e-9);
    }

    @Test
    void zeroTolerancesRequireExactPredictions() {
        ShadowEvaluator evaluator = new ShadowEvaluator();
        add(evaluator, 1, 0, 0);
        add(evaluator, 2, 0.01, 0);

        assertEquals(50.0,
                evaluator.calculateMetrics("model", "node", 0.0, 0.0, 64)
                        .getAccuracy(), 1e-9);
    }

    @Test
    void invalidTolerancesAreRejected() {
        ShadowEvaluator evaluator = new ShadowEvaluator();
        double[] invalid = {-1, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        for (double value : invalid) {
            assertThrows(IllegalArgumentException.class,
                    () -> evaluator.calculateMetrics(
                            "model", "node", value, 5.0, 64));
            assertThrows(IllegalArgumentException.class,
                    () -> evaluator.calculateMetrics(
                            "model", "node", 0.1, value, 64));
        }
    }

    private void add(ShadowEvaluator evaluator, int timestamp,
            double predicted, double actual) {
        evaluator.registerPrediction(new ShadowPrediction(
                "model", "node", timestamp, timestamp, predicted));
        evaluator.resolveActualValue("node", timestamp, actual);
    }
}