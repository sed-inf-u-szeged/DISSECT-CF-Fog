package hu.u_szeged.inf.fog.simulator.prediction.mobility;

import static org.junit.jupiter.api.Assertions.*;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobilityLifecycleAdapterTest {
    @TempDir Path directory;
    LifecycleManager manager;

    @BeforeEach
    void reset() { manager = LifecycleManager.getInstance(); manager.reset(); }

    class Stream implements AutoCloseable {
        final MobilityLifecycleAdapter adapter;
        int sequence;
        int index = -1;
        int version;
        double longitude;

        Stream(int observations) throws Exception { this(observations, false); }
        Stream(int observations, boolean closedLoop) throws Exception {
            adapter = new MobilityLifecycleAdapter(manager, directory, "run", "0", observations, closedLoop);
        }
        void observe(int tick, double lon, boolean more) throws Exception {
            index = tick; longitude = lon;
            adapter.observe("run", "0", tick * 60000L, 0, lon, more);
        }
        JSONObject message(String event) throws Exception {
            return new JSONObject().put("sequence", sequence + 1).put("event", event)
                    .put("sim_time_ms", index * 60000L).put("run_id", "run").put("device_id", "0")
                    .put("model_version", String.format("v%04d", version));
        }
        void send(JSONObject frame) throws Exception { adapter.accept(frame.toString()); sequence++; }
        void train(int number) throws Exception {
            train(number, 32 + 64 * (number - 1));
        }
        void train(int number, int count) throws Exception {
            version = number;
            send(message("TRAINING_STARTED").put("request_number", number).put("training_windows", count)
                    .put("latest_label_ms", index * 60000L));
            send(message("TRAINING_COMPLETE").put("training_windows", count).put("scaler", "FROZEN"));
        }
        JSONObject forecast(double candidate) throws Exception {
            JSONArray values = new JSONArray();
            values.put(new JSONObject().put("model", "HoldPosition").put("latitude_deg", 0).put("longitude_deg", longitude));
            values.put(new JSONObject().put("model", "ConstantVelocity").put("latitude_deg", 0).put("longitude_deg", longitude));
            values.put(new JSONObject().put("model", "LSTM").put("latitude_deg", 0).put("longitude_deg", candidate));
            return message("FORECAST").put("target_ms", (index + 1) * 60000L).put("predictions", values);
        }
        void closeTick() throws Exception { adapter.endObservation(); }
        public void close() throws Exception { adapter.close(); }
    }

    JSONObject summary() throws Exception { return new JSONObject(Files.readString(directory.resolve("shared-lifecycle.json"))); }

    @Test
    void promotesOnlyAfter64TargetsThenFallsBackWithoutTouchingLoadOwner() throws Exception {
        LifecycleCoordinator load = new LifecycleCoordinator("load-baseline", "cpu", new LifecyclePolicy(64, 1, 1, 1, 1, 1, .2));
        manager.registerCoordinator(load); manager.activateBaseline("load-baseline", "cpu");
        try (Stream stream = new Stream(297)) {
            for (int i = 0; i < 297; i++) {
                stream.observe(i, i * .001, i < 296);
                if (i == 40) { stream.train(1); }
                if (i >= 40 && i < 296) { stream.send(stream.forecast(i < 104 ? (i + 1) * .001 : 10)); }
                stream.closeTick();
                if (i == 103) { assertEquals("run", summary().getString("run_id")); }
            }
            stream.adapter.finish();
        }
        JSONObject result = summary();
        assertEquals("COMPLETE", result.getString("status"));
        assertEquals(1, result.getInt("promotions"));
        assertEquals(1, result.getInt("fallbacks"));
        assertEquals(1, result.getInt("lstm_selected_origins"));
        assertEquals(288, result.getInt("selected_matched"));
        assertFalse(result.getBoolean("active_lstm"));
        assertEquals("DEGRADED", result.getJSONArray("versions").getJSONObject(0).getString("state"));
        assertEquals("load-baseline", manager.getActivePredictorName("cpu"));
        assertEquals(LifecycleState.ACTIVE, load.getState());
        String journal = Files.readString(directory.resolve("shared_lifecycle_events.jsonl"));
        long promotion = -1;
        for (String line : journal.split("\n")) {
            JSONObject event = new JSONObject(line);
            if ("PROMOTED".equals(event.getString("event"))) { promotion = event.getLong("sim_time_ms"); }
        }
        assertEquals(104 * 60000L, promotion);
    }

    @Test
    void rejectionUsesIndependentFutureBlocksAndNoVersionCanBorrowTargets() throws Exception {
        try (Stream stream = new Stream(297)) {
            for (int i = 0; i < 297; i++) {
                stream.observe(i, 0, i < 296);
                if (i >= 40 && (i - 40) % 64 == 0 && i < 296) { stream.train(1 + (i - 40) / 64); }
                if (i >= 40 && i < 296) { stream.send(stream.forecast(.001)); }
                stream.closeTick();
            }
            stream.adapter.finish();
        }
        JSONObject result = summary();
        assertEquals(0, result.getInt("promotions"));
        JSONArray versions = result.getJSONArray("versions");
        assertEquals(4, versions.length());
        for (int i = 0; i < 4; i++) {
            assertEquals(64, versions.getJSONObject(i).getInt("matched_origins"));
            assertEquals("REJECT", versions.getJSONObject(i).getString("quality_decision"));
            assertEquals("FAILED", versions.getJSONObject(i).getString("state"));
        }
    }

    @Test
    void duplicateGapIdentityAndEarlyTrainingDoNotConsumeObservation() throws Exception {
        try (Stream stream = new Stream(105)) {
            stream.observe(0, 0, true); stream.closeTick();
            assertThrows(IllegalArgumentException.class, () -> stream.adapter.observe("other", "0", 60000, 0, 0, true));
            assertThrows(IllegalArgumentException.class, () -> stream.adapter.observe("run", "0", 0, 0, 0, true));
            assertThrows(IllegalArgumentException.class, () -> stream.adapter.observe("run", "0", 120000, 0, 0, true));
            stream.observe(1, 0, true);
            stream.version = 1;
            assertThrows(IllegalArgumentException.class, () -> stream.send(stream.message("TRAINING_STARTED")
                    .put("request_number", 1).put("training_windows", 32).put("latest_label_ms", 60000)));
            assertThrows(IllegalArgumentException.class, stream.adapter::finish);
            stream.closeTick();
        }
        assertEquals("FAILED", summary().getString("status"));
        assertEquals(2, summary().getInt("observations"));
    }

    @Test
    void invalidForecastBatchesAreAtomicAndWrongVersionIsRejected() throws Exception {
        try (Stream stream = new Stream(105)) {
            for (int i = 0; i <= 40; i++) {
                stream.observe(i, 0, true);
                if (i < 40) { stream.closeTick(); }
            }
            stream.train(1);
            JSONObject wrong = stream.forecast(0).put("model_version", "v0002");
            assertThrows(IllegalArgumentException.class, () -> stream.send(wrong));
            JSONObject bad = stream.forecast(0);
            bad.getJSONArray("predictions").getJSONObject(2).put("latitude_deg", 100);
            assertThrows(IllegalArgumentException.class, () -> stream.send(bad));
            JSONObject duplicate = stream.forecast(0);
            duplicate.getJSONArray("predictions").getJSONObject(2).put("model", "HoldPosition");
            assertThrows(IllegalArgumentException.class, () -> stream.send(duplicate));
            stream.send(stream.forecast(0)); stream.closeTick();
            stream.observe(41, 0, true);
            assertThrows(IllegalArgumentException.class, () -> stream.train(2));
        }
        assertEquals("FAILED", summary().getString("status"));
        assertEquals(1, summary().getJSONArray("versions").getJSONObject(0).getInt("matched_origins"));
    }

    @Test
    void missingForecastAndMissingEndCannotReportComplete() throws Exception {
        try (Stream stream = new Stream(105)) {
            for (int i = 0; i <= 40; i++) {
                stream.observe(i, 0, true);
                if (i < 40) { stream.closeTick(); }
            }
            stream.train(1);
            assertThrows(IllegalArgumentException.class, stream.adapter::endObservation);
        }
        assertEquals("FAILED", summary().getString("status"));
        assertFalse(summary().getBoolean("active_lstm"));
    }

    @Test
    void distanceHasMetreUnitsAndWrapsDateline() {
        assertEquals(111.31949, MobilityLifecycleAdapter.distance(new double[] {0, 0}, new double[] {0, .001}), .00001);
        assertEquals(222.63898, MobilityLifecycleAdapter.distance(new double[] {0, 179.999}, new double[] {0, -179.999}), .00001);
    }
    @Test
    void closedLoopRefitsAfterFallbackAndRequalifiesOnItsOwnFutureTargets() throws Exception {
        try (Stream stream = new Stream(174, true)) {
            for (int i = 0; i < 174; i++) {
                stream.observe(i, i * .001, i < 173);
                JSONObject command = new JSONObject(stream.adapter.control());
                if ("TRAIN".equals(command.getString("action"))) {
                    if (i == 105) {
                        assertEquals("DEGRADATION", command.getString("reason"));
                        assertEquals("DEGRADED", command.getString("previous_state"));
                        assertEquals(32, command.getInt("window_size"));
                        assertEquals(97, command.getInt("available_windows"));
                    } else { assertEquals(40, i); }
                    stream.train(command.getInt("request_number"), command.getInt("window_size"));
                }
                if (i >= 40 && i < 173) { stream.send(stream.forecast(i == 104 ? 10 : (i + 1) * .001)); }
                stream.closeTick();
            }
            stream.adapter.finish();
        }
        JSONObject result = summary();
        assertEquals("COMPLETE", result.getString("status"));
        assertEquals("JAVA_SHARED_RULES", result.getString("training_scheduler"));
        assertEquals(2, result.getInt("promotions"));
        assertEquals(1, result.getInt("fallbacks"));
        assertEquals(1, result.getInt("degradation_retrainings"));
        assertTrue(result.getBoolean("active_lstm"));
        JSONArray versions = result.getJSONArray("versions");
        assertEquals(65, versions.getJSONObject(0).getInt("matched_origins"));
        assertEquals("DEGRADED", versions.getJSONObject(0).getString("state"));
        assertEquals(68, versions.getJSONObject(1).getInt("matched_origins"));
        assertEquals(169 * 60000L, versions.getJSONObject(1).getLong("decided_at_sim_ms"));
        assertEquals("DEGRADATION", versions.getJSONObject(1).getString("training_reason"));
        assertEquals(165, result.getInt("selected_matched"));
    }

    @Test
    void closedLoopRejectionGrowsButTerminalTickNeverStartsAnUnevaluableFit() throws Exception {
        try (Stream stream = new Stream(233, true)) {
            for (int i = 0; i < 233; i++) {
                stream.observe(i, 0, i < 232);
                JSONObject command = new JSONObject(stream.adapter.control());
                if ("TRAIN".equals(command.getString("action"))) {
                    assertEquals(0, (i - 40) % 64);
                    stream.train(command.getInt("request_number"), command.getInt("window_size"));
                }
                if (i == 232) {
                    assertEquals("HOLD", command.getString("action"));
                    assertEquals("STREAM_COMPLETE", command.getString("reason"));
                    assertEquals(224, command.getInt("window_size"));
                }
                if (i >= 40 && i < 232) { stream.send(stream.forecast(.001)); }
                stream.closeTick();
            }
            stream.adapter.finish();
        }
        JSONArray versions = summary().getJSONArray("versions");
        assertEquals(3, versions.length());
        for (int i = 0; i < 3; i++) {
            assertEquals(32 + 64 * i, versions.getJSONObject(i).getInt("training_windows"));
            assertEquals(64, versions.getJSONObject(i).getInt("matched_origins"));
        }
    }

    @Test
    void closedLoopRequiresOneControlAndMatchingFitAcknowledgement() throws Exception {
        try (Stream stream = new Stream(105, true)) {
            assertThrows(IllegalArgumentException.class, stream.adapter::control);
            for (int i = 0; i <= 40; i++) {
                stream.observe(i, 0, true);
                if (i == 40) {
                    assertThrows(IllegalArgumentException.class, () -> stream.train(1, 32));
                }
                stream.adapter.control();
                assertThrows(IllegalArgumentException.class, stream.adapter::control);
                if (i < 40) { stream.closeTick(); }
            }
            assertThrows(IllegalArgumentException.class, () -> stream.train(1, 96));
            assertThrows(IllegalArgumentException.class, stream.adapter::endObservation);
            stream.train(1, 32);
            assertThrows(IllegalArgumentException.class, () -> stream.train(1, 32));
            stream.send(stream.forecast(0)); stream.closeTick();
        }
        assertEquals("FAILED", summary().getString("status"));
    }

    @Test
    void closedLoopTrainingFailureAfterFallbackCannotCompleteOrKeepActive() throws Exception {
        try (Stream stream = new Stream(174, true)) {
            for (int i = 0; i <= 105; i++) {
                stream.observe(i, i * .001, true);
                JSONObject command = new JSONObject(stream.adapter.control());
                if (i == 105) {
                    assertEquals("DEGRADATION", command.getString("reason"));
                    stream.version = 2;
                    stream.send(stream.message("TRAINING_STARTED").put("request_number", 2)
                            .put("training_windows", 32).put("latest_label_ms", i * 60000L));
                    assertThrows(IllegalArgumentException.class, stream.adapter::endObservation);
                    assertThrows(IllegalArgumentException.class, stream.adapter::finish);
                    break;
                }
                if (i == 40) { stream.train(1, 32); }
                if (i >= 40) { stream.send(stream.forecast(i == 104 ? 10 : (i + 1) * .001)); }
                stream.closeTick();
            }
        }
        assertEquals("FAILED", summary().getString("status"));
        assertFalse(summary().getBoolean("active_lstm"));
    }

}
