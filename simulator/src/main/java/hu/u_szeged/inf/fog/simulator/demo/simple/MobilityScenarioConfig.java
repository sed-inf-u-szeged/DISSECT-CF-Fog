package hu.u_szeged.inf.fog.simulator.demo.simple;

import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.NomadicMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.RandomWalkMobilityStrategy;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Selects official movement strategies; sampling interval and infrastructure remain fixed. */
public final class MobilityScenarioConfig {
    private static final String DEFAULT_POINTS = "0,0;1600,0;1600,1200;0,1200;0,0";
    private static final Set<String> KEYS = Set.of("scenario_name", "seed", "mobility_strategy",
            "start_latitude_deg", "start_longitude_deg", "speed_m_s", "waypoints_m", "route_repeats",
            "speed_min_m_s", "speed_max_m_s", "radius_m", "duration_minutes");
    private final String name;
    private final long seed;
    private final int durationMinutes;
    private final String strategy;
    private final boolean legacy;
    private final boolean repeats;
    private final double latitude;
    private final double longitude;
    private final double speed;
    private final double maximumSpeed;
    private final double radius;
    private final List<double[]> offsets = new ArrayList<>();
    private final List<GeoLocation> points = new ArrayList<>();
    private final double length;

    private MobilityScenarioConfig(Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!KEYS.contains(key)) { throw new IllegalArgumentException("Unknown scenario key: " + key); }
        }
        durationMinutes = Integer.parseInt(properties.getProperty("duration_minutes", "120").trim());
        if (durationMinutes < 10 || durationMinutes > 1440) {
            throw new IllegalArgumentException("duration_minutes must be an integer between 10 and 1440");
        }
        legacy = !properties.containsKey("mobility_strategy");
        if (legacy) { reject(properties, "start_latitude_deg", "start_longitude_deg", "route_repeats"); }
        strategy = properties.getProperty("mobility_strategy", "nomadic").trim();
        if (!strategy.equals("nomadic") && !strategy.equals("random_walk")) {
            throw new IllegalArgumentException("mobility_strategy must be nomadic or random_walk");
        }
        name = properties.getProperty("scenario_name", legacy ? "rectangle-default" : "official-" + strategy).trim();
        if (!name.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("scenario_name must contain 1-64 letters, digits, _ or -");
        }
        seed = Long.parseLong(properties.getProperty("seed", "42").trim());
        latitude = number(properties, "start_latitude_deg", 47.73, -85, 85);
        longitude = number(properties, "start_longitude_deg", 18.27, -180, 180);
        if (isRandomWalk()) {
            reject(properties, "waypoints_m", "route_repeats", "speed_m_s");
            repeats = false;
            speed = number(properties, "speed_min_m_s", 2.0, 0, 50);
            maximumSpeed = number(properties, "speed_max_m_s", 6.0, speed, 50);
            radius = number(properties, "radius_m", 800, 1, 10000);
            // At a boundary there must still be feasible directions at the largest 60-second step.
            if (maximumSpeed * 60 >= radius) {
                throw new IllegalArgumentException("radius_m must exceed speed_max_m_s * 60 for this sampler");
            }
            length = 0;
            return;
        }
        reject(properties, "speed_min_m_s", "speed_max_m_s", "radius_m");
        String repeatText = properties.getProperty("route_repeats", Boolean.toString(legacy)).trim();
        if (!repeatText.equals("true") && !repeatText.equals("false")) {
            throw new IllegalArgumentException("route_repeats must be true or false");
        }
        repeats = Boolean.parseBoolean(repeatText);
        speed = number(properties, "speed_m_s", 4.1, 0.1, 50);
        maximumSpeed = speed;
        radius = 0;
        String route = properties.getProperty("waypoints_m", legacy ? DEFAULT_POINTS : "");
        String[] rows = route.trim().split(";", -1);
        if (rows.length < (repeats ? 3 : 2) || rows.length > 100) {
            throw new IllegalArgumentException("Provide 2-100 waypoints; a repeated route needs a closed loop");
        }
        for (String row : rows) {
            String[] values = row.trim().split(",", -1);
            if (values.length != 2) { throw new IllegalArgumentException("Expected east_m,north_m: " + row); }
            double east = Double.parseDouble(values[0].trim());
            double north = Double.parseDouble(values[1].trim());
            if (!Double.isFinite(east) || !Double.isFinite(north)
                    || Math.abs(east) > 10000 || Math.abs(north) > 10000) {
                throw new IllegalArgumentException("Waypoint offsets must be finite and within +/-10000 m");
            }
            offsets.add(new double[] {east, north});
            GeoLocation point = start();
            if (east != 0) { point = point.nextLocation(Math.abs(east), east > 0 ? 90 : 270); }
            if (north != 0) { point = point.nextLocation(Math.abs(north), north > 0 ? 0 : 180); }
            if (Math.abs(point.latitude) > 90 || Math.abs(point.longitude) > 180) {
                throw new IllegalArgumentException("Waypoints exceed supported geographic coordinates");
            }
            points.add(point);
        }
        double[] first = offsets.get(0);
        double[] last = offsets.get(offsets.size() - 1);
        if (first[0] != 0 || first[1] != 0) {
            throw new IllegalArgumentException("First waypoint must be 0,0 (the configured start)");
        }
        if (repeats && (last[0] != 0 || last[1] != 0)) {
            throw new IllegalArgumentException("A repeated route must end at 0,0");
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

    private static double number(Properties properties, String key, double fallback, double min, double max) {
        double value = Double.parseDouble(properties.getProperty(key, Double.toString(fallback)).trim());
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static void reject(Properties properties, String... keys) {
        for (String key : keys) {
            if (properties.containsKey(key)) {
                throw new IllegalArgumentException("Setting does not belong to this mobility strategy: " + key);
            }
        }
    }

    /** Keeps earlier pilot and UI runs reproducible when no explicit strategy is supplied. */
    public static MobilityScenarioConfig defaults() { return new MobilityScenarioConfig(new Properties()); }

    /** Reads a UTF-8 Java properties file; unknown and incompatible keys are rejected. */
    public static MobilityScenarioConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { properties.load(reader); }
        return new MobilityScenarioConfig(properties);
    }

    public String name() { return name; }
    public long seed() { return seed; }
    public String strategy() { return strategy; }
    public boolean isRandomWalk() { return strategy.equals("random_walk"); }
    public boolean repeats() { return repeats; }
    public double speedMetersPerSecond() { return speed; }
    public double maximumSpeedMetersPerSecond() { return maximumSpeed; }
    public double radiusMeters() { return radius; }
    public GeoLocation start() { return new GeoLocation(latitude, longitude); }

    /** Creates an official strategy with fresh mutable state and metres-per-millisecond speed. */
    public MobilityStrategy movement(long limitMillis) {
        if (limitMillis <= 0 || limitMillis > simulationLimitMillis()) {
            throw new IllegalArgumentException("Simulation limit must be positive and within the configured duration plus drain allowance");
        }
        double perMillis = java.math.BigDecimal.valueOf(speed).movePointLeft(3).doubleValue();
        if (isRandomWalk()) {
            double maxPerMillis = java.math.BigDecimal.valueOf(maximumSpeed).movePointLeft(3).doubleValue();
            return new RandomWalkMobilityStrategy(start(), perMillis, maxPerMillis, radius);
        }
        int laps = repeats ? Math.max(32, (int) Math.ceil(speed * limitMillis / 1000.0 / length) + 2) : 1;
        ArrayList<GeoLocation> destinations = new ArrayList<>();
        for (int lap = 0; lap < laps; lap++) {
            for (int i = 1; i < points.size(); i++) {
                GeoLocation point = points.get(i);
                destinations.add(new GeoLocation(point.latitude, point.longitude));
            }
        }
        return new NomadicMobilityStrategy(start(), perMillis, destinations);
    }

    public long durationMillis() { return durationMinutes * 60_000L; }
    public long simulationLimitMillis() { return (durationMinutes + 120L) * 60_000L; }
    public int observationCount() { return durationMinutes + 1; }

    public double widthMeters() { return isRandomWalk() ? 2 * radius : extent(0); }
    public double heightMeters() { return isRandomWalk() ? 2 * radius : extent(1); }

    private double extent(int axis) {
        double min = 0;
        double max = 0;
        for (double[] point : offsets) { min = Math.min(min, point[axis]); max = Math.max(max, point[axis]); }
        return max - min;
    }

    /** Reports strategy-specific metadata without inventing a waypoint file or fixed random speed. */
    public String metadata() {
        String common = "\nduration_minutes=" + durationMinutes + "\nmobility_strategy=" + strategy
                + "\nroute_strategy=" + (isRandomWalk() ? "RandomWalkMobilityStrategy" : "NomadicMobilityStrategy")
                + "\nroute_repeats=" + repeats + "\nstart_latitude_deg=" + latitude
                + "\nstart_longitude_deg=" + longitude;
        return common + (isRandomWalk()
                ? "\nspeed_min_m_s=" + speed + "\nspeed_max_m_s=" + maximumSpeed + "\nradius_m=" + radius
                : "\nspeed_m_s=" + speed + "\nroute_file=route_waypoints.csv");
    }

    /** Saves scene inputs separately from observations; no future random trajectory is generated. */
    public void save(Path directory) throws IOException {
        StringBuilder effective = new StringBuilder("scenario_name=").append(name)
                .append("\nseed=").append(seed).append("\nduration_minutes=").append(durationMinutes);
        if (!legacy) {
            effective.append("\nmobility_strategy=").append(strategy)
                    .append("\nstart_latitude_deg=").append(latitude)
                    .append("\nstart_longitude_deg=").append(longitude);
        }
        if (isRandomWalk()) {
            effective.append("\nspeed_min_m_s=").append(speed)
                    .append("\nspeed_max_m_s=").append(maximumSpeed).append("\nradius_m=").append(radius);
        } else {
            effective.append("\nspeed_m_s=").append(speed);
            if (!legacy) { effective.append("\nroute_repeats=").append(repeats); }
            effective.append("\nwaypoints_m=");
            StringBuilder coordinates = new StringBuilder("waypoint_index,east_m,north_m,latitude_deg,longitude_deg\n");
            for (int i = 0; i < offsets.size(); i++) {
                if (i > 0) { effective.append(';'); }
                double[] offset = offsets.get(i);
                GeoLocation point = points.get(i);
                effective.append(offset[0]).append(',').append(offset[1]);
                coordinates.append(i).append(',').append(offset[0]).append(',').append(offset[1])
                        .append(',').append(point.latitude).append(',').append(point.longitude).append('\n');
            }
            Files.writeString(directory.resolve("route_waypoints.csv"), coordinates.toString());
        }
        Files.writeString(directory.resolve("scenario-effective.properties"), effective.append('\n').toString());
    }
}
