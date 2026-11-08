package hu.u_szeged.inf.fog.simulator.prediction.mobility;

import hu.u_szeged.inf.fog.simulator.prediction.OnlineTrainingSchedule;
import hu.u_szeged.inf.fog.simulator.prediction.settings.OnlineTrainingSettings;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.EvaluationMetrics;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleAssessment;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleAssessmentSource;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleCoordinator;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecyclePolicy;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleState;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

/**
 * Joins live position forecasts to the shared lifecycle coordinator and manager.
 * Java owns actual positions, target matching, qualification, active selection and
 * fallback. Closed-loop mode also issues training commands using the same
 * eligibility/growth rules as the load controller; Python executes frozen fits.
 * Selected position forecasts are recorded, but never change device connections.
 */
public final class MobilityLifecycleAdapter implements AutoCloseable {
    public static final String PROTOCOL = "MOBILITY_SHARED_LIFECYCLE_V1";
    public static final String CLOSED_LOOP_PROTOCOL = "MOBILITY_CLOSED_LOOP_V1";
    private final boolean closedLoop;
    private final OnlineTrainingSettings trainingSettings = OnlineTrainingSettings.getDefault();
    private int trainingWindow = 32;
    private int lastAttempt = -1;
    private int lastEvaluation = -1;
    private int degradationRetrainings;
    private boolean controlIssued;
    private JSONObject trainingCommand;
    private static final long STEP = 60_000;
    private static final int BLOCK = 64;
    private static final String[] METHODS = {"HoldPosition", "ConstantVelocity", "LSTM"};
    private final LifecycleManager manager;
    private final Path output;
    private final String runId;
    private final String deviceId;
    private final String feature;
    private final String baseline;
    private final int expected;
    private final Map<String, Version> versions = new LinkedHashMap<>();
    private BufferedWriter events;
    private BufferedWriter selected;
    private BufferedWriter selectedEvaluations;
    private Version current;
    private Forecast pending;
    private Selection pendingSelection;
    private double[] actual;
    private long now = -1;
    private int observations;
    private int sequence;
    private int protocolSequence;
    private int selectedCount;
    private int selectedMatched;
    private int lstmSelections;
    private int promotions;
    private int fallbacks;
    private boolean observationClosed = true;
    private boolean forecastAllowed;
    private boolean forecastReceived;
    private boolean finished;
    private boolean failed;

    /** The manager is shared with load prediction; no global reset is performed. */
    public MobilityLifecycleAdapter(LifecycleManager manager, Path output,
            String runId, String deviceId, int expectedObservations) throws IOException, JSONException {
        this(manager, output, runId, deviceId, expectedObservations, false);
    }

    /** Enable Java-issued fits without changing the legacy shared shadow probe. */
    public MobilityLifecycleAdapter(LifecycleManager manager, Path output,
            String runId, String deviceId, int expectedObservations, boolean closedLoop) throws IOException, JSONException {
        this.closedLoop = closedLoop;
        require(manager != null && output != null && Files.isDirectory(output)
                && runId != null && !runId.isBlank() && deviceId != null && !deviceId.isBlank()
                && expectedObservations >= 105 && expectedObservations <= 1441,
                "Invalid mobility lifecycle settings");
        this.manager = manager;
        this.output = output;
        this.runId = runId;
        this.deviceId = deviceId;
        expected = expectedObservations;
        // Length prefixes avoid collisions between run/device identity components.
        feature = "mobility:" + runId.length() + ":" + runId + ":" + deviceId.length() + ":" + deviceId + ":position";
        baseline = feature + ":HoldPosition";
        require(manager.getCoordinator(baseline, feature) == null
                && !Files.exists(output.resolve("shared-lifecycle.json")), "Existing mobility lifecycle session");
        try {
            events = open("shared_lifecycle_events.jsonl");
            selected = open("shared_selected_predictions.csv");
            selectedEvaluations = open("shared_selected_evaluations.csv");
            selected.write("origin_ms,target_ms,model,model_version,predictor_name,predicted_latitude_deg,predicted_longitude_deg\n");
            selectedEvaluations.write("origin_ms,target_ms,model,model_version,actual_latitude_deg,actual_longitude_deg,position_error_m\n");
            LifecycleCoordinator base = new LifecycleCoordinator(baseline, feature, policy());
            manager.registerCoordinator(base);
            manager.activateBaseline(baseline, feature);
            save("RUNNING", null);
        } catch (IOException | JSONException | RuntimeException error) {
            closeWriters();
            throw error;
        }
    }

