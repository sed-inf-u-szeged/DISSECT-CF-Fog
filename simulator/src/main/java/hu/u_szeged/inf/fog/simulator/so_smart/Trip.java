package hu.u_szeged.inf.fog.simulator.so_smart;

import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;

import java.util.List;
import java.util.Objects;

/**
 * Represents a planned trip in the SO-SMART simulation.
 * <p>
 * Each trip has a unique identifier, a scheduled start time,
 * an origin, a destination and a list of travel segments.
 * <p>
 * The trip segments define the mobility operators, distances,
 * durations and estimated costs of the journey.
 */
public class Trip {

    private final String id;
    private final long startTimeMs;
    private final GeoLocation origin;
    private final GeoLocation destination;
    private final List<TripSegment> segments;

    /**
     * Creates a new trip with predefined travel segments.
     *
     * @param id          the unique trip identifier
     * @param startTimeMs the scheduled start time in milliseconds
     * @param origin      the starting geographical location
     * @param destination the destination geographical location
     * @param segments    the ordered list of travel segments
     */
    public Trip(String id, long startTimeMs,
                GeoLocation origin, GeoLocation destination,
                List<TripSegment> segments) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Trip ID cannot be empty");
        }

        if (startTimeMs < 0) {
            throw new IllegalArgumentException("Start time cannot be negative");
        }

        Objects.requireNonNull(segments, "Segments cannot be null");

        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Trip must have at least one segment");
        }

        this.id = id;
        this.startTimeMs = startTimeMs;
        this.origin = Objects.requireNonNull(origin, "Origin cannot be null");
        this.destination = Objects.requireNonNull(destination, "Destination cannot be null");
        this.segments = List.copyOf(segments);
    }

    public String getId() {
        return id;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public GeoLocation getOrigin() {
        return origin;
    }

    public GeoLocation getDestination() {
        return destination;
    }

    public List<TripSegment> getSegments() {
        return segments;
    }

    /**
     * Calculates the total planned travel distance.
     *
     * @return the total distance in kilometers
     */
    public double getTotalDistanceKm() {
        return segments.stream()
                .mapToDouble(TripSegment::getDistanceKm)
                .sum();
    }

    /**
     * Calculates the total planned travel duration.
     *
     * @return the total duration in milliseconds
     */
    public long getTotalDurationMs() {
        return segments.stream()
                .mapToLong(TripSegment::getDurationMs)
                .sum();
    }

    /**
     * Calculates the estimated total cost of the trip.
     *
     * @return the total planned cost in HUF
     */
    public double getTotalCost() {
        return segments.stream()
                .mapToDouble(TripSegment::getCost)
                .sum();
    }
}
