package hu.u_szeged.inf.fog.simulator.prediction;

import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.EvaluationMetrics;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleCoordinator;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecyclePolicy;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleState;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.ShadowPrediction;
import hu.u_szeged.inf.fog.simulator.prediction.settings.OnlineTrainingSettings;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Causal load-model adapter on the shared node clock. No resource control. */
public final class NodeLoadLearning implements AutoCloseable {
    static final int INPUT = 192;
    static final int OUTPUT = 64;
    private static final long INTERVAL = 60000;
    interface Backend extends AutoCloseable {
        JSONObject request(JSONObject request) throws IOException, JSONException;
        @Override void close() throws IOException;
    }

    private final Path output;
    private final String runId;
    private final List<String> methods;
    private final String profile;
    private final int epochs;
    private final Backend backend;
    private final NodeLoadBaselines baselines;
    private final LifecyclePolicy policy;
    private final OnlineTrainingSettings training = OnlineTrainingSettings.getDefault();
    private final LifecycleManager manager = LifecycleManager.getInstance();
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Version> versions = new ArrayList<>();
    private final BufferedWriter predictions;
    private final BufferedWriter evaluations;
    private final BufferedWriter selections;
    private final BufferedWriter events;
    private NodeLoadListener listener = NodeLoadListener.NONE;

    public void setListener(NodeLoadListener listener) { this.listener = java.util.Objects.requireNonNull(listener); if (baselines != null) { baselines.setListener(listener); } }

    /** The live stream uses the same policy and model identities as the saved run. */
    public JSONObject liveMetadata(JSONArray nodeIds) throws JSONException {
        return new JSONObject().put("run_id", runId).put("time_ms", 0).put("interval_ms", INTERVAL)
                .put("baselines", new JSONArray(NodeLoadBaselines.METHODS)).put("schema_version", 2).put("node_ids", nodeIds).put("models", new JSONArray(methods)).put("profile", profile)
                .put("policy", new JSONObject().put("matched_targets", 64)
                    .put("minimum_within_tolerance_percent", policy.getMinimumAccuracy())
                    .put("relative_tolerance", policy.getAccuracyTolerance()).put("absolute_tolerance_bytes", .10));
    }

    public static NodeLoadLearning fromEnvironment(Path output) throws IOException, JSONException {
        JSONArray enabled = new JSONArray(System.getenv().getOrDefault("NODE_LOAD_MODELS", "[]"));
        List<String> models = new ArrayList<>();
        for (int i = 0; i < enabled.length(); i++) { models.add(enabled.getString(i)); }
        String profile = System.getenv().getOrDefault("NODE_LOAD_PROFILE", "candidate");
        int epochs = Integer.parseInt(System.getenv().getOrDefault("NODE_LOAD_EPOCHS", "40"));
        String python = System.getenv("MOBILITY_SHADOW_PYTHON");
        String script = System.getenv("NODE_LOAD_SCRIPT");
        if (!models.isEmpty() && (python == null || script == null)) {
            throw new IOException("Node-load models require the audited mobility runner");
        }
        return new NodeLoadLearning(output, models, profile, epochs,
                python == null || script == null ? null : new NodeLoadModelWorker(python, script, output, epochs));
    }