    private BufferedWriter open(String name) throws IOException, JSONException {
        return Files.newBufferedWriter(output.resolve(name), StandardOpenOption.CREATE_NEW);
    }

    private static LifecyclePolicy policy() {
        // Only minimumMatchedSamples applies to domain assessments. The original
        // scalar policy and its relative/absolute tolerances remain unchanged.
        return new LifecyclePolicy(BLOCK, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 0, 0);
    }

    /** Settle old forecasts using the real simulator observation before any worker reply. */
    public void observe(String run, String device, long time, double latitude, double longitude,
            boolean allowForecast) throws IOException, JSONException {
        double[] point = point(latitude, longitude);
        require(!finished && !failed && observationClosed && runId.equals(run) && deviceId.equals(device)
                && time >= 0 && (now < 0 || time == now + STEP) && observations < expected
                && allowForecast == (observations + 1 < expected), "Invalid lifecycle observation order or identity");
        require(pending == null || pending.target == time, "Unmatched forecast target");
        require(pendingSelection == null || pendingSelection.target == time, "Unmatched selected target");
        now = time;
        actual = point;
        observations++;
        forecastAllowed = allowForecast;
        forecastReceived = false;
        controlIssued = false;
        trainingCommand = null;
        observationClosed = false;
        event("OBSERVATION", new JSONObject().put("latitude_deg", latitude).put("longitude_deg", longitude));
        if (pendingSelection != null) {
            Selection selection = pendingSelection;
            double error = distance(selection.point, point);
            selectedEvaluations.write(selection.origin + "," + time + "," + selection.model + ","
                    + selection.version + "," + latitude + "," + longitude + "," + error + "\n");
            selectedEvaluations.flush();
            selectedMatched++;
            event("SELECTED_EVALUATION", new JSONObject().put("origin_ms", selection.origin)
                    .put("model", selection.model).put("model_version", selection.version).put("position_error_m", error));
            pendingSelection = null;
        }
        if (pending != null) {
            Version version = versions.get(pending.version);
            double[] errors = new double[METHODS.length];
            for (int i = 0; i < errors.length; i++) { errors[i] = distance(pending.points[i], point); }
            version.source.append(errors);
            version.matched++;
            event("EVALUATION", new JSONObject().put("model_version", version.name)
                    .put("origin_ms", pending.origin).put("errors_m", new JSONArray(errors))
                    .put("matched_origins", version.matched));
            pending = null;
            if (version.decision == null) {
                version.coordinator.evaluate();
                if (version.coordinator.hasCompleteEvaluationWindow()) {
                    boolean pass = version.coordinator.isPolicySatisfied();
                    version.decision = pass ? "PASS" : "REJECT";
                    version.decidedAt = time;
                    version.quality = version.source.report();
                    event("QUALITY_DECISION", new JSONObject(version.quality.toString())
                            .put("model_version", version.name).put("decision", version.decision));
                    if (pass) {
                        manager.promote(version.predictor, feature);
                        promotions++;
                        event("PROMOTED", new JSONObject().put("model_version", version.name)
                                .put("active_predictor", manager.getActivePredictorName(feature)));
                    } else {
                        version.coordinator.markFailed();
                        if (closedLoop) {
                            trainingWindow = OnlineTrainingSchedule.afterRejection(trainingWindow, trainingSettings);
                        }
                        event("REJECTED", new JSONObject().put("model_version", version.name));
                    }
                }
            } else if (version.coordinator.getState() == LifecycleState.ACTIVE) {
                version.coordinator.evaluate();
                version.monitored++;
                event("MONITORED", new JSONObject(version.source.report().toString())
                        .put("model_version", version.name).put("state", version.coordinator.getState().name()));
                if (version.coordinator.getState() == LifecycleState.DEGRADED) {
                    require(manager.fallbackToBaseline(feature), "Shared manager did not restore baseline");
                    fallbacks++;
                    event("FALLBACK", new JSONObject().put("model_version", version.name)
                            .put("active_predictor", manager.getActivePredictorName(feature))
                            .put("reason", "RELATIVE_QUALITY_DEGRADED"));
                }
            }
        }
    }

