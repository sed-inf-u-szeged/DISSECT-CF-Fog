package hu.u_szeged.inf.fog.simulator.iot.mobility;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/** Records observed motion without inspecting future route points or changing device state. */
public final class MobilitySampleRecorder implements AutoCloseable {
    private static final double EARTH_RADIUS_M = 6_378_137.0;
    private static final double HEADING_MIN_DISTANCE_M = 0.000001;
    private static final String HEADER = "run_id,device_id,time_ms,latitude_deg,longitude_deg,"
            + "delta_time_s,displacement_m,observed_speed_m_s,heading_deg,motion_valid,heading_valid,current_node";
    private final BufferedWriter writer;
    private final String runId;
    private final Map<String, Observation> previous = new HashMap<>();
    private long sampleCount;

    private static final class Observation {
        final long time;
        final double latitude;
        final double longitude;

        Observation(long time, double latitude, double longitude) {
            this.time = time;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /** Creates a new UTF-8 CSV; refuses to overwrite an existing dataset. */
    public MobilitySampleRecorder(Path output, String runId) throws IOException {
        this.runId = requireId(runId);
        writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            writer.write(HEADER);
            writer.newLine();
        } catch (IOException error) {
            writer.close();
            throw error;
        }
    }

    /**
     * Appends one observation after movement. History is separate for each device.
     * Speed is endpoint displacement divided by elapsed time, not instantaneous path speed.
     * The first sample has empty motion fields; stationary samples have no heading.
     */
    public void record(String deviceId, long timeMs, double latitude, double longitude,
            String currentNode) throws IOException {
        recordAndGet(deviceId, timeMs, latitude, longitude, currentNode);
    }

    /** Records and returns the exact CSV row for a synchronous observation consumer. */
    public String recordAndGet(String deviceId, long timeMs, double latitude, double longitude,
            String currentNode) throws IOException {
        requireId(deviceId);
        requireId(currentNode);
        if (timeMs < 0 || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid mobility observation coordinates or time");
        }
        Observation last = previous.get(deviceId);
        String delta = "";
        String displacement = "";
        String speed = "";
        String heading = "";
        boolean headingValid = false;
        if (last != null) {
            if (timeMs <= last.time) {
                throw new IllegalArgumentException("Observation time must increase for device " + deviceId);
            }
            double seconds = (timeMs - last.time) / 1000.0;
            double lat1 = Math.toRadians(last.latitude);
            double lat2 = Math.toRadians(latitude);
            double dLat = lat2 - lat1;
            double dLon = Math.toRadians(longitude - last.longitude);
            double a = Math.pow(Math.sin(dLat / 2), 2)
                    + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
            a = Math.max(0, Math.min(1, a));
            double meters = EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            double y = Math.sin(dLon) * Math.cos(lat2);
            double x = Math.cos(lat1) * Math.sin(lat2)
                    - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
            // No meaningful heading for identical or antipodal endpoints, or a polar origin.
            headingValid = meters > HEADING_MIN_DISTANCE_M && Math.hypot(x, y) > 1e-15
                    && Math.abs(last.latitude) < 90;
            if (headingValid) {
                heading = Double.toString((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
            }
            delta = Double.toString(seconds);
            displacement = Double.toString(meters);
            speed = Double.toString(meters / seconds);
        }
        String row = csv(runId) + "," + csv(deviceId) + "," + timeMs + "," + latitude + "," + longitude
                + "," + delta + "," + displacement + "," + speed + "," + heading + ","
                + (last != null) + "," + headingValid + "," + csv(currentNode);
        writer.write(row);
        writer.newLine();
        // Store primitive snapshots: simulator GeoLocation instances can be mutable.
        previous.put(deviceId, new Observation(timeMs, latitude, longitude));
        sampleCount++;
        return row;
    }

    /** Returns the number of successfully appended observations. */
    public long getSampleCount() {
        return sampleCount;
    }

    private static String requireId(String value) {
        if (value == null || value.trim().isEmpty() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Non-empty, single-line mobility identifier required");
        }
        return value;
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
