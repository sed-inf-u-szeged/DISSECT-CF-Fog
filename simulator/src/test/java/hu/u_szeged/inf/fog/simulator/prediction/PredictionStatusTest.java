package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class PredictionStatusTest {

    private Prediction parse(String statusJson) throws Exception {
        return JsonParser.fromJsonString(
                "{\"feature_name\":\"node::notYetProcessedData\","
                + "\"error_metrics\":{\"MAE\":2.0,\"MSE\":4.0,\"RMSE\":2.0},"
                + "\"prediction_status\":" + statusJson + "}",
                Prediction.class);
    }

    @Test
    void readsSeparateFutureAndTestStatuses() throws Exception {
        Prediction prediction = parse(
                "{\"future\":{\"model\":\"ARIMA\",\"phase\":\"future\","
                + "\"status\":\"not_converged\",\"converged\":false,"
                + "\"finite\":true,\"order\":[1,0,1]},"
                + "\"test\":{\"model\":\"ARIMA\",\"phase\":\"test\","
                + "\"status\":\"converged\",\"converged\":true,\"finite\":true}}");

        Prediction.ModelStatus future = prediction.getPredictionStatus().getFuture();
        Prediction.ModelStatus test = prediction.getPredictionStatus().getTest();

        assertEquals("ARIMA", future.getModel());
        assertEquals("future", future.getPhase());
        assertEquals("not_converged", future.getStatus());
        assertFalse(future.getConverged());
        assertTrue(future.getFinite());
        assertEquals(List.of(1, 0, 1), future.getOrder());
        assertEquals("test", test.getPhase());
        assertTrue(test.getConverged());
        assertEquals(2.0, prediction.getErrorMetrics().getMae(), 0.0);
    }

    @Test
    void acceptsUnavailableStatuses() throws Exception {
        Prediction prediction = parse("{\"future\":null,\"test\":null}");

        assertNotNull(prediction.getPredictionStatus());
        assertNull(prediction.getPredictionStatus().getFuture());
        assertNull(prediction.getPredictionStatus().getTest());
        assertNull(parse("null").getPredictionStatus());
    }

    @Test
    void acceptsOlderResponsesWithoutStatus() throws Exception {
        Prediction prediction = JsonParser.fromJsonString(
                "{\"feature_name\":\"node::notYetProcessedData\","
                + "\"prediction_future\":{\"timestamp\":[10],\"data\":[5.0]}}",
                Prediction.class);

        assertNull(prediction.getPredictionStatus());
        assertEquals(List.of(10), prediction.getPredictionFuture().getTimestamp());
        assertEquals(5.0,
                prediction.getPredictionFuture().getData().get(0), 0.0);
    }

    @Test
    void preservesUnknownConvergenceWhenWrittenAndRead() throws Exception {
        Prediction prediction = parse(
                "{\"future\":{\"model\":\"ARIMA\",\"status\":\"unknown\","
                + "\"converged\":null,\"finite\":true},\"test\":null}");

        JSONObject json = JsonParser.toJson(prediction, Prediction.class);
        assertTrue(json.has("prediction_status"));

        Prediction restored = JsonParser.fromJsonString(
                json.toString(), Prediction.class);

        assertEquals("unknown",
                restored.getPredictionStatus().getFuture().getStatus());
        assertNull(restored.getPredictionStatus().getFuture().getConverged());
        assertTrue(restored.getPredictionStatus().getFuture().getFinite());
        assertNull(restored.getPredictionStatus().getTest());
    }

    @Test
    void preservesNonConvergedStatusWhenWrittenAndRead() throws Exception {
        Prediction prediction = parse(
                "{\"future\":{\"model\":\"ARIMA\",\"status\":\"not_converged\","
                + "\"converged\":false,\"finite\":true,\"order\":[1,0,1]},"
                + "\"test\":null}");

        Prediction restored = JsonParser.fromJsonString(
                JsonParser.toJson(prediction, Prediction.class).toString(),
                Prediction.class);

        assertEquals("not_converged",
                restored.getPredictionStatus().getFuture().getStatus());
        assertFalse(restored.getPredictionStatus().getFuture().getConverged());
        assertEquals(List.of(1, 0, 1),
                restored.getPredictionStatus().getFuture().getOrder());
    }
}