    public boolean isClosedLoop() { return closedLoop; }

    /** Send one decision after matching actual targets, before accepting training. */
    public String control() throws IOException, JSONException {
        require(closedLoop && !observationClosed && !controlIssued && !failed && !finished,
                "Training control outside the current observation");
        int available = Math.max(0, observations - 9);
        LifecycleState state = current == null ? null : current.coordinator.getState();
        boolean retry = state == LifecycleState.FAILED || state == LifecycleState.DEGRADED;
        boolean eligible = forecastAllowed && available >= trainingWindow
                && OnlineTrainingSchedule.needsTraining(available, lastAttempt, lastEvaluation,
                        current != null, retry, state == LifecycleState.ACTIVE,
                        state == LifecycleState.TRAINING || state == LifecycleState.EVALUATING, trainingSettings);
        String reason = !forecastAllowed ? "STREAM_COMPLETE" : eligible
                ? current == null ? "INITIAL" : state == LifecycleState.DEGRADED ? "DEGRADATION" : "REJECTION"
                : current == null ? "WARMUP" : state == LifecycleState.ACTIVE ? "ACTIVE" : retry ? "WAITING_FOR_NEW_DATA" : "EVALUATING";
        JSONObject details = new JSONObject().put("action", eligible ? "TRAIN" : "HOLD").put("reason", reason)
                .put("available_windows", available).put("window_size", trainingWindow)
                .put("request_number", eligible ? versions.size() + 1 : 0)
                .put("model_version", eligible ? String.format(Locale.ROOT, "v%04d", versions.size() + 1) : "")
                .put("previous_version", current == null ? "" : current.name)
                .put("previous_state", state == null ? "NONE" : state.name());
        JSONObject command = new JSONObject(details.toString()).put("sequence", observations)
                .put("sim_time_ms", now).put("run_id", runId).put("device_id", deviceId);
        event("CONTROL", details);
        controlIssued = true;
        trainingCommand = eligible ? command : null;
        return command.toString();
    }

