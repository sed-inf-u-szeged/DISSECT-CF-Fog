package hu.u_szeged.inf.fog.simulator.application.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.prediction.Prediction;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class PliantPredictionAverageTest {

    @Test
    void shortOutputUsesAvailableValues() throws Exception {
        Prediction prediction = prediction(2.0, 4.0, 6.0);

        assertEquals(4.0, PliantApplicationStrategy
                .averageFuture(prediction, 10).orElseThrow(), 1e-9);
    }

    @Test
    void longOutputUsesOnlyFirstTenValues() throws Exception {
        Prediction prediction = prediction(
                1.0, 2.0, 3.0, 4.0, 5.0, 6.0,
                7.0, 8.0, 9.0, 10.0, 1000.0);

        assertEquals(5.5, PliantApplicationStrategy
                .averageFuture(prediction, 10).orElseThrow(), 1e-9);
    }

    @Test
    void emptyOrMissingOutputIsUnavailable() throws Exception {
        assertTrue(PliantApplicationStrategy.averageFuture(prediction(), 10).isEmpty());
        assertTrue(PliantApplicationStrategy.averageFuture(new Prediction(), 10).isEmpty());
        assertTrue(PliantApplicationStrategy.averageFuture(null, 10).isEmpty());
    }

    @Test
    void invalidValuesAreUnavailable() throws Exception {
        Prediction prediction = prediction(1.0, 2.0);
        prediction.getPredictionFuture().getData().set(1, Double.NaN);
        assertTrue(PliantApplicationStrategy.averageFuture(prediction, 10).isEmpty());

        prediction.getPredictionFuture().getData().set(1, Double.POSITIVE_INFINITY);
        assertTrue(PliantApplicationStrategy.averageFuture(prediction, 10).isEmpty());

        prediction.getPredictionFuture().getData().set(1, null);
        assertTrue(PliantApplicationStrategy.averageFuture(prediction, 10).isEmpty());
    }

    @Test
    void zeroPredictionRemainsValid() throws Exception {
        assertEquals(0.0, PliantApplicationStrategy
                .averageFuture(prediction(0.0), 10).orElseThrow(), 0.0);
    }

    @Test
    void backlogSumsAllApplicationsInBytes() {
        ComputingAppliance appliance = emptyAppliance();
        appliance.applications.add(application(10_000L, 2_500L, 5_120L));
        appliance.applications.add(application(8_000L, 3_000L, 1_024L));

        // 7,500 + 5,000 bytes, without division by task size.
        assertEquals(12_500.0,
                PliantApplicationStrategy.unprocessedBytes(appliance), 0.0);
    }

    @Test
    void backlogCanExceedIntegerLimit() {
        ComputingAppliance appliance = emptyAppliance();
        appliance.applications.add(application(2_000_000_000L, 0L, 5_120L));
        appliance.applications.add(application(2_000_000_000L, 0L, 5_120L));

        assertEquals(4_000_000_000.0,
                PliantApplicationStrategy.unprocessedBytes(appliance), 0.0);
    }

    @Test
    void emptyApplianceHasZeroBacklog() {
        assertEquals(0.0,
                PliantApplicationStrategy.unprocessedBytes(emptyAppliance()), 0.0);
    }

    private ComputingAppliance emptyAppliance() {
        ComputingAppliance appliance = new ComputingAppliance("test");
        appliance.applications = new ArrayList<>();
        return appliance;
    }

    private Application application(long received, long processed, long taskSize) {
        Application app = new Application(
                "test", 60_000L, taskSize, 480.0, false,
                new PliantApplicationStrategy(0.9, 2.0), null);

        // Remove this test object from the global application registry.
        Application.allApplications.remove(app);
        app.receivedData = received;
        app.processedData = processed;
        return app;
    }

    private Prediction prediction(double... values) throws Exception {
        JSONArray data = new JSONArray();
        for (double value : values) {
            data.put(value);
        }

        JSONObject json = new JSONObject().put("prediction_future",
                new JSONObject().put("data", data));
        return JsonParser.fromJsonObject(json, Prediction.class, null);
    }
}