    NodeLoadLearning(Path output, List<String> methods, String profile, int epochs, Backend backend)
            throws IOException {
        if (!Arrays.asList("candidate", "observe").contains(profile) || epochs < 1 || epochs > 200
                || methods.stream().distinct().count() != methods.size()
                || methods.stream().anyMatch(m -> !Arrays.asList("LSTM", "TCN", "GRU", "PatchTST").contains(m))) {
            throw new IllegalArgumentException("Invalid node-load settings");
        }
        this.output = output; this.runId = output.getFileName().toString(); this.methods = new ArrayList<>(methods);
        this.profile = profile; this.epochs = epochs; this.backend = backend;
        policy = new LifecyclePolicy(64, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, 200,
                "candidate".equals(profile) ? 80 : 0, "candidate".equals(profile) ? .20 : .10, .10);
        if (backend == null) { baselines = null; predictions = evaluations = selections = events = null; return; }
        baselines = new NodeLoadBaselines(output, backend);
        predictions = Files.newBufferedWriter(output.resolve("node_load_model_predictions.csv"));
        evaluations = Files.newBufferedWriter(output.resolve("node_load_model_evaluations.csv"));
        selections = Files.newBufferedWriter(output.resolve("node_load_model_selection.csv"));
        events = Files.newBufferedWriter(output.resolve("node_load_model_events.jsonl"));
        line(predictions, "run_id,node_id,model,version,origin_index,origin_ms,target_index,target_ms,lead_steps,predicted_bytes,inference_vector_ms");
        line(evaluations, "run_id,node_id,model,version,origin_index,target_index,target_ms,predicted_bytes,actual_bytes,absolute_error_bytes");
        line(selections, "run_id,node_id,selection_index,selection_ms,target_index,target_ms,model,version,forecast_origin_index,lead_steps,predicted_bytes");
    }

    private final class Node {
        final String id;
        final String feature;
        final List<Double> history = new ArrayList<>();
        final Map<String, Family> families = new LinkedHashMap<>();
        Node(String id) {
            this.id = id; feature = "node-load/" + runId + "/" + id;
            LifecycleCoordinator baseline = new LifecycleCoordinator("LR", feature, policy);
            baseline.setAutomaticPromotionAllowed(false);
            manager.registerCoordinator(baseline); manager.activateBaseline("LR", feature);
            for (String method : methods) { families.put(method, new Family(method)); }
        }
    }

    private final class Family {
        final String method;
        int window = training.getInitialWindowPoints(INPUT, OUTPUT);
        int lastAttempt = -1;
        int lastEvaluation = -1;
        int number;
        Version current;
        Family(String method) { this.method = method; }
    }

    private final class Version {
        final Node node;
        final String method;
        final String version;
        final String name;
        final int trainedAt;
        final int window;
        final LifecycleCoordinator coordinator;
        final Map<Integer, Forecast> pending = new LinkedHashMap<>();
        String qualification = "AWAITING_64_TARGETS";
        int matched;
        int forecasts;
        int lastOrigin = -1;
        int lastMatched = -1;
        int inferenceCalls;
        double trainingSeconds;
        double inferenceTotalMs;
        double absolute;
        double squared;
        Version(Node node, Family family, int index) {
            this.node = node; method = family.method; version = String.format(java.util.Locale.ROOT, "v%04d", ++family.number);
            name = method + "-" + version; trainedAt = index; window = family.window;
            coordinator = new LifecycleCoordinator(name, node.feature, policy);
            coordinator.setAutomaticPromotionAllowed(false); coordinator.setMonitorWhileReady(true);
            manager.registerCoordinator(coordinator);
        }
    }

    private static final class Forecast {
        final int origin;
        final double predicted;
        Forecast(int origin, double predicted) {
            this.origin = origin; this.predicted = predicted;
        }
    }

