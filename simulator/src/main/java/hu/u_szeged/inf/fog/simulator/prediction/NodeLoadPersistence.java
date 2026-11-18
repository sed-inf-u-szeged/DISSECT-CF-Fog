package hu.u_szeged.inf.fog.simulator.prediction;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One-step node-load forecasts, issued and matched only from observed samples. */
public final class NodeLoadPersistence {
    public static final String MODEL = "Persistence";
    public static final String VERSION = "fixed-v1";
    public static final String PREDICTION_HEADER = "run_id,node_id,task,model,model_version,target_metric,unit,"
            + "origin_sample_index,last_input_time_ms,origin_time_ms,target_time_ms,horizon_ms,"
            + "predicted_waiting_assignment_bytes,inference_duration_ns";
    public static final String EVALUATION_HEADER = PREDICTION_HEADER
            + ",actual_sample_index,actual_time_ms,actual_waiting_assignment_bytes,error_bytes,absolute_error_bytes";

    private final String runId;
    private final long interval;
    private final Writer predictions;
    private final Writer evaluations;
    private final Map<String, State> nodes = new LinkedHashMap<>();
    private NodeLoadListener listener = NodeLoadListener.NONE;

    public void setListener(NodeLoadListener listener) { this.listener = java.util.Objects.requireNonNull(listener); }

    private static final class State {
        long generated, matched, lastTime, lastValue;
        double absoluteError, squaredError, signedError, inferenceNanos;
        String pendingRow;
    }

    public NodeLoadPersistence(String runId, long interval, Writer predictions, Writer evaluations)
            throws IOException {
        checkId(runId);
        if (interval <= 0) { throw new IllegalArgumentException("Interval must be positive"); }
        this.runId = runId;
        this.interval = interval;
        this.predictions = predictions;
        this.evaluations = evaluations;
        predictions.write(PREDICTION_HEADER + "\n");
        evaluations.write(EVALUATION_HEADER + "\n");
    }

    private static void checkId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid run or node identifier");
        }
    }

    private static long predict(long observed) { return observed; }

    /** Must be called once per node per grid boundary, after events at that time. */
    public void observe(String node, long index, long time, long waitingBytes) throws IOException {
        checkId(node);
        State state = nodes.get(node);
        long expectedIndex = state == null ? 0 : state.generated;
        if (index != expectedIndex || index < 0 || time != Math.multiplyExact(index, interval)
                || waitingBytes < 0) {
            throw new IllegalArgumentException("Duplicate, missing or invalid node sample");
        }
        long target = Math.addExact(time, interval);
        if (state == null) {
            state = new State();
            nodes.put(node, state);
        }
        // Only now does the target of the previous forecast have a known actual.
        if (state.pendingRow != null) {
            long error = state.lastValue - waitingBytes;
            evaluations.write(state.pendingRow + "," + index + "," + time + "," + waitingBytes
                    + "," + error + "," + Math.abs(error) + "\n");
            state.matched++;
            state.absoluteError += Math.abs(error);
            state.squaredError += (double) error * error;
            state.signedError += error;
        }
        long started = System.nanoTime();
        long predicted = predict(waitingBytes);
        long elapsed = System.nanoTime() - started;
        String row = runId + "," + node + ",node_load," + MODEL + "," + VERSION
                + ",waiting_assignment_bytes,B," + index + "," + time + "," + time + "," + target
                + "," + interval + "," + predicted + "," + elapsed;
        predictions.write(row + "\n");
        state.generated++;
        state.lastTime = time;
        state.lastValue = predicted;
        state.inferenceNanos += elapsed;
        state.pendingRow = row;
        try {
            if (index > 0) {
                listener.emit("EVALUATION", new JSONObject().put("node_id", node).put("model", MODEL).put("version", VERSION)
                        .put("time_ms", time).put("origin_index", index - 1).put("target_index", index)
                        .put("actual_bytes", waitingBytes));
            }
            listener.emit("VECTOR", new JSONObject().put("node_id", node).put("model", MODEL).put("version", VERSION)
                    .put("time_ms", time).put("origin_index", index).put("values", new JSONArray().put(predicted))
                    .put("inference_vector_ms", elapsed / 1_000_000.0));
        } catch (JSONException error) { throw new IOException("Cannot publish load baseline", error); }
    }

    private static JSONObject metrics(State state) throws JSONException {
        return new JSONObject().put("generated", state.generated).put("matched", state.matched)
                .put("unmatched", state.generated - state.matched)
                .put("mae_bytes", state.matched == 0 ? JSONObject.NULL : state.absoluteError / state.matched)
                .put("rmse_bytes", state.matched == 0 ? JSONObject.NULL
                        : Math.sqrt(state.squaredError / state.matched))
                .put("mean_error_bytes", state.matched == 0 ? JSONObject.NULL : state.signedError / state.matched)
                .put("mean_inference_ms", state.generated == 0 ? JSONObject.NULL
                        : state.inferenceNanos / state.generated / 1_000_000.0);
    }

    /** Unobserved tail targets remain unmatched; no final-settlement label is invented. */
    public JSONObject summary() throws JSONException {
        State total = new State();
        JSONArray entries = new JSONArray();
        for (Map.Entry<String, State> entry : nodes.entrySet()) {
            State state = entry.getValue();
            entries.put(metrics(state).put("node_id", entry.getKey())
                    .put("last_origin_time_ms", state.lastTime)
                    .put("pending_target_time_ms", state.lastTime + interval));
            total.generated += state.generated;
            total.matched += state.matched;
            total.absoluteError += state.absoluteError;
            total.squaredError += state.squaredError;
            total.signedError += state.signedError;
            total.inferenceNanos += state.inferenceNanos;
        }
        return new JSONObject().put("schema_version", 1).put("run_id", runId).put("task", "node_load")
                .put("model", MODEL).put("model_version", VERSION).put("target_metric", "waiting_assignment_bytes")
                .put("unit", "B").put("horizon_ms", interval).put("sample_phase", FixedIntervalSampler.PHASE)
                .put("role", "BASELINE_COMPARISON").put("controls_simulation", false)
                .put("qualification_required", false).put("execution_mode", "SIMULATION_CALLBACK")
                .put("evaluation_scope", "ALL_MATCHED_GRID_TARGETS_INCLUDING_STARTUP")
                .put("inference_timing_scope", "PREDICT_CALL_AND_CLOCK_OVERHEAD_EXCLUDING_IO")
                .put("nodes", entries).put("aggregate", metrics(total));
    }
}
