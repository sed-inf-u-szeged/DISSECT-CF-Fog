package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.EvaluationMetrics;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.PairedShadowEvaluation;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class PairedShadowEvaluationTest {

    private Prediction prediction(int origin, int historyLength,
            double first, double second) throws Exception {
        JSONArray history = new JSONArray();
        for (int i = 0; i < historyLength; i++) {
            history.put(i);
        }

        JSONObject json = new JSONObject()
                .put("feature_name", "node")
                .put("window_start_timestamp", origin - historyLength)
                .put("original_data", new JSONObject().put("timestamp", history))
                .put("prediction_future", new JSONObject()
                        .put("timestamp", new JSONArray()
                                .put(historyLength).put(historyLength + 1))
                        .put("data", new JSONArray().put(first).put(second)))
                .put("prediction_status", new JSONObject()
                        .put("future", new JSONObject()
                                .put("model", "ARIMA")
                                .put("status", "not_converged")
                                .put("converged", false)
                                .put("finite", true)));

        return JsonParser.fromJsonString(json.toString(), Prediction.class);
    }

    private PairedShadowEvaluation comparison() throws Exception {
        return new PairedShadowEvaluation(
                "node", 3, "LSTM-v1", prediction(3, 3, 12, 18),
                "ARIMA", prediction(3, 2, 10, 30));
    }

    @Test
    void comparesDifferentInputLengthsOnIdenticalFutureTargets() throws Exception {
        PairedShadowEvaluation pair = comparison();

        assertFalse(pair.isComplete());
        assertTrue(pair.resolveActualValue("node", 3, 10));
        assertTrue(pair.resolveActualValue("node", 4, 20));
        assertTrue(pair.isComplete());

        EvaluationMetrics candidate = pair.getCandidateMetrics(0.2, 0.1);
        EvaluationMetrics baseline = pair.getBaselineMetrics(0.2, 0.1);

        assertEquals(2, candidate.getMatchedSamples());
        assertEquals(2, baseline.getMatchedSamples());
        assertEquals(2.0, candidate.getMae(), 1e-9);
        assertEquals(4.0, candidate.getMse(), 1e-9);
        assertEquals(100.0, candidate.getAccuracy(), 1e-9);
        assertEquals(5.0, baseline.getMae(), 1e-9);
        assertEquals(50.0, baseline.getMse(), 1e-9);
        assertEquals(50.0, baseline.getAccuracy(), 1e-9);
        assertFalse(pair.getBaselineStatus().getConverged());
    }

    @Test
    void rejectsDifferentPredictionOrigins() throws Exception {
        Prediction candidate = prediction(3, 3, 12, 18);
        Prediction baseline = prediction(4, 2, 10, 30);

        assertThrows(IllegalArgumentException.class, () ->
                new PairedShadowEvaluation(
                        "node", 3, "LSTM-v1", candidate, "ARIMA", baseline));
    }

    @Test
    void rejectsDifferentFutureTargets() throws Exception {
        Prediction candidate = prediction(3, 3, 12, 18);
        Prediction baseline = prediction(3, 2, 10, 30);
        baseline.getPredictionFuture().getTimestamp().set(1, 4);

        assertThrows(IllegalArgumentException.class, () ->
                new PairedShadowEvaluation(
                        "node", 3, "LSTM-v1", candidate, "ARIMA", baseline));
    }

    @Test
    void ignoresOtherFeaturesAndRepeatedActualValues() throws Exception {
        PairedShadowEvaluation pair = comparison();

        assertFalse(pair.resolveActualValue("other-node", 3, 10));
        assertFalse(pair.resolveActualValue("node", 99, 10));
        assertTrue(pair.resolveActualValue("node", 3, 10));
        assertFalse(pair.resolveActualValue("node", 3, 10));
        assertEquals(1, pair.getMatchedSamples());
        assertFalse(pair.isComplete());

        assertThrows(IllegalArgumentException.class, () ->
                pair.resolveActualValue("node", 3, 11));
    }

    @Test
    void rejectsInvalidPredictionsAndActualValues() throws Exception {
        Prediction candidate = prediction(3, 3, 12, 18);
        Prediction baseline = prediction(3, 2, 10, 30);
        candidate.getPredictionFuture().getData().set(0, Double.NaN);

        assertThrows(IllegalArgumentException.class, () ->
                new PairedShadowEvaluation(
                        "node", 3, "LSTM-v1", candidate, "ARIMA", baseline));

        PairedShadowEvaluation pair = comparison();
        assertThrows(IllegalArgumentException.class, () ->
                pair.resolveActualValue("node", 3, Double.NaN));
        assertEquals(0, pair.getMatchedSamples());
    }
}