    public void observe(String nodeId, long sampleIndex, long time, double actual) throws IOException {
        if (backend == null) { return; }
        if (sampleIndex < 0 || sampleIndex > Integer.MAX_VALUE || time != sampleIndex * INTERVAL
                || !Double.isFinite(actual) || actual < 0 || !nodeId.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException("Invalid node-load sample");
        }
        int index = (int) sampleIndex;
        Node node = nodes.computeIfAbsent(nodeId, Node::new);
        if (index != node.history.size()) { throw new IllegalArgumentException("Node-load grid must be contiguous"); }
        node.history.add(actual);
        try {
            baselines.observe(node.id, index, node.history);
            // Settle all issued forecasts, including tails from a degraded model.
            for (Version version : versions) {
                if (version.node != node) { continue; }
                Forecast forecast = version.pending.remove(index);
                if (forecast == null) { continue; }
                double error = Math.abs(forecast.predicted - actual);
                version.matched++; version.lastMatched = index;
                version.absolute += error; version.squared += error * error;
                line(evaluations, runId + "," + node.id + "," + version.method + "," + version.version + ","
                        + forecast.origin + "," + index + "," + time + "," + forecast.predicted + "," + actual
                        + "," + error);
                listener.emit("EVALUATION", new JSONObject().put("node_id", node.id).put("model", version.method)
                        .put("version", version.version).put("time_ms", time).put("origin_index", forecast.origin)
                        .put("target_index", index).put("actual_bytes", actual));
                LifecycleCoordinator coordinator = version.coordinator;
                if (coordinator.canCollectEvaluation()) {
                    LifecycleState before = coordinator.getState();
                    coordinator.resolveActualValue(index, actual); coordinator.evaluate();
                    Family family = node.families.get(version.method);
                    if (before == LifecycleState.EVALUATING && coordinator.hasCompleteEvaluationWindow()) {
                        family.lastEvaluation = node.history.size();
                        if (coordinator.isPolicySatisfied()) {
                            version.qualification = "PASSED"; event("QUALIFIED", version, index);
                        } else {
                            version.qualification = "REJECTED"; event("REJECTED", version, index);
                            coordinator.markFailed(); family.window = OnlineTrainingSchedule.afterRejection(family.window, training);
                        }
                    } else if (coordinator.getState() == LifecycleState.DEGRADED) {
                        family.lastEvaluation = node.history.size(); event("DEGRADED", version, index);
                        if (manager.isActive(version.name, node.feature)) {
                            manager.fallbackToBaseline(node.feature); event("FALLBACK", version, index);
                        }
                    }
                }
            }
            for (Family family : node.families.values()) {
                Version current = family.current;
                boolean pending = current != null && current.coordinator.getState() == LifecycleState.EVALUATING;
                boolean usable = current != null && (current.coordinator.getState() == LifecycleState.ACTIVE
                        || current.coordinator.getState() == LifecycleState.READY);
                if (node.history.size() >= family.window && OnlineTrainingSchedule.needsTraining(node.history.size(),
                        family.lastAttempt, family.lastEvaluation, current != null, current != null && !usable && !pending,
                        usable, pending, training)) {
                    family.lastAttempt = node.history.size();
                    current = new Version(node, family, index); family.current = current; versions.add(current);
                    current.coordinator.startTraining(); event("TRAINING", current, index);
                    JSONObject reply = backend.request(request("TRAIN", current, index, family.window).put("window_points", family.window));
                    current.trainingSeconds = reply.getDouble("training_seconds");
                    if (reply.getInt("training_windows") != family.window - INPUT - OUTPUT + 1) {
                        throw new IOException("Unexpected load training window count");
                    }
                    current.coordinator.startEvaluation(); event("TRAINED", current, index);
                }
                if (current != null && current.coordinator.canCollectEvaluation()
                        && (current.lastOrigin < 0 || index - current.lastOrigin >= OUTPUT)) {
                    issue(current, index, actual);
                }
            }
            // Compare qualified families only while their latest 64 target times align.
            for (Family family : node.families.values()) {
                Version current = family.current;
                if (current == null || current.coordinator.getState() != LifecycleState.READY || current.lastMatched != index) { continue; }
                String active = manager.getActivePredictorName(node.feature);
                Version activeVersion = versions.stream().filter(v -> v.node == node && v.name.equals(active)).findFirst().orElse(null);
                if ((activeVersion == null || activeVersion.lastMatched == index) && manager.promoteIfBetter(current.name, node.feature)) {
                    event("PROMOTED", current, index);
                }
            }
            String active = manager.getActivePredictorName(node.feature);
            Version chosen = versions.stream().filter(v -> v.node == node && v.name.equals(active)).findFirst().orElse(null);
            Forecast selected = chosen == null ? null : chosen.pending.get(index + 1);
            if (chosen != null && selected == null) { throw new IOException("Active model has no next-target forecast"); }
            NodeLoadBaselines.Forecast baseline = selected == null ? baselines.selected(node.id, index + 1) : null;
            if (selected == null && baseline == null) {
                if (index < INPUT - 1) { return; } // Honest baseline warm-up: no fabricated prediction.
                throw new IOException("No usable load baseline for " + node.id + " at " + index);
            }
            String selectedMethod = chosen == null ? baseline.method : chosen.method;
            String selectedVersion = chosen == null ? NodeLoadBaselines.VERSION : chosen.version;
            int origin = chosen == null ? baseline.origin : selected.origin;
            double value = chosen == null ? baseline.value : selected.predicted;
            line(selections, runId + "," + node.id + "," + index + "," + time + "," + (index + 1) + "," + ((index + 1L) * INTERVAL)
                    + "," + selectedMethod + "," + selectedVersion + "," + origin + "," + (index + 1 - origin) + "," + value);
            listener.emit("SELECTION", new JSONObject().put("node_id", node.id).put("time_ms", time)
                    .put("model", selectedMethod).put("version", selectedVersion)
                    .put("origin_index", origin).put("target_index", index + 1));
        } catch (JSONException error) { throw new IOException("Invalid node-load worker reply", error); }
    }

