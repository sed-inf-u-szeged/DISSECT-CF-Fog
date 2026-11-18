package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.*;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import java.io.StringWriter;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeLoadPersistenceTest {
    private final StringWriter predictions = new StringWriter();
    private final StringWriter evaluations = new StringWriter();
    private NodeLoadPersistence baseline;

    @BeforeEach void setup() throws Exception {
        Timed.resetTimed();
        baseline = new NodeLoadPersistence("run-1", 60, predictions, evaluations);
    }
    @AfterEach void cleanup() { Timed.resetTimed(); }

    @Test void issuesBeforeTheActualAndScoresOnlyWhenThatTargetArrives() throws Exception {
        baseline.observe("west", 0, 0, 15);
        assertEquals(2, predictions.toString().lines().count());
        assertEquals(1, evaluations.toString().lines().count());
        String[] first = predictions.toString().split("\n")[1].split(",");
        assertEquals("0", first[8]);
        assertEquals("60", first[10]);
        assertEquals("15", first[12]);
        baseline.observe("west", 1, 60, 4);
        String[] scored = evaluations.toString().split("\n")[1].split(",");
        assertEquals("15", scored[12]);
        assertEquals("60", scored[15]);
        assertEquals("4", scored[16]);
        assertEquals("11", scored[17]);
        JSONObject result = baseline.summary().getJSONObject("aggregate");
        assertEquals(1, result.getLong("matched"));
        assertEquals(1, result.getLong("unmatched"));
        assertEquals(11.0, result.getDouble("mae_bytes"));
    }

    @Test void keepsNodesSeparateAndComputesRmseFromErrorsNotNodeAverages() throws Exception {
        baseline.observe("west", 0, 0, 15);
        baseline.observe("east", 0, 0, 100);
        baseline.observe("west", 1, 60, 4); // Error +11.
        baseline.observe("east", 1, 60, 105); // Error -5.
        JSONObject result = baseline.summary().getJSONObject("aggregate");
        assertEquals(4, result.getLong("generated"));
        assertEquals(2, result.getLong("unmatched"));
        assertEquals(8.0, result.getDouble("mae_bytes"));
        assertEquals(Math.sqrt(73), result.getDouble("rmse_bytes"), 1e-12);
        assertEquals(3.0, result.getDouble("mean_error_bytes"));
    }

    @Test void oneSampleHasAnUnmatchedForecastAndNoFabricatedZeroError() throws Exception {
        baseline.observe("west", 0, 0, 150);
        JSONObject summary = baseline.summary();
        assertTrue(summary.getJSONObject("aggregate").isNull("mae_bytes"));
        assertEquals(1, summary.getJSONObject("aggregate").getLong("unmatched"));
        assertFalse(summary.getBoolean("controls_simulation"));
        assertEquals(60, summary.getJSONArray("nodes").getJSONObject(0).getLong("pending_target_time_ms"));
        assertEquals(1, evaluations.toString().lines().count());
    }

    @Test void rejectsDuplicateGappedAndNegativeSamplesBeforeChangingOutput() throws Exception {
        baseline.observe("west", 0, 0, 15);
        String before = predictions.toString();
        assertThrows(IllegalArgumentException.class, () -> baseline.observe("west", 0, 0, 12));
        assertThrows(IllegalArgumentException.class, () -> baseline.observe("west", 2, 120, 12));
        assertThrows(IllegalArgumentException.class, () -> baseline.observe("west", 1, 61, 12));
        assertThrows(IllegalArgumentException.class, () -> baseline.observe("west", 1, 60, -1));
        assertEquals(before, predictions.toString());
        assertEquals(1, evaluations.toString().lines().count());
        baseline.observe("west", 1, 60, 10);
        assertEquals(5.0, baseline.summary().getJSONObject("aggregate").getDouble("mae_bytes"));
    }

    @Test void fixedSamplerDoesNotUseLaterEventsOrDrainingAsActualTargets() throws Exception {
        long[] value = {0};
        new DeferredEvent(60) { @Override protected void eventAction() { value[0] = 100; } };
        new DeferredEvent(61) { @Override protected void eventAction() { value[0] = 999; } };
        new DeferredEvent(130) { @Override protected void eventAction() { value[0] = 0; } };
        FixedIntervalSampler.simulate(60, 120, 200,
                (index, time) -> baseline.observe("west", index, time, value[0]));
        String[] forecastRows = predictions.toString().split("\n");
        assertEquals("100", forecastRows[2].split(",")[12]);
        assertEquals("999", forecastRows[3].split(",")[12]);
        assertEquals(0, value[0]); // Work drained, but t=180 was never observed.
        assertEquals(2, baseline.summary().getJSONObject("aggregate").getLong("matched"));
        assertEquals(1, baseline.summary().getJSONObject("aggregate").getLong("unmatched"));
        assertEquals(-1, Timed.getNextFire());
    }
}
