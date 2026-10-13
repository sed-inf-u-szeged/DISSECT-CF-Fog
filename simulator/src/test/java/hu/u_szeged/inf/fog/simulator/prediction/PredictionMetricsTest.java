package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class PredictionMetricsTest {

    @Test
    void selectsLowestMaeInsteadOfMixedMetrics() throws Exception {
        Prediction lowerMae = prediction("A", 1.0, 100.0, 10.0);
        Prediction lowerMixedScore = prediction("A", 2.0, 4.0, 2.0);

        assertSame(lowerMae, Prediction.getBestPrediction(
                List.of(lowerMixedScore, lowerMae)));
    }

    @Test
    void selectsBestPredictionForEachFeature() throws Exception {
        Prediction aBest = prediction("A", 1.0, 1.0, 1.0);
        Prediction aOther = prediction("A", 3.0, 9.0, 3.0);
        Prediction bBest = prediction("B", 2.0, 4.0, 2.0);
        Prediction bOther = prediction("B", 4.0, 16.0, 4.0);

        Map<String, Prediction> best = Prediction.getBestPredictions(
                List.of(aOther, bOther, aBest, bBest));

        assertEquals(2, best.size());
        assertSame(aBest, best.get("A"));
        assertSame(bBest, best.get("B"));
    }

    @Test
    void ignoresInvalidMaeAndMissingMetrics() throws Exception {
        Prediction valid = prediction("A", 2.0, 4.0, 2.0);
        Prediction negative = prediction("A", -1.0, 1.0, 1.0);
        Prediction nan = prediction("A", 1.0, 1.0, 1.0);
        Prediction infinite = prediction("A", 1.0, 1.0, 1.0);
        Prediction missing = JsonParser.fromJsonObject(
                new JSONObject().put("feature_name", "A"),
                Prediction.class, null);

        setMetric(nan.getErrorMetrics(), "mae", Double.NaN);
        setMetric(infinite.getErrorMetrics(), "mae", Double.POSITIVE_INFINITY);

        List<Prediction> candidates = Arrays.asList(
                null, missing, negative, nan, infinite, valid);

        assertSame(valid, Prediction.getBestPrediction(candidates));
        assertSame(valid, Prediction.getBestPredictions(candidates).get("A"));
    }

    @Test
    void omitsFeatureWithNoValidCandidate() throws Exception {
        Prediction invalid = prediction("A", -1.0, 1.0, 1.0);

        assertTrue(Prediction.getBestPredictions(List.of(invalid)).isEmpty());
    }

    @Test
    void reportsNoAverageBeforeAnyValidMetrics() {
        PairPredictionSettings settings = new PairPredictionSettings();

        assertNull(settings.getAverageMae());
        assertNull(settings.getAverageMse());
        assertNull(settings.getAverageRmse());
        assertEquals(0L, settings.getValidMetricCount());
        assertEquals(0L, settings.getInvalidMetricCount());
        assertEquals(0.0, settings.getNumberOfPredictions(), 0.0);
    }

    @Test
    void averagesEachMetricSeparatelyAndCountsOnce() throws Exception {
        PairPredictionSettings settings = new PairPredictionSettings();

        settings.recordPredictionMetrics(
                prediction("A", 2.0, 4.0, 2.0).getErrorMetrics());
        settings.recordPredictionMetrics(
                prediction("A", 4.0, 16.0, 4.0).getErrorMetrics());

        assertEquals(3.0, settings.getAverageMae(), 1e-9);
        assertEquals(10.0, settings.getAverageMse(), 1e-9);
        assertEquals(3.0, settings.getAverageRmse(), 1e-9);
        assertEquals(2L, settings.getValidMetricCount());
        assertEquals(0L, settings.getInvalidMetricCount());
        assertEquals(2.0, settings.getNumberOfPredictions(), 0.0);
    }

    @Test
    void invalidMetricsDoNotPolluteAverages() throws Exception {
        PairPredictionSettings settings = new PairPredictionSettings();
        Prediction invalid = prediction("A", 2.0, 4.0, 2.0);
        setMetric(invalid.getErrorMetrics(), "mse", Double.NaN);

        settings.recordPredictionMetrics(null);
        settings.recordPredictionMetrics(invalid.getErrorMetrics());

        assertNull(settings.getAverageMae());
        assertNull(settings.getAverageMse());
        assertNull(settings.getAverageRmse());

        settings.recordPredictionMetrics(
                prediction("A", 3.0, 9.0, 3.0).getErrorMetrics());

        assertEquals(3.0, settings.getAverageMae(), 1e-9);
        assertEquals(9.0, settings.getAverageMse(), 1e-9);
        assertEquals(3.0, settings.getAverageRmse(), 1e-9);
        assertEquals(1L, settings.getValidMetricCount());
        assertEquals(2L, settings.getInvalidMetricCount());
        assertEquals(3.0, settings.getNumberOfPredictions(), 0.0);
    }

    private Prediction prediction(String feature, double mae,
            double mse, double rmse) throws Exception {
        JSONObject metrics = new JSONObject()
                .put("MAE", mae)
                .put("MSE", mse)
                .put("RMSE", rmse);
        JSONObject json = new JSONObject()
                .put("feature_name", feature)
                .put("error_metrics", metrics);
        return JsonParser.fromJsonObject(json, Prediction.class, null);
    }

    private void setMetric(Prediction.ErrorMetrics metrics,
            String name, double value) throws Exception {
        Field field = Prediction.ErrorMetrics.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(metrics, value);
    }

        @Test
    void missingMaeCannotWinSelection() throws Exception {
        JSONObject json = new JSONObject()
                .put("feature_name", "A")
                .put("error_metrics", new JSONObject()
                        .put("MSE", 4.0)
                        .put("RMSE", 2.0));

        Prediction missingMae = JsonParser.fromJsonObject(
                json, Prediction.class, null);
        Prediction valid = prediction("A", 2.0, 4.0, 2.0);

        assertTrue(Double.isNaN(missingMae.getErrorMetrics().getMae()));
        assertSame(valid, Prediction.getBestPrediction(
                List.of(missingMae, valid)));

        PairPredictionSettings settings = new PairPredictionSettings();
        settings.recordPredictionMetrics(missingMae.getErrorMetrics());

        assertEquals(0L, settings.getValidMetricCount());
        assertEquals(1L, settings.getInvalidMetricCount());
        assertNull(settings.getAverageMae());
    }

    @Test
    void genuineZeroErrorsRemainValid() throws Exception {
        Prediction perfect = prediction("A", 0.0, 0.0, 0.0);
        Prediction other = prediction("A", 1.0, 1.0, 1.0);

        assertSame(perfect, Prediction.getBestPrediction(
                List.of(other, perfect)));

        PairPredictionSettings settings = new PairPredictionSettings();
        settings.recordPredictionMetrics(perfect.getErrorMetrics());

        assertEquals(1L, settings.getValidMetricCount());
        assertEquals(0L, settings.getInvalidMetricCount());
        assertEquals(0.0, settings.getAverageMae(), 0.0);
        assertEquals(0.0, settings.getAverageMse(), 0.0);
        assertEquals(0.0, settings.getAverageRmse(), 0.0);
    }
}