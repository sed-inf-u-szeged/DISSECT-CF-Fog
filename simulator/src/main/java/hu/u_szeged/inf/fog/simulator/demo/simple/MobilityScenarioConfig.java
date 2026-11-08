package hu.u_szeged.inf.fog.simulator.demo.simple;

import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.NomadicMobilityStrategy;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Configures a closed waypoint route; infrastructure and sampling remain fixed. */
public final class MobilityScenarioConfig {
    private static final String DEFAULT_POINTS = "0,0;1600,0;1600,1200;0,1200;0,0";
    private static final Set<String> KEYS = Set.of("scenario_name", "seed", "speed_m_s", "waypoints_m");
    private final String name;
    private final long seed;
    private final double speed;
    private final List<double[]> offsets;
    private final List<GeoLocation> points;
    private final double length;

    private MobilityScenarioConfig(Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!KEYS.contains(key)) { throw new IllegalArgumentException("Unknown scenario key: " + key); }
        }
        name = properties.getProperty("scenario_name", "rectangle-default").trim();
        if (!name.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("scenario_name must contain 1-64 letters, digits, _ or -");
        }
        seed = Long.parseLong(properties.getProperty("seed", "42").trim());
        speed = Double.parseDouble(properties.getProperty("speed_m_s", "4.1").trim());
        if (!Double.isFinite(speed) || speed < 0.1 || speed > 50) {
            throw new IllegalArgumentException("speed_m_s must be between 0.1 and 50");
        }
        String[] rows = properties.getProperty("waypoints_m", DEFAULT_POINTS).trim().split(";", -1);
        if (rows.length < 3 || rows.length > 100) {
            throw new IllegalArgumentException("Use 3-100 waypoints, including the repeated start");
        }
        offsets = new ArrayList<>();
        points = new ArrayList<>();
        for (String row : rows) {
            String[] values = row.trim().split(",", -1);
            if (values.length != 2) { throw new IllegalArgumentException("Expected east_m,north_m: " + row); }
            double east = Double.parseDouble(values[0].trim());
            double north = Double.parseDouble(values[1].trim());
            if (!Double.isFinite(east) || !Double.isFinite(north) || Math.abs(east) > 10000 || Math.abs(north) > 10000) {
                throw new IllegalArgumentException("Waypoint offsets must be finite and within +/-10000 m");
            }
            offsets.add(new double[] {east, north});
            GeoLocation point = start();
            if (east != 0) { point = point.nextLocation(Math.abs(east), east > 0 ? 90 : 270); }
            if (north != 0) { point = point.nextLocation(Math.abs(north), north > 0 ? 0 : 180); }
            points.add(point);
        }
        double[] first = offsets.get(0);
        double[] last = offsets.get(offsets.size() - 1);
        if (first[0] != 0 || first[1] != 0 || last[0] != 0 || last[1] != 0) {
            throw new IllegalArgumentException("First and last waypoint must be 0,0 (closed route)");
        }
        double sum = 0;
        for (int i = 1; i < points.size(); i++) {
            double distance = points.get(i - 1).calculateDistance(points.get(i));
            if (distance < 1) { throw new IllegalArgumentException("Adjacent waypoints must be at least 1 m apart"); }
            sum += distance;
        }
        if (sum < 100) { throw new IllegalArgumentException("Route length must be at least 100 m"); }
        length = sum;
    }

    /** Returns the existing rectangle, speed and seed without a configuration file. */
    public static MobilityScenarioConfig defaults() { return new MobilityScenarioConfig(new Properties()); }

    /** Reads a UTF-8 Java properties file; unknown keys are rejected. */
    public static MobilityScenarioConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { properties.load(reader); }
        return new MobilityScenarioConfig(properties);
    }

    public String name() { return name; }
    public long seed() { return seed; }
    public double speedMetersPerSecond() { return speed; }
    public GeoLocation start() { return new GeoLocation(47.73, 18.27); }

    /** Makes fresh waypoints because the simulator mutates reached destinations. */
    public NomadicMobilityStrategy movement(long limitMillis) {
        if (limitMillis <= 0 || limitMillis > 14_400_000) {
            throw new IllegalArgumentException("Supported simulation limit is 1-14400000 ms");
        }
        int laps = Math.max(32, (int) Math.ceil(speed * limitMillis / 1000.0 / length) + 2);
        ArrayList<GeoLocation> destinations = new ArrayList<>();
        for (int lap = 0; lap < laps; lap++) {
            for (int i = 1; i < points.size(); i++) {
                GeoLocation point = points.get(i);
                destinations.add(new GeoLocation(point.latitude, point.longitude));
            }
        }
        double speedPerMillisecond = java.math.BigDecimal.valueOf(speed).movePointLeft(3).doubleValue();
        return new NomadicMobilityStrategy(start(), speedPerMillisecond, destinations);
    }

    public double widthMeters() { return extent(0); }
    public double heightMeters() { return extent(1); }

    private double extent(int axis) {
        double min = 0;
        double max = 0;
        for (double[] point : offsets) { min = Math.min(min, point[axis]); max = Math.max(max, point[axis]); }
        return max - min;
    }

    /** Saves reproducible input separately from observation/training data. */
    public void save(Path directory) throws IOException {
        StringBuilder effective = new StringBuilder("scenario_name=").append(name)
                .append("\nseed=").append(seed).append("\nspeed_m_s=").append(speed).append("\nwaypoints_m=");
        StringBuilder coordinates = new StringBuilder("waypoint_index,east_m,north_m,latitude_deg,longitude_deg\n");
        for (int i = 0; i < offsets.size(); i++) {
            if (i > 0) { effective.append(';'); }
            double[] offset = offsets.get(i);
            GeoLocation point = points.get(i);
            effective.append(offset[0]).append(',').append(offset[1]);
            coordinates.append(i).append(',').append(offset[0]).append(',').append(offset[1])
                    .append(',').append(point.latitude).append(',').append(point.longitude).append('\n');
        }
        effective.append('\n');
        Files.writeString(directory.resolve("scenario-effective.properties"), effective.toString());
        Files.writeString(directory.resolve("route_waypoints.csv"), coordinates.toString());
    }
}