    private JSONObject request(String operation, Version version, int index, int count) throws JSONException {
        List<Double> values = version.node.history.subList(index - count + 1, index + 1);
        return new JSONObject().put("op", operation).put("node", version.node.id).put("model", version.method)
                .put("version", version.version).put("origin_index", index).put("values", new JSONArray(values));
    }

    private void issue(Version version, int index, double actual) throws IOException, JSONException {
        baselines.issue(version.node.id, index, version.node.history);
        JSONObject reply = backend.request(request("PREDICT", version, index, INPUT));
        JSONArray values = reply.getJSONArray("values");
        double ms = reply.getDouble("inference_ms");
        if (values.length() != OUTPUT || !Double.isFinite(ms) || ms < 0) { throw new IOException("Invalid load forecast vector"); }
        // Validate the entire vector before saving or publishing any of its targets.
        for (int i = 0; i < OUTPUT; i++) {
            double value = values.getDouble(i);
            if (!Double.isFinite(value) || value < 0) { throw new IOException("Invalid load prediction value"); }
        }
        version.lastOrigin = index; version.inferenceCalls++; version.inferenceTotalMs += ms;
        for (int offset = 1; offset <= OUTPUT; offset++) {
            double predicted = values.getDouble(offset - 1);
            if (!Double.isFinite(predicted) || predicted < 0) { throw new IOException("Invalid load prediction value"); }
            int target = index + offset;
            version.pending.put(target, new Forecast(index, predicted)); version.forecasts++;
            version.coordinator.registerPrediction(new ShadowPrediction(version.name, version.node.feature,
                    version.inferenceCalls, target, predicted));
            line(predictions, runId + "," + version.node.id + "," + version.method + "," + version.version + "," + index
                    + "," + (index * INTERVAL) + "," + target + "," + (target * INTERVAL) + "," + offset
                    + "," + predicted + "," + ms);
        }
        listener.emit("VECTOR", new JSONObject().put("node_id", version.node.id).put("model", version.method)
                .put("version", version.version).put("time_ms", index * INTERVAL).put("origin_index", index)
                .put("values", values).put("inference_vector_ms", ms));
    }

    private JSONObject metrics(EvaluationMetrics metrics) throws JSONException {
        if (metrics == null) { return new JSONObject(); }
        return new JSONObject().put("matched", metrics.getMatchedSamples()).put("mae_bytes", metrics.getMae())
                .put("rmse_bytes", metrics.getRmse()).put("smape_percent", metrics.getSmape())
                .put("within_tolerance_percent", metrics.getAccuracy());
    }

