package hu.u_szeged.inf.fog.simulator.prediction.mobility;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Causal position baselines independent of learned-model windows and coverage decisions. */
public final class MobilityPositionBaselines implements AutoCloseable {
    private static final long STEP = 60_000;
    private final String runId;
    private final int device;
    private final BufferedWriter predictions;
    private final BufferedWriter evaluations;
    private final List<JSONObject> pending = new ArrayList<>();
    private JSONArray latest = new JSONArray();
    private long previousTime = -1;
    private double previousLat;
    private double previousLon;

    public MobilityPositionBaselines(Path directory, String runId, String deviceId) throws IOException {
        this.runId = runId;
        this.device = Integer.parseInt(deviceId);
        predictions = Files.newBufferedWriter(directory.resolve("position_baseline_predictions.csv"));
        evaluations = Files.newBufferedWriter(directory.resolve("position_baseline_evaluations.csv"));
        predictions.write("run_id,device_id,model,model_version,origin_ms,target_ms,origin_node,predicted_latitude_deg,predicted_longitude_deg\n");
        evaluations.write("run_id,device_id,model,model_version,origin_ms,target_ms,actual_latitude_deg,actual_longitude_deg,position_error_m\n");
    }

    public void observe(long now, double latitude, double longitude, String node, boolean issue) throws IOException {
        try { observeFrame(now, latitude, longitude, node, issue); }
        catch (JSONException error) { throw new IOException("Cannot record position baseline", error); }
    }

    private void observeFrame(long now, double latitude, double longitude, String node, boolean issue) throws IOException, JSONException {
        if (previousTime >= 0 && now - previousTime != STEP) throw new IOException("Position baseline sampling gap");
        latest = new JSONArray();
        for (JSONObject p : pending) {
            if (p.getLong("target_ms") != now) throw new IOException("Position baseline target mismatch");
            double error = distance(p.getDouble("predicted_latitude_deg"), p.getDouble("predicted_longitude_deg"), latitude, longitude);
            JSONObject e = new JSONObject().put("model", p.getString("model")).put("model_version", "baseline-v1")
                    .put("origin_ms", p.getLong("origin_ms")).put("target_ms", now)
                    .put("actual_latitude_deg", latitude).put("actual_longitude_deg", longitude).put("position_error_m", error);
            latest.put(e);
            evaluations.write(runId + "," + device + "," + e.getString("model") + ",baseline-v1," + e.getLong("origin_ms")
                    + "," + now + "," + latitude + "," + longitude + "," + error + "\n");
        }
        pending.clear();
        if (issue) {
            issue("HoldPosition", now, node, latitude, longitude);
            // No assumed velocity at the first observation; only use the last measured displacement.
            if (previousTime >= 0) issue("ConstantVelocity", now, node, 2 * latitude - previousLat, 2 * longitude - previousLon);
        }
        previousTime = now; previousLat = latitude; previousLon = longitude;
    }

    private void issue(String model, long now, String node, double lat, double lon) throws IOException, JSONException {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180)
            throw new IOException("Invalid baseline coordinates");
        JSONObject p = new JSONObject().put("model", model).put("model_version", "baseline-v1").put("device_id", device)
                .put("origin_ms", now).put("target_ms", now + STEP).put("origin_node", node)
                .put("predicted_latitude_deg", lat).put("predicted_longitude_deg", lon);
        pending.add(p);
        predictions.write(runId + "," + device + "," + model + ",baseline-v1," + now + "," + (now + STEP) + "," + node + "," + lat + "," + lon + "\n");
    }

    public JSONObject preview() throws JSONException {
        return new JSONObject().put("predictions", new JSONArray(pending)).put("evaluations", latest);
    }

    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.toRadians(lat1), b = Math.toRadians(lat2);
        double q = Math.pow(Math.sin((a - b) / 2), 2)
                + Math.cos(a) * Math.cos(b) * Math.pow(Math.sin(Math.toRadians(lon1 - lon2) / 2), 2);
        return 12_756_274 * Math.asin(Math.min(1, Math.sqrt(q)));
    }

    @Override public void close() throws IOException {
        try { predictions.close(); } finally { evaluations.close(); }
    }
}
