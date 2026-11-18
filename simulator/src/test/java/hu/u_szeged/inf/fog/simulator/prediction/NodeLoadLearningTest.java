package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.*;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleCoordinator;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecyclePolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodeLoadLearningTest {
    @TempDir Path output;
    @BeforeEach void setup() { LifecycleManager.getInstance().reset(); }
    @AfterEach void cleanup() { LifecycleManager.getInstance().reset(); }

    static final class Fake implements NodeLoadLearning.Backend {
        final List<JSONObject> requests = new ArrayList<>();
        double prediction = 100;
        @Override public JSONObject request(JSONObject request) throws org.json.JSONException {
            requests.add(request);
            if (request.getString("op").equals("TRAIN")) {
                return new JSONObject().put("training_seconds", .1).put("training_windows", request.getInt("window_points") - 255);
            }
            JSONArray result = new JSONArray();
            for (int i = 0; i < 64; i++) { result.put(prediction); }
            return new JSONObject().put("values", result).put("inference_ms", 1)
                    .put("usable", true).put("raw_values", result).put("clipped_count", 0)
                    .put("status", new JSONObject().put("status", "test-double"));
        }
        @Override public void close() { }
    }

    @Test void publishesTrainingBeforeTheWorkerAndOnlyCompleteCausalVectors() throws Exception {
        List<String> kinds = new ArrayList<>();
        List<JSONObject> live = new ArrayList<>();
        Fake delegate = new Fake();
        NodeLoadLearning.Backend backend = new NodeLoadLearning.Backend() {
            @Override public JSONObject request(JSONObject request) throws org.json.JSONException {
                if (request.getString("op").equals("TRAIN")) {
                    assertEquals("EVENT", kinds.get(kinds.size() - 1));
                    assertEquals("TRAINING", live.get(live.size() - 1).getString("event"));
                }
                return delegate.request(request);
            }
            @Override public void close() { }
        };
        try (NodeLoadLearning model = new NodeLoadLearning(output, List.of("LSTM"), "candidate", 1, backend)) {
            model.setListener((kind, row) -> { kinds.add(kind); live.add(row); });
            for (int i = 0; i <= 286; i++) { model.observe("west", i, i * 60000L, 100); }
            assertFalse(live.stream().anyMatch(r -> "LSTM".equals(r.optString("model")) && r.has("actual_bytes")));
            int vector = -1;
            for (int i = 0; i < live.size(); i++) { if (kinds.get(i).equals("VECTOR") && live.get(i).optString("model").equals("LSTM")) { vector = i; break; } }
            assertEquals(64, live.get(vector).getJSONArray("values").length());
            assertEquals(286, live.get(vector).getInt("origin_index"));
            model.observe("west", 287, 287 * 60000L, 200);
            JSONObject evaluated = live.stream().filter(r -> r.optString("model").equals("LSTM") && r.has("actual_bytes")).findFirst().orElseThrow();
            assertEquals(287 * 60000L, evaluated.getLong("time_ms"));
            assertEquals(200, evaluated.getDouble("actual_bytes"));
            model.finish();
        }
    }

    @Test void anInvalidVectorNeverPublishesOrPartiallyWritesTargets() throws Exception {
        Fake delegate = new Fake();
        NodeLoadLearning.Backend backend = new NodeLoadLearning.Backend() {
            @Override public JSONObject request(JSONObject request) throws org.json.JSONException {
                JSONObject result = delegate.request(request);
                if (request.getString("op").equals("PREDICT")) { result.getJSONArray("values").put(63, -1); }
                return result;
            }
            @Override public void close() { }
        };
        List<String> kinds = new ArrayList<>();
        try (NodeLoadLearning model = new NodeLoadLearning(output, List.of("TCN"), "candidate", 1, backend)) {
            model.setListener((kind, row) -> { if (row.optString("model").equals("TCN")) { kinds.add(kind); } });
            for (int i = 0; i < 286; i++) { model.observe("west", i, i * 60000L, 100); }
            assertThrows(java.io.IOException.class, () -> model.observe("west", 286, 286 * 60000L, 100));
            assertFalse(kinds.contains("VECTOR"));
        }
        assertEquals(1, Files.readAllLines(output.resolve("node_load_model_predictions.csv")).size());
    }

    private List<JSONObject> events() throws Exception {
        List<JSONObject> result = new ArrayList<>();
        for (String line : Files.readAllLines(output.resolve("node_load_model_events.jsonl"))) { result.add(new JSONObject(line)); }
        return result;
    }
    private boolean hasEvent(String name, int index) throws Exception {
        for (JSONObject event : events()) {
            if (event.getString("event").equals(name) && event.getInt("sample_index") == index) { return true; }
        }
        return false;
    }

    @Test void usesPastOnlyAndWaitsFor64ActualTargets() throws Exception {
        Fake backend = new Fake();
        try (NodeLoadLearning model = new NodeLoadLearning(output, List.of("LSTM", "TCN", "GRU", "PatchTST"), "candidate", 40, backend)) {
            for (int i = 0; i < 286; i++) { model.observe("west", i, i * 60000L, 100); }
            assertTrue(backend.requests.stream().noneMatch(r -> r.optString("op").equals("TRAIN")));
            model.observe("west", 286, 286 * 60000L, 100);
            JSONObject train = backend.requests.stream().filter(r -> r.optString("op").equals("TRAIN")).findFirst().orElseThrow();
            assertEquals(287, train.getJSONArray("values").length());
            assertEquals(286, train.getInt("origin_index"));
            for (int i = 287; i < 350; i++) { model.observe("west", i, i * 60000L, 100); }
            assertFalse(hasEvent("QUALIFIED", 349));
            model.observe("west", 350, 350 * 60000L, 100);
            assertTrue(hasEvent("QUALIFIED", 350)); assertTrue(hasEvent("PROMOTED", 350));
            model.finish();
        }
        JSONObject summary = new JSONObject(Files.readString(output.resolve("node_load_model_summary.json")));
        assertEquals(64, summary.getJSONArray("versions").getJSONObject(0).getInt("matched"));
        assertEquals(64, summary.getJSONArray("versions").getJSONObject(0).getInt("unmatched"));
        assertFalse(summary.getBoolean("resource_control"));
        List<String> selected = Files.readAllLines(output.resolve("node_load_model_selection.csv"));
        assertTrue(selected.get(selected.size()-2).contains(",LR,online-v1,"));
        // The shared lifecycle breaks identical scores by predictor name: GRU wins this tie.
        assertTrue(selected.get(selected.size()-1).contains(",GRU,v0001,350,1,100.0"));
        assertEquals(4, events().stream().filter(e -> e.optString("event").equals("QUALIFIED")).count());
    }

    @Test void baselinesRunWithoutNeuralModelsAfter192PastSamples() throws Exception {
        Fake backend = new Fake();
        try (NodeLoadLearning model = new NodeLoadLearning(output, List.of(), "candidate", 40, backend)) {
            for (int i = 0; i < 191; i++) { model.observe("west", i, i * 60000L, i); }
            assertTrue(backend.requests.isEmpty());
            for (int i = 191; i <= 256; i++) { model.observe("west", i, i * 60000L, i); }
            model.finish();
        }
        assertEquals(4, backend.requests.size());
        for (JSONObject request : backend.requests) {
            assertEquals("BASELINE", request.getString("op"));
            int origin = request.getInt("origin_index");
            assertTrue(origin == 191 || origin == 255);
            JSONArray input = request.getJSONArray("values");
            assertEquals(192, input.length());
            assertEquals(origin - 191, input.getDouble(0));
            assertEquals(origin, input.getDouble(191));
        }
        List<String> selected = Files.readAllLines(output.resolve("node_load_model_selection.csv"));
        assertEquals(67, selected.size());
        assertTrue(selected.get(1).contains(",191,11460000,192,11520000,LR,online-v1,191,1,"));
        assertFalse(Files.exists(output.resolve("node_load_predictions.csv")));
        assertEquals(257, Files.readAllLines(output.resolve("node_load_baseline_predictions.csv")).size());
    }

    @Test void unavailableLrUsesArimaAndNeverRenamesAFailedForecast() throws Exception {
        Fake delegate = new Fake();
        NodeLoadLearning.Backend backend = new NodeLoadLearning.Backend() {
            @Override public JSONObject request(JSONObject request) throws org.json.JSONException {
                JSONObject reply = delegate.request(request);
                if (request.getString("model").equals("LR")) {
                    return reply.put("usable", false).put("values", new JSONArray())
                            .put("raw_values", new JSONArray()).put("status", new JSONObject().put("status", "failed"));
                }
                return reply;
            }
            @Override public void close() { }
        };
        try (NodeLoadLearning model = new NodeLoadLearning(output, List.of(), "candidate", 40, backend)) {
            for (int i = 0; i <= 191; i++) { model.observe("west", i, i * 60000L, 100); }
            model.finish();
        }
        assertTrue(Files.readAllLines(output.resolve("node_load_model_selection.csv")).get(1).contains(",ARIMA,online-v1,191,1,"));
        assertEquals(65, Files.readAllLines(output.resolve("node_load_baseline_predictions.csv")).size());
    }

    @Test void rejectionExpandsWindowAndRequires64NewPointsBeforeRetry() throws Exception {
        Fake backend = new Fake(); backend.prediction = 0;
        try (NodeLoadLearning model = new NodeLoadLearning(output, List.of("LSTM"), "candidate", 40, backend)) {
            for (int i = 0; i <= 413; i++) { model.observe("west", i, i * 60000L, 100); }
            assertTrue(hasEvent("REJECTED", 350));
            assertEquals(1, backend.requests.stream().filter(r -> r.optString("op").equals("TRAIN")).count());
            model.observe("west", 414, 414 * 60000L, 100); model.finish();
        }
        JSONObject second = backend.requests.stream().filter(r -> r.optString("op").equals("TRAIN")).skip(1).findFirst().orElseThrow();
        assertEquals("TRAIN", second.getString("op")); assertEquals(351, second.getInt("window_points"));
        assertEquals(414, second.getInt("origin_index"));
    }

    @Test void degradationFallsBackWithoutWaitingForTheEndOfTheForecastBlock() throws Exception {
        Fake backend = new Fake();
        try (NodeLoadLearning model = new NodeLoadLearning(output, List.of("TCN"), "candidate", 40, backend)) {
            for (int i = 0; i <= 363; i++) { model.observe("west", i, i * 60000L, i <= 350 ? 100 : 1000); }
            assertTrue(hasEvent("DEGRADED", 363)); assertTrue(hasEvent("FALLBACK", 363));
            model.finish();
        }
        List<String> rows = Files.readAllLines(output.resolve("node_load_model_selection.csv"));
        assertTrue(rows.get(rows.size()-1).endsWith(",LR,online-v1,350,14,100.0"));
    }

    @Test void shortRunsRemainUntrainedAndDoNotResetMobilityLifecycle() throws Exception {
        LifecycleManager manager = LifecycleManager.getInstance();
        manager.registerCoordinator(new LifecycleCoordinator("HoldPosition", "mobility/existing", new LifecyclePolicy(64, 1, 1, 1, 1, 1, .2)));
        manager.activateBaseline("HoldPosition", "mobility/existing");
        Fake backend = new Fake();
        try (NodeLoadLearning model = new NodeLoadLearning(output, List.of("LSTM"), "candidate", 40, backend)) {
            for (int i = 0; i < 124; i++) {
                model.observe("west", i, i * 60000L, 100);
                model.observe("east", i, i * 60000L, 0);
            }
            model.finish();
        }
        assertTrue(backend.requests.isEmpty());
        assertEquals("HoldPosition", manager.getActivePredictorName("mobility/existing"));
        JSONObject summary = new JSONObject(Files.readString(output.resolve("node_load_model_summary.json")));
        assertEquals(0, summary.getJSONArray("versions").length());
        assertEquals("LR", summary.getJSONObject("final_selected").getString("east"));
    }

    @Test void nodesHaveSeparateTrainingHistoriesAndInvalidSamplesAreRejected() throws Exception {
        Fake backend = new Fake();
        try (NodeLoadLearning model = new NodeLoadLearning(output, List.of("LSTM"), "observe", 40, backend)) {
            for (int i = 0; i <= 286; i++) {
                model.observe("west", i, i * 60000L, 100);
                model.observe("east", i, i * 60000L, 1000);
            }
            List<JSONObject> trains = backend.requests.stream().filter(r -> r.optString("op").equals("TRAIN")).collect(java.util.stream.Collectors.toList());
            assertEquals(100, trains.get(0).getJSONArray("values").getDouble(0));
            assertEquals(1000, trains.get(1).getJSONArray("values").getDouble(0));
            assertThrows(IllegalArgumentException.class, () -> model.observe("west", 286, 286 * 60000L, 100));
            assertThrows(IllegalArgumentException.class, () -> model.observe("west", 288, 288 * 60000L, 100));
            assertThrows(IllegalArgumentException.class, () -> model.observe("west", 287, 287 * 60000L, Double.NaN));
            model.finish();
        }
    }
}