    private void event(String kind, Version version, int index) throws IOException, JSONException {
        JSONObject record = new JSONObject().put("run_id", runId).put("domain", "NODE_LOAD").put("node_id", version.node.id)
                .put("model", version.method).put("version", version.version).put("event", kind)
                .put("sample_index", index).put("time_ms", index * INTERVAL).put("state", version.coordinator.getState().name())
                .put("metrics", metrics(version.coordinator.getLatestMetrics()));
        line(events, record.toString());
        events.flush();
        listener.emit("EVENT", new JSONObject(record.toString()).put("training_windows", version.window - 255)
                .put("trained_at_ms", version.trainedAt * INTERVAL).put("training_seconds", version.trainingSeconds));
    }

    public void finish() throws IOException, JSONException {
        if (backend == null) { return; }
        baselines.finish();
        JSONArray records = new JSONArray();
        for (Version v : versions) {
            records.put(new JSONObject().put("node_id", v.node.id).put("model", v.method).put("version", v.version)
                    .put("trained_at_index", v.trainedAt).put("raw_training_points", v.window).put("training_windows", v.window - 255)
                    .put("training_seconds", v.trainingSeconds).put("first_qualification", v.qualification)
                    .put("final_state", v.coordinator.getState().name()).put("forecasts", v.forecasts).put("matched", v.matched)
                    .put("unmatched", v.pending.size()).put("inference_calls", v.inferenceCalls)
                    .put("mean_vector_inference_ms", v.inferenceCalls == 0 ? JSONObject.NULL : v.inferenceTotalMs / v.inferenceCalls)
                    .put("mae_bytes", v.matched == 0 ? JSONObject.NULL : v.absolute / v.matched)
                    .put("rmse_bytes", v.matched == 0 ? JSONObject.NULL : Math.sqrt(v.squared / v.matched))
                    .put("latest_quality_window", metrics(v.coordinator.getLatestMetrics())));
        }
        JSONObject serving = new JSONObject();
        for (Node node : nodes.values()) { serving.put(node.id, manager.getActivePredictorName(node.feature)); }
        JSONObject summary = new JSONObject().put("schema_version", 2).put("baselines", new JSONArray(NodeLoadBaselines.METHODS)).put("baseline_service", "LR").put("run_id", runId).put("status", "COMPLETE")
                .put("target", "waiting_assignment_bytes").put("units", "B").put("interval_ms", INTERVAL)
                .put("input_points", INPUT).put("output_points", OUTPUT).put("forecast_stride", OUTPUT)
                .put("minimum_training_windows", 32).put("minimum_new_points", 64).put("window_growth_points", 64)
                .put("model_scope", "PER_RUN_PER_NODE_PER_FAMILY").put("models", new JSONArray(methods)).put("epochs", epochs)
                .put("profile", profile).put("quality_standard", "EXPERIMENTAL_UNCALIBRATED")
                .put("quality_policy", new JSONObject().put("matched_targets", 64).put("minimum_within_tolerance_percent", policy.getMinimumAccuracy())
                    .put("relative_tolerance", policy.getAccuracyTolerance()).put("absolute_tolerance_bytes", .10)
                    .put("maximum_smape_percent", 200).put("mae_mse_rmse_limits", "UNBOUNDED"))
                .put("resource_control", false).put("connection_control", false).put("offload_control", false)
                .put("versions", records).put("final_selected", serving);
        Files.writeString(output.resolve("node_load_model_summary.json"), summary.toString(2));
        System.out.println("NODE_LOAD_MODELS_RECORDED versions=" + versions.size() + " profile=" + profile);
    }

    private static void line(BufferedWriter writer, String text) throws IOException { writer.write(text); writer.newLine(); }

    @Override public void close() throws IOException {
        IOException failure = null;
        for (AutoCloseable resource : new AutoCloseable[] {predictions, evaluations, selections, events, baselines, backend}) {
            if (resource == null) { continue; }
            try { resource.close(); } catch (Exception error) {
                if (failure == null) { failure = new IOException(error); } else { failure.addSuppressed(error); }
            }
        }
        if (failure != null) { throw failure; }
    }
}
