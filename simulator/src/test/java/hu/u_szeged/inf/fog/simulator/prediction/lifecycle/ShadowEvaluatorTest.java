package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowEvaluatorTest {

    @Test
    void shouldResolveMatchingPredictions() {
        ShadowEvaluator evaluator = new ShadowEvaluator();

        ShadowPrediction firstPrediction = new ShadowPrediction(
                "linear-regression",
                "cpu",
                1,
                1000,
                100.0
        );

        ShadowPrediction secondPrediction = new ShadowPrediction(
                "lstm",
                "cpu",
                1,
                1000,
                105.0
        );

        ShadowPrediction differentTimestamp = new ShadowPrediction(
                "linear-regression",
                "cpu",
                2,
                2000,
                110.0
        );

        evaluator.registerPrediction(firstPrediction);
        evaluator.registerPrediction(secondPrediction);
        evaluator.registerPrediction(differentTimestamp);

        int resolvedCount =
                evaluator.resolveActualValue("cpu", 1000, 98.0);

        assertEquals(2, resolvedCount);
        assertTrue(firstPrediction.isResolved());
        assertTrue(secondPrediction.isResolved());
        assertFalse(differentTimestamp.isResolved());
    }

    @Test
    void shouldCalculateMetricsFromResolvedPredictions() {
        ShadowEvaluator evaluator = new ShadowEvaluator();

        evaluator.registerPrediction(new ShadowPrediction(
                "linear-regression",
                "cpu",
                1,
                1000,
                100.0
        ));

        evaluator.registerPrediction(new ShadowPrediction(
                "linear-regression",
                "cpu",
                2,
                2000,
                50.0
        ));

        evaluator.resolveActualValue("cpu", 1000, 100.0);
        evaluator.resolveActualValue("cpu", 2000, 100.0);

        EvaluationMetrics metrics = evaluator.calculateMetrics(
                "linear-regression",
                "cpu",
                0.10
        );

        assertEquals(2, metrics.getMatchedSamples());
        assertEquals(25.0, metrics.getMae(), 0.0001);
        assertEquals(1250.0, metrics.getMse(), 0.0001);
        assertEquals(Math.sqrt(1250.0), metrics.getRmse(), 0.0001);
        assertEquals(33.3333, metrics.getSmape(), 0.0001);
        assertEquals(50.0, metrics.getAccuracy(), 0.0001);
    }

    @Test
    void shouldReturnZeroMetricsWhenNoPredictionsAreResolved() {
        ShadowEvaluator evaluator = new ShadowEvaluator();

        EvaluationMetrics metrics = evaluator.calculateMetrics(
                "linear-regression",
                "cpu",
                0.10
        );

        assertEquals(0, metrics.getMatchedSamples());
        assertEquals(0.0, metrics.getMae());
        assertEquals(0.0, metrics.getMse());
        assertEquals(0.0, metrics.getRmse());
        assertEquals(0.0, metrics.getSmape());
        assertEquals(0.0, metrics.getAccuracy());
    }

    @Test
    void shouldRejectInvalidInput() {
        ShadowEvaluator evaluator = new ShadowEvaluator();

        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.registerPrediction(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.calculateMetrics(
                        "linear-regression",
                        "cpu",
                        -0.10
                )
        );
    }
}