package hu.u_szeged.inf.fog.simulator.so_smart;

import java.util.Objects;

/**
 * Represents a single segment of a trip in the SO-SMART simulation.
 * <p>
 * Each segment is associated with a mobility operator and
 * has a predefined distance and duration.
 */
public class TripSegment {

    private final Operator operator;
    private final double distanceKm;
    private final long durationMs;

    /**
     * Creates a new trip segment.
     *
     * @param operator   the mobility service operator
     * @param distanceKm the distance in kilometers
     * @param durationMs the simulated duration in milliseconds
     */
    public TripSegment(Operator operator, double distanceKm, long durationMs) {
        if (!Double.isFinite(distanceKm) || distanceKm <= 0) {
            throw new IllegalArgumentException("Distance must be positive and finite");
        }

        if (durationMs <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }

        this.operator = Objects.requireNonNull(operator, "Operator cannot be null");
        this.distanceKm = distanceKm;
        this.durationMs = durationMs;
    }

    public Operator getOperator() {
        return operator;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public long getDurationMs() {
        return durationMs;
    }

    /**
     * Calculates the cost of the segment based on its distance
     * and the operator's price per kilometer.
     *
     * @return the segment cost in HUF
     */
    public double getCost() {
        return distanceKm * operator.getPricePerKm();
    }
}
