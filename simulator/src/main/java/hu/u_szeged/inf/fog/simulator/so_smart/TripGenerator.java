package hu.u_szeged.inf.fog.simulator.so_smart;

import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Generates synthetic trip plans for the SO-SMART simulation.
 * <p>
 * Each trip consists of a random origin, destination, scheduled
 * start time and a list of mobility service segments.
 * <p>
 * Using the same random seed and configuration produces
 * identical trip plans for reproducible simulation scenarios.
 */
public class TripGenerator {

    private static final double MIN_DISTANCE_KM = 1.0;
    private static final double MAX_DISTANCE_KM = 15.0;
    private static final int MAX_SEGMENTS = 3;

    private final Random random;
    private final GeoLocation center;
    private final double radiusKm;

    /**
     * Creates a synthetic trip generator.
     *
     * @param center   the geographical center of the scenario
     * @param radiusKm the maximum distance of trip origins from the center
     * @param seed     the random seed for reproducible generation
     */
    public TripGenerator(GeoLocation center, double radiusKm, long seed) {
        Objects.requireNonNull(center, "Center cannot be null");

        if (!Double.isFinite(radiusKm) || radiusKm <= 0) {
            throw new IllegalArgumentException("Radius must be positive");
        }

        this.center = new GeoLocation(center.latitude, center.longitude);
        this.radiusKm = radiusKm;
        this.random = new Random(seed);
    }

    /**
     * Generates the specified number of synthetic trip plans.
     * <p>
     * Trip start times are distributed randomly within the
     * specified simulation time window.
     *
     * @param count         the number of trips to generate
     * @param startWindowMs the time window for trip starts in milliseconds
     * @return the generated trip plans
     */
    public List<Trip> generateTrips(int count, long startWindowMs) {
        if (count <= 0 || startWindowMs <= 0) {
            throw new IllegalArgumentException(
                    "Trip count and start window must be positive");
        }

        List<Trip> trips = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String id = "Trip-" + (i + 1);

            long startTimeMs = (long) (random.nextDouble() * startWindowMs);

            GeoLocation origin = generateLocation();

            double distanceKm = MIN_DISTANCE_KM
                    + random.nextDouble() * (MAX_DISTANCE_KM - MIN_DISTANCE_KM);

            GeoLocation destination = origin.nextLocation(
                    distanceKm * 1000, random.nextDouble() * 360);

            List<TripSegment> segments = generateSegments(distanceKm);

            trips.add(new Trip(
                    id, startTimeMs, origin, destination, segments));
        }

        trips.sort((a, b) ->
                Long.compare(a.getStartTimeMs(), b.getStartTimeMs()));

        return List.copyOf(trips);
    }

    /**
     * Generates a random geographical position around the center.
     *
     * @return the generated geographical location
     */
    private GeoLocation generateLocation() {
        double distance = random.nextDouble() * radiusKm * 1000;
        double angle = random.nextDouble() * 360;

        return center.nextLocation(distance, angle);
    }

    /**
     * Divides a trip into randomly sized mobility service segments.
     *
     * @param totalDistanceKm the total distance of the trip
     * @return the generated travel segments
     */
    private List<TripSegment> generateSegments(double totalDistanceKm) {
        int segmentCount = 1 + random.nextInt(MAX_SEGMENTS);

        List<TripSegment> segments = new ArrayList<>();
        Operator[] operators = Operator.values();

        double[] weights = new double[segmentCount];
        double totalWeight = 0;

        for (int i = 0; i < segmentCount; i++) {
            weights[i] = 0.2 + random.nextDouble();
            totalWeight += weights[i];
        }

        double remainingDistance = totalDistanceKm;
        Operator previousOperator = null;

        for (int i = 0; i < segmentCount; i++) {
            Operator operator;

            do {
                operator = operators[random.nextInt(operators.length)];
            } while (operator == previousOperator);

            double distanceKm = (i == segmentCount - 1)
                    ? remainingDistance
                    : totalDistanceKm * weights[i] / totalWeight;

            remainingDistance -= distanceKm;

            long durationMs = calculateDuration(operator, distanceKm);

            segments.add(new TripSegment(operator, distanceKm, durationMs));

            previousOperator = operator;
        }

        return segments;
    }

    /**
     * Estimates the travel duration using the average speed
     * associated with the selected mobility operator.
     *
     * @param operator   the mobility service operator
     * @param distanceKm the segment distance in kilometers
     * @return the estimated duration in milliseconds
     */
    private long calculateDuration(Operator operator, double distanceKm) {
        double speedKmH;

        switch (operator) {
            case PUBLIC_TRANSPORT:
                speedKmH = 25.0;
                break;
            case CAR_SHARING:
                speedKmH = 35.0;
                break;
            case BIKE_SHARING:
                speedKmH = 15.0;
                break;
            default:
                throw new IllegalArgumentException("Unknown operator");
        }

        return Math.max(1L, (long) Math.ceil(
                distanceKm / speedKmH * 3_600_000));
    }
}
