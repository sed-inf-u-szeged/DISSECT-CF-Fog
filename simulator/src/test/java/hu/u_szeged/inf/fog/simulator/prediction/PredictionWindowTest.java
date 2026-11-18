package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import java.lang.reflect.Field;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class PredictionWindowTest {

    @Test
    void differentInputWindowsCanUseSameTargets() throws Exception {
        Prediction shortWindow = prediction("A", 372, 64, 2);
        Prediction longWindow = prediction("A", 244, 192, 2);

        assertTrue(PredictionWindow.areComparable(shortWindow, longWindow));
    }

    @Test
    void sameLocalTimesWithDifferentGlobalTimesAreRejected() throws Exception {
        Prediction first = prediction("A", 372, 64, 2);
        Prediction shifted = prediction("A", 373, 64, 2);

        assertFalse(PredictionWindow.areComparable(first, shifted));
    }

    @Test
    void differentFeaturesAreRejected() throws Exception {
        Prediction first = prediction("A", 372, 64, 2);
        Prediction second = prediction("B", 372, 64, 2);

        assertFalse(PredictionWindow.areComparable(first, second));
    }

    @Test
    void differentTargetCountsAreRejected() throws Exception {
        Prediction first = prediction("A", 372, 64, 2);
        Prediction second = prediction("A", 372, 64, 3);

        assertFalse(PredictionWindow.areComparable(first, second));
    }

    @Test
    void differentActualValuesAreRejected() throws Exception {
        Prediction first = prediction("A", 372, 64, 2);
        Prediction second = prediction("A", 244, 192, 2);
        second.getTestDataEnd().getData().set(1, 999.0);

        assertFalse(PredictionWindow.areComparable(first, second));
    }

    @Test
    void unknownWindowStartIsRejected() throws Exception {
        Prediction first = prediction("A", 372, 64, 2);
        Prediction unknown = prediction("A", -1, 64, 2);

        assertFalse(PredictionWindow.areComparable(first, unknown));
    }

    @Test
    void misalignedPredictionTimesAreRejected() throws Exception {
        Prediction first = prediction("A", 372, 64, 2);
        Prediction second = prediction("A", 244, 192, 2);
        second.getPredictionTest().getTimestamp().set(1, 194);

        assertFalse(PredictionWindow.areComparable(first, second));
    }

    @Test
    void missingTargetDataIsRejected() throws Exception {
        Prediction valid = prediction("A", 372, 64, 2);

        assertFalse(PredictionWindow.areComparable(valid, new Prediction()));
        assertFalse(PredictionWindow.areComparable(valid, null));
    }

    @Test
    void selectorChoosesLowerMaeOnMatchingWindow() throws Exception {
        Prediction baseline = candidate("Arima128", 372, 64, 3.0);
        Prediction lstm = candidate("LSTM256-v1", 244, 192, 1.0);

        assertSame(lstm, PredictionSelector.select(
                List.of(baseline, lstm), "Arima128").get("A"));
    }

    @Test
    void selectorRejectsLowerMaeOnDifferentWindow() throws Exception {
        Prediction baseline = candidate("Arima128", 372, 64, 3.0);
        Prediction shifted = candidate("LSTM256-v1", 245, 192, 0.1);

        // Baseline defines the comparison window even when listed second.
        assertSame(baseline, PredictionSelector.select(
                List.of(shifted, baseline), "Arima128").get("A"));
    }

    @Test
    void selectorCanWorkWhenBaselineIsUnavailable() throws Exception {
        Prediction first = candidate("LinearRegression", 372, 64, 3.0);
        Prediction better = candidate("LSTM256-v1", 244, 192, 1.0);

        assertSame(better, PredictionSelector.select(
                List.of(first, better), "Arima128").get("A"));
    }

    private Prediction prediction(String feature, int windowStart,
            int localStart, int count) throws Exception {
        JSONArray timestamps = new JSONArray();
        JSONArray actual = new JSONArray();
        JSONArray predicted = new JSONArray();

        for (int i = 0; i < count; i++) {
            timestamps.put(localStart + i);
            actual.put(100.0 + i);
            predicted.put(101.0 + i);
        }

        JSONObject json = new JSONObject()
                .put("feature_name", feature)
                .put("test_data_end", new JSONObject()
                        .put("timestamp", timestamps)
                        .put("data", actual))
                .put("prediction_test", new JSONObject()
                        .put("timestamp", timestamps)
                        .put("data", predicted));

        Prediction result = JsonParser.fromJsonObject(
                json, Prediction.class, null);
        result.setWindowStartTimestamp(windowStart);
        return result;
    }

    private Prediction candidate(String predictor, int windowStart,
            int localStart, double mae) throws Exception {
        Prediction result = prediction("A", windowStart, localStart, 2);
        result.setPredictionSettings(new PairPredictionSettings(predictor, null, null));

        Prediction.ErrorMetrics metrics = JsonParser.fromJsonObject(
                new JSONObject()
                        .put("MAE", mae)
                        .put("MSE", mae * mae)
                        .put("RMSE", mae),
                Prediction.ErrorMetrics.class, result);

        Field field = Prediction.class.getDeclaredField("errorMetrics");
        field.setAccessible(true);
        field.set(result, metrics);
        return result;
    }
}