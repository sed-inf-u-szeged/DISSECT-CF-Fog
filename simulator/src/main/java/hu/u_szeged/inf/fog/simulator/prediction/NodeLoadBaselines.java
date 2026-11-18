package hu.u_szeged.inf.fog.simulator.prediction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Standalone ARIMA/LR adapters on the same causal 192-input, 64-target grid. */
final class NodeLoadBaselines implements AutoCloseable {
    static final String VERSION = "online-v1";
    static final List<String> METHODS = java.util.Arrays.asList("ARIMA", "LR");
    private final Path output;
    private final String runId;
    private final NodeLoadLearning.Backend backend;
    private final BufferedWriter predictions;
    private final BufferedWriter evaluations;
    private final BufferedWriter fits;
    private final Map<String, List<Forecast>> pending = new LinkedHashMap<>();
    private final Map<String, Integer> lastOrigin = new LinkedHashMap<>();
    private int generated;
    private int matched;
    private int failed;
    private NodeLoadListener listener = NodeLoadListener.NONE;

    static final class Forecast {
        final String method;
        final int origin;
        final int target;
        final double value;
        final double ms;
        Forecast(String method, int origin, int target, double value, double ms) {
            this.method = method; this.origin = origin; this.target = target; this.value = value; this.ms = ms;
        }
    }
    NodeLoadBaselines(Path output, NodeLoadLearning.Backend backend) throws IOException {
        this.output = output; this.runId = output.getFileName().toString(); this.backend = backend;
        predictions = Files.newBufferedWriter(output.resolve("node_load_baseline_predictions.csv"));
        evaluations = Files.newBufferedWriter(output.resolve("node_load_baseline_evaluations.csv"));
        fits = Files.newBufferedWriter(output.resolve("node_load_baseline_fits.jsonl"));
        line(predictions, "run_id,node_id,model,version,origin_index,origin_ms,target_index,target_ms,lead_steps,predicted_bytes,inference_vector_ms");
        line(evaluations, "run_id,node_id,model,version,origin_index,target_index,target_ms,predicted_bytes,actual_bytes,absolute_error_bytes");
    }
    void setListener(NodeLoadListener value) { listener = value; }

    void observe(String node, int index, List<Double> history) throws IOException, JSONException {
        List<Forecast> outstanding = pending.computeIfAbsent(node, n -> new ArrayList<>());
        java.util.Iterator<Forecast> iterator = outstanding.iterator();
        while (iterator.hasNext()) {
            Forecast p = iterator.next();
            if (p.target != index) { continue; }
            double actual = history.get(index);
            line(evaluations, runId + "," + node + "," + p.method + "," + VERSION + "," + p.origin + "," + index
                    + "," + index * 60000L + "," + p.value + "," + actual + "," + Math.abs(p.value - actual));
            listener.emit("EVALUATION", new JSONObject().put("node_id", node).put("model", p.method)
                    .put("version", VERSION).put("time_ms", index * 60000L).put("origin_index", p.origin)
                    .put("target_index", index).put("actual_bytes", actual));
            matched++; iterator.remove();
        }
        if (index >= 191 && index - lastOrigin.getOrDefault(node, -1000) >= 64) { issue(node, index, history); }
    }
    /** Also fit at each neural issue time, so comparisons can use identical targets. */
    void issue(String node, int index, List<Double> history) throws IOException, JSONException {
        if (lastOrigin.getOrDefault(node, -1) == index) { return; }
        if (index < 191 || history.size() != index + 1) { throw new IOException("Invalid baseline origin"); }
        JSONArray past = new JSONArray(history.subList(index - 191, index + 1));
        for (String method : METHODS) {
            JSONObject response = backend.request(new JSONObject().put("op", "BASELINE").put("node", node)
                    .put("model", method).put("version", VERSION).put("origin_index", index).put("values", past));
            boolean usable = response.getBoolean("usable");
            double ms = response.getDouble("inference_ms");
            JSONArray values = response.getJSONArray("values");
            if (!Double.isFinite(ms) || ms < 0 || usable && values.length() != 64) { throw new IOException("Invalid baseline reply"); }
            for (int i = 0; i < values.length(); i++) {
                double value = values.getDouble(i);
                if (!Double.isFinite(value) || value < 0) { throw new IOException("Invalid baseline value"); }
            }
            line(fits, new JSONObject().put("run_id", runId).put("node_id", node).put("model", method)
                    .put("version", VERSION).put("origin_index", index).put("first_input_index", index - 191)
                    .put("last_input_index", index).put("usable", usable).put("status", response.getJSONObject("status"))
                    .put("clipped_count", response.getInt("clipped_count")).put("raw_values", response.getJSONArray("raw_values")).toString());
            if (!usable) { failed++; continue; }
            for (int lead = 1; lead <= 64; lead++) {
                Forecast p = new Forecast(method, index, index + lead, values.getDouble(lead - 1), ms);
                pending.computeIfAbsent(node, n -> new ArrayList<>()).add(p); generated++;
                line(predictions, runId + "," + node + "," + method + "," + VERSION + "," + index + "," + index * 60000L
                        + "," + p.target + "," + p.target * 60000L + "," + lead + "," + p.value + "," + ms);
            }
            listener.emit("VECTOR", new JSONObject().put("node_id", node).put("model", method).put("version", VERSION)
                    .put("time_ms", index * 60000L).put("origin_index", index).put("values", values).put("inference_vector_ms", ms));
        }
        lastOrigin.put(node, index);
    }
    Forecast selected(String node, int target) {
        // LR is the declared service/fallback baseline. ARIMA is an independent
        // comparator and backup if LR has no issued forecast for this target.
        for (String method : java.util.Arrays.asList("LR", "ARIMA")) {
            Forecast latest = null;
            for (Forecast p : pending.getOrDefault(node, java.util.Collections.emptyList())) {
                if (p.method.equals(method) && p.target == target && (latest == null || p.origin > latest.origin)) { latest = p; }
            }
            if (latest != null) { return latest; }
        }
        return null;
    }
    void finish() throws IOException, JSONException {
        Files.writeString(output.resolve("node_load_baseline_summary.json"), new JSONObject()
                .put("schema_version", 2).put("run_id", runId).put("models", new JSONArray(METHODS))
                .put("default_service", "LR").put("backup_service", "ARIMA").put("input_points", 192).put("output_points", 64)
                .put("interval_ms", 60000).put("first_origin_index", 191).put("refit_stride", 64)
                .put("refit_at_neural_origins", true).put("arima_order", new JSONArray(java.util.Arrays.asList(3, 0, 0)))
                .put("nonnegative_output", "CLIP_AT_ZERO").put("nonconverged_arima", "SKIP_BLOCK")
                .put("generated", generated).put("matched", matched).put("unmatched", generated - matched).put("failed_fits", failed).toString(2));
    }
    private static void line(BufferedWriter writer, String value) throws IOException { writer.write(value); writer.newLine(); }
    @Override public void close() throws IOException {
        IOException failure = null;
        for (BufferedWriter writer : new BufferedWriter[] {predictions, evaluations, fits}) {
            try { writer.close(); } catch (IOException e) { if (failure == null) { failure = e; } else { failure.addSuppressed(e); } }
        }
        if (failure != null) { throw failure; }
    }
}
