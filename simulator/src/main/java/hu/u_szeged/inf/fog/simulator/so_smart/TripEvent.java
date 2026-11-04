package hu.u_szeged.inf.fog.simulator.so_smart;

import java.util.Objects;

/**
 * Represents an immutable event generated during trip simulation.
 */
public final class TripEvent {

    private final String id;
    private final TripEventType type;
    private final String tripId;
    private final long timestampMs;
    private final int segmentIndex;
    private final TripSegment segment;

    /**
     * Creates a trip event.
     *
     * @param type         event type
     * @param tripId       identifier of the associated trip
     * @param timestampMs  simulation time of the event
     * @param segmentIndex zero-based index of the completed segment,
     *                     or -1 for non-segment events
     * @param segment      completed segment, or null for non-segment events
     */
    public TripEvent(
            TripEventType type,
            String tripId,
            long timestampMs,
            int segmentIndex,
            TripSegment segment) {

        this.type = Objects.requireNonNull(type, "Event type is required.");

        if (tripId == null || tripId.isBlank()) {
            throw new IllegalArgumentException("Trip ID must not be empty.");
        }

        if (timestampMs < 0) {
            throw new IllegalArgumentException(
                    "Event timestamp must not be negative.");
        }

        if (type == TripEventType.SEGMENT_COMPLETED) {
            if (segment == null || segmentIndex < 0) {
                throw new IllegalArgumentException(
                        "Completed segment events require segment data.");
            }
        } else if (segment != null || segmentIndex != -1) {
            throw new IllegalArgumentException(
                    "Non-segment events must not contain segment data.");
        }

        this.tripId = tripId;
        this.timestampMs = timestampMs;
        this.segmentIndex = segmentIndex;
        this.segment = segment;

        this.id = tripId + ":" + type.name()
                + (segmentIndex >= 0 ? ":" + segmentIndex : "");
    }

    public String getId() {
        return id;
    }

    public TripEventType getType() {
        return type;
    }

    public String getTripId() {
        return tripId;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public TripSegment getSegment() {
        return segment;
    }

    /**
     * Returns the cost associated with the completed segment.
     *
     * @return completed segment cost, or zero for other event types
     */
    public double getCost() {
        return segment == null ? 0.0 : segment.getCost();
    }

    @Override
    public String toString() {
        return timestampMs + " ms | " + type
                + " | " + tripId
                + (segment == null
                ? ""
                : " | segment=" + segmentIndex
                + " | " + segment.getOperator().getName()
                + " | cost=" + getCost() + " HUF");
    }
}