    /** Accept one bounded protocol message after the current actual observation. */
    public void accept(String message) throws IOException, JSONException {
        require(!observationClosed && !failed && !finished && message != null && message.length() <= 4096,
                "Lifecycle message outside an observation");
        JSONObject row = new JSONObject(message);
        require(row.getInt("sequence") == protocolSequence + 1 && row.getLong("sim_time_ms") == now
                && runId.equals(row.getString("run_id")) && deviceId.equals(row.getString("device_id")),
                "Lifecycle protocol identity, time or sequence mismatch");
        String kind = row.getString("event");
        if ("TRAINING_STARTED".equals(kind)) {
            int request = row.getInt("request_number");
            int count = row.getInt("training_windows");
            String version = row.getString("model_version");
            boolean permitted;
            if (closedLoop) {
                permitted = controlIssued && trainingCommand != null
                        && request == trainingCommand.getInt("request_number")
                        && version.equals(trainingCommand.getString("model_version"))
                        && count == trainingCommand.getInt("window_size");
            } else {
                permitted = request == versions.size() + 1 && count == 32 + BLOCK * (request - 1)
                        && count == observations - 9 && (current == null || ("REJECT".equals(current.decision)
                        && current.matched == BLOCK && current.coordinator.getState() == LifecycleState.FAILED));
            }
            require(permitted && row.getLong("latest_label_ms") == now
                    && version.equals(String.format(Locale.ROOT, "v%04d", request)) && forecastAllowed
                    && !forecastReceived, "Training requires the authorized mature window and current lifecycle decision");
            Version candidate = new Version(version, count, now, feature);
            manager.registerCoordinator(candidate.coordinator);
            candidate.coordinator.startTraining();
            versions.put(version, candidate);
            current = candidate;
            if (closedLoop) {
                candidate.trainingReason = trainingCommand.getString("reason");
                if ("DEGRADATION".equals(candidate.trainingReason)) { degradationRetrainings++; }
                lastAttempt = observations - 9;
                trainingCommand = null;
            }
            event(kind, new JSONObject().put("model_version", version).put("training_windows", count));
        } else if ("TRAINING_COMPLETE".equals(kind)) {
            require(current != null && current.name.equals(row.getString("model_version"))
                    && current.cutoff == now && current.count == row.getInt("training_windows")
                    && "FROZEN".equals(row.getString("scaler"))
                    && current.coordinator.getState() == LifecycleState.TRAINING,
                    "Training completion does not match the pending version");
            current.coordinator.startEvaluation();
            if (closedLoop) { lastEvaluation = observations - 9; }
            event(kind, new JSONObject().put("model_version", current.name).put("training_windows", current.count));
        } else if ("FORECAST".equals(kind)) {
            require(current != null && forecastAllowed && !forecastReceived && pending == null
                    && current.name.equals(row.getString("model_version"))
                    && now >= current.cutoff && row.getLong("target_ms") == now + STEP
                    && (current.coordinator.getState() == LifecycleState.EVALUATING
                    || current.coordinator.getState() == LifecycleState.ACTIVE
                    || current.coordinator.getState() == LifecycleState.DEGRADED),
                    "Forecast before training, after rejection or for a different version");
            JSONArray supplied = row.getJSONArray("predictions");
            require(supplied.length() == METHODS.length, "Expected all three methods atomically");
            double[][] points = new double[METHODS.length][];
            for (int i = 0; i < METHODS.length; i++) {
                JSONObject prediction = supplied.getJSONObject(i);
                require(METHODS[i].equals(prediction.getString("model")), "Duplicate or unordered forecast methods");
                points[i] = point(prediction.getDouble("latitude_deg"), prediction.getDouble("longitude_deg"));
            }
            require(distance(points[0], actual) <= 1e-7, "HoldPosition is not the current observation");
            pending = new Forecast(current.name, now, points);
            forecastReceived = true;
            event(kind, new JSONObject().put("model_version", current.name).put("target_ms", now + STEP)
                    .put("predictions", supplied));
        } else {
            throw new IllegalArgumentException("Unknown lifecycle protocol event: " + kind);
        }
        protocolSequence++;
    }

    /** Select the real shared-manager owner after settling, training and qualification. */
    public void endObservation() throws IOException, JSONException {
        require(!observationClosed && !failed && !finished, "Observation already closed");
        require(!closedLoop || (controlIssued && trainingCommand == null), "Missing Java control or commanded training");
        require(current == null ? observations < 41 : current.coordinator.getState() != LifecycleState.TRAINING,
                "Missing completed first fit or incomplete model");
        require(forecastReceived == (current != null && forecastAllowed), "Missing or unexpected lifecycle forecast");
        if (forecastAllowed && observations >= 9) {
            String owner = manager.getActivePredictorName(feature);
            boolean useLstm = current != null && current.predictor.equals(owner);
            require(baseline.equals(owner) || (useLstm && current.coordinator.getState() == LifecycleState.ACTIVE),
                    "Unexpected shared active owner");
            String model = useLstm ? "LSTM" : "HoldPosition";
            String version = useLstm ? current.name : "";
            double[] chosen = useLstm ? pending.points[2] : actual;
            Selection selection = new Selection(now, model, version, chosen);
            selected.write(now + "," + (now + STEP) + "," + model + "," + version + ","
                    + csv(owner) + "," + chosen[0] + "," + chosen[1] + "\n");
            selected.flush();
            pendingSelection = selection;
            selectedCount++;
            if (useLstm) { lstmSelections++; current.selected++; }
            event("SELECTED", new JSONObject().put("model", model).put("model_version", version)
                    .put("predictor_name", owner).put("target_ms", now + STEP)
                    .put("latitude_deg", chosen[0]).put("longitude_deg", chosen[1]));
        }
        observationClosed = true;
    }

