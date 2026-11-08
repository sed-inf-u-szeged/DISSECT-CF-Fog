package hu.u_szeged.inf.fog.simulator.prediction.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShadowEvaluatorWindowTest {

    @Test
    void windowUsesTargetTimeInsteadOfRegistrationOrder() {
        ShadowEvaluator evaluator = new ShadowEvaluator();

        addResolved(evaluator, 1, 100, 100);
        addResolved(evaluator, 1, 101, 100);
        addResolved(evaluator, 1, 102, 100);

        // An older target is registered later and must not replace newer data.
        addResolved(evaluator, 2, 99, 200);

        EvaluationMetrics metrics = evaluator.calculateMetrics(
                "LSTM256-v1", "node", 0.1, 2);

        assertEquals(2, metrics.getMatchedSamples());
        assertEquals(0.0, metrics.getMae(), 1e-9);
        assertEquals(100.0, metrics.getAccuracy(), 1e-9);
    }

    @Test
    void newerPredictionNumberBreaksTiesForSameTargetTime() {
        ShadowEvaluator evaluator = new ShadowEvaluator();

        addResolved(evaluator, 2, 100, 100);
        addResolved(evaluator, 1, 100, 200);

        EvaluationMetrics metrics = evaluator.calculateMetrics(
                "LSTM256-v1", "node", 0.1, 1);

        assertEquals(1, metrics.getMatchedSamples());
        assertEquals(0.0, metrics.getMae(), 1e-9);
    }

    @Test
    void unresolvedFuturePredictionDoesNotEnterWindow() {
        ShadowEvaluator evaluator = new ShadowEvaluator();

        addResolved(evaluator, 1, 100, 100);
        evaluator.registerPrediction(new ShadowPrediction(
                "LSTM256-v1", "node", 2, 200, 500));

        EvaluationMetrics metrics = evaluator.calculateMetrics(
                "LSTM256-v1", "node", 0.1, 1);

        assertEquals(1, metrics.getMatchedSamples());
        assertEquals(0.0, metrics.getMae(), 1e-9);
    }

    @Test
    void repeatedTargetTimesRemainSeparatePredictionRecords() {
        ShadowEvaluator evaluator = new ShadowEvaluator();

        addResolved(evaluator, 1, 100, 100);
        addResolved(evaluator, 2, 100, 120);

        EvaluationMetrics metrics = evaluator.calculateMetrics(
                "LSTM256-v1", "node", 0.1, 64);

        assertEquals(2, metrics.getMatchedSamples());
        assertEquals(10.0, metrics.getMae(), 1e-9);
        assertEquals(200.0, metrics.getMse(), 1e-9);
        assertEquals(Math.sqrt(200.0), metrics.getRmse(), 1e-9);
        assertEquals(100.0 / 11.0, metrics.getSmape(), 1e-9);
        assertEquals(50.0, metrics.getAccuracy(), 1e-9);
    }

    private void addResolved(ShadowEvaluator evaluator, int predictionNumber,
                             int targetTimestamp, double predictedValue) {
        ShadowPrediction prediction = new ShadowPrediction(
                "LSTM256-v1", "node", predictionNumber,
                targetTimestamp, predictedValue);
        prediction.resolve(100.0);
        evaluator.registerPrediction(prediction);
    }
}