    public void finish() throws IOException, JSONException {
        require(!finished && !failed && observationClosed && observations == expected && current != null
                && pending == null && pendingSelection == null && selectedMatched == selectedCount
                && selectedCount == expected - 9 && versions.values().stream().mapToInt(v -> v.matched).sum() == expected - 41,
                "Incomplete shared lifecycle stream");
        event("COMPLETE", new JSONObject());
        save("COMPLETE", null);
        finished = true;
    }

    /** Fail the session and remove its active candidate without touching other features. */
    public void fail(String error) throws IOException {
        if (finished || failed) { return; }
        manager.fallbackToBaseline(feature);
        if (current != null) { current.coordinator.markFailed(); }
        failed = true;
        try {
            event("FAILED", new JSONObject().put("error", error));
            save("FAILED", error);
        } catch (JSONException failure) {
            throw new IOException("Cannot write lifecycle failure", failure);
        }
    }

    private void event(String kind, JSONObject details) throws IOException, JSONException {
        details.put("sequence", ++sequence).put("event", kind).put("sim_time_ms", now)
                .put("run_id", runId).put("device_id", deviceId);
        events.write(details.toString());
        events.newLine();
        events.flush();
    }

    private void save(String status, String error) throws IOException, JSONException {
        JSONArray records = new JSONArray();
        for (Version version : versions.values()) {
            records.put(new JSONObject().put("model_version", version.name).put("predictor_name", version.predictor)
                    .put("training_windows", version.count).put("training_cutoff_sim_ms", version.cutoff)
                    .put("matched_origins", version.matched).put("quality_decision", version.decision == null ? "WAITING_FOR_EVALUATION" : version.decision)
                    .put("decided_at_sim_ms", version.decidedAt).put("quality", version.quality == null ? JSONObject.NULL : version.quality)
                    .put("state", version.coordinator.getState().name()).put("monitor_evaluations", version.monitored)
                    .put("selected_origins", version.selected));
        }
        JSONObject result = new JSONObject().put("schema_version", 1).put("status", status)
                .put("run_id", runId).put("device_id", deviceId).put("feature_name", feature)
                .put("controller_integration", "SHARED_COORDINATOR_AND_MANAGER")
                .put("training_scheduler", closedLoop ? "JAVA_SHARED_RULES" : "PYTHON_MOBILITY")
                .put("retraining_after_degradation", closedLoop ? "ENABLED" : "DEFERRED")
                .put("connection_control", false).put("observations", observations).put("expected_observations", expected)
                .put("selected_origins", selectedCount).put("selected_matched", selectedMatched)
                .put("lstm_selected_origins", lstmSelections).put("promotions", promotions).put("fallbacks", fallbacks)
                .put("active_predictor", manager.getActivePredictorName(feature))
                .put("active_lstm", current != null && current.predictor.equals(manager.getActivePredictorName(feature)))
                .put("pending_forecasts", (pending == null ? 0 : 1) + (pendingSelection == null ? 0 : 1))
                .put("versions", records);
        if (closedLoop) {
            result.put("degradation_retrainings", degradationRetrainings).put("training_window_selection", "LATEST_MATURE_WINDOWS");
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.getJSONObject(i);
                record.put("training_reason", versions.get(record.getString("model_version")).trainingReason);
            }
        }
        if (error != null) { result.put("error", error); }
        Files.writeString(output.resolve("shared-lifecycle.json"), result.toString(2) + "\n");
    }

    private static final class Version {
        final String name;
        final String predictor;
        final int count;
        final long cutoff;
        final BlockSource source = new BlockSource();
        final LifecycleCoordinator coordinator;
        int matched;
        int monitored;
        int selected;
        long decidedAt = -1;
        String decision;
        String trainingReason;
        JSONObject quality;

        Version(String name, int count, long cutoff, String feature) {
            this.name = name; this.count = count; this.cutoff = cutoff;
            predictor = feature + ":LSTM:" + name;
            coordinator = new LifecycleCoordinator(predictor, feature, policy(), source);
        }
    }

    private static final class Forecast {
        final String version;
        final long origin;
        final long target;
        final double[][] points;
        Forecast(String version, long origin, double[][] points) {
            this.version = version; this.origin = origin; target = origin + STEP;
            this.points = Arrays.stream(points).map(double[]::clone).toArray(double[][]::new);
        }
    }

    private static final class Selection {
        final long origin;
        final long target;
        final String model;
        final String version;
        final double[] point;
        Selection(long origin, String model, String version, double[] point) {
            this.origin = origin; target = origin + STEP; this.model = model;
            this.version = version; this.point = point.clone();
        }
    }

    /** Latest 64 paired actual-target errors; all three methods share exactly the same block. */
    private static final class BlockSource implements LifecycleAssessmentSource {
        final ArrayDeque<double[]> block = new ArrayDeque<>();
        void append(double[] errors) {
            require(errors.length == 3 && Arrays.stream(errors).allMatch(x -> Double.isFinite(x) && x >= 0),
                    "Invalid paired position errors");
            block.addLast(errors.clone());
            if (block.size() > BLOCK) { block.removeFirst(); }
        }
        double[][] metrics() {
            double[][] values = new double[3][3];
            if (block.isEmpty()) { return values; }
            for (double[] row : block) {
                for (int i = 0; i < 3; i++) { values[i][0] += row[i]; values[i][1] += row[i] * row[i]; }
            }
            for (double[] row : values) { row[0] /= block.size(); row[1] /= block.size(); row[2] = Math.sqrt(row[1]); }
            return values;
        }
        int reference(double[][] values) {
            return values[1][2] < values[0][2] || (values[1][2] == values[0][2] && values[1][0] < values[0][0]) ? 1 : 0;
        }
        @Override
        public LifecycleAssessment assess() {
            double[][] values = metrics();
            int reference = reference(values);
            boolean pass = block.size() == BLOCK && values[2][2] < values[reference][2]
                    && values[2][0] <= values[reference][0];
            return new LifecycleAssessment(new EvaluationMetrics(block.size(), values[2][0], values[2][1], values[2][2],
                    Double.NaN, Double.NaN), pass, "POSITION_ERROR_METRES",
                    block.size() < BLOCK ? "INSUFFICIENT_TARGETS" : pass ? "RELATIVE_QUALITY_PASSED" : "RELATIVE_QUALITY_REJECTED");
        }
        JSONObject report() throws JSONException {
            double[][] values = metrics();
            JSONObject metrics = new JSONObject();
            for (int i = 0; i < 3; i++) {
                metrics.put(METHODS[i], new JSONObject().put("mean_error_m", values[i][0]).put("rmse_m", values[i][2]));
            }
            return new JSONObject().put("evaluated_origins", block.size())
                    .put("reference_baseline", METHODS[reference(values)]).put("metrics", metrics);
        }
        @Override
        public void clear() { block.clear(); }
    }

    private static double[] point(double latitude, double longitude) {
        require(Double.isFinite(latitude) && Double.isFinite(longitude) && Math.abs(latitude) <= 90
                && Math.abs(longitude) <= 180, "Invalid forecast or actual coordinates");
        return new double[] {latitude, longitude};
    }

    /** Great-circle endpoint distance, matching the frozen Python metric (metres). */
    static double distance(double[] first, double[] second) {
        double lat1 = Math.toRadians(first[0]);
        double lat2 = Math.toRadians(second[0]);
        double dlat = lat2 - lat1;
        double dlon = Math.toRadians(second[1] - first[1]);
        double a = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2);
        a = Math.max(0, Math.min(1, a));
        return 6_378_137.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalArgumentException(message); }
    }
    private void closeWriters() throws IOException {
        IOException failure = null;
        for (BufferedWriter writer : new BufferedWriter[] {events, selected, selectedEvaluations}) {
            if (writer != null) {
                try { writer.close(); } catch (IOException error) { failure = error; }
            }
        }
        if (failure != null) { throw failure; }
    }
    @Override
    public void close() throws IOException {
        try { if (!finished && !failed) { fail("Stream closed without successful END"); } }
        finally { closeWriters(); }
    }
}
