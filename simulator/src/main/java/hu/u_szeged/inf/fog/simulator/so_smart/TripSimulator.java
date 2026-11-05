package hu.u_szeged.inf.fog.simulator.so_smart;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.*;
import java.util.function.Consumer;

/**
 * Simulates the execution of planned SO-SMART trips.
 * <p>
 * Trip starts and segment completions are scheduled using
 * the DISSECT-CF discrete event simulation framework.
 */
public class TripSimulator {

    private enum TripStatus {
        PLANNED,
        ACTIVE,
        COMPLETED,
        INTERRUPTED
    }

    /**
     * Stores the execution state of a single trip.
     */
    private static class TripProgress {

        final Trip trip;
        TripStatus status = TripStatus.PLANNED;
        int nextSegmentIndex = 0;
        DeferredEvent segmentEvent;

        /**
         * Number of segments completed by this trip.
         */
        private int completedSegmentCount = 0;

        TripProgress(Trip trip) {
            this.trip = trip;
        }
    }

    private final Map<String, TripProgress> trips = new LinkedHashMap<>();


    private boolean scheduled = false;
    private int completedTrips = 0;
    private int interruptedTrips = 0;
    private int completedSegments = 0;
    private double completedCost = 0;

    /**
     * Stores all events generated during the simulation.
     */
    private final List<TripEvent> events = new ArrayList<>();

    /**
     * Optional listener notified when a trip event occurs.
     */
    private Consumer<TripEvent> eventListener;

    /**
     * Creates a simulator for the specified trip plans.
     *
     * @param tripPlans the trips to simulate
     */
    public TripSimulator(List<Trip> tripPlans) {
        Objects.requireNonNull(tripPlans, "Trip plans cannot be null");

        for (Trip trip : tripPlans) {
            Objects.requireNonNull(trip, "Trip cannot be null");

            if (trips.putIfAbsent(trip.getId(),
                    new TripProgress(trip)) != null) {
                throw new IllegalArgumentException(
                        "Duplicate trip ID: " + trip.getId());
            }
        }
    }

    /**
     * Schedules all trips according to their planned start times.
     */
    public void scheduleTrips() {
        if (scheduled) {
            throw new IllegalStateException("Trips already scheduled");
        }

        long currentTime = Timed.getFireCount();

        for (TripProgress progress : trips.values()) {
            if (progress.trip.getStartTimeMs() < currentTime) {
                throw new IllegalArgumentException(
                        "Trip start time is in the past");
            }
        }

        scheduled = true;

        for (TripProgress progress : trips.values()) {
            long delay = progress.trip.getStartTimeMs() - currentTime;

            new DeferredEvent(delay) {
                @Override
                protected void eventAction() {
                    startTrip(progress);
                }
            };
        }
    }

    /**
     * Starts a trip and schedules its first segment.
     *
     * @param progress the trip execution state
     */
    private void startTrip(TripProgress progress) {
        if (progress.status != TripStatus.PLANNED) {
            return;
        }

        progress.status = TripStatus.ACTIVE;

        recordEvent(TripEventType.TRIP_STARTED, progress, -1, null);

        SimLogger.logRun("TRIP_STARTED " + progress.trip.getId());

        scheduleNextSegment(progress);
    }

    /**
     * Schedules the completion of the next travel segment.
     *
     * @param progress the trip execution state
     */
    private void scheduleNextSegment(TripProgress progress) {
        TripSegment segment = progress.trip.getSegments()
                .get(progress.nextSegmentIndex);

        progress.segmentEvent = new DeferredEvent(segment.getDurationMs()) {
            @Override
            protected void eventAction() {
                completeSegment(progress, segment);
            }
        };
    }

    /**
     * Completes a segment and updates the execution statistics.
     *
     * @param progress the trip execution state
     * @param segment  the completed travel segment
     */
    private void completeSegment(TripProgress progress, TripSegment segment) {
        if (progress.status != TripStatus.ACTIVE) {
            return;
        }

        progress.nextSegmentIndex++;
        completedSegments++;
        completedCost += segment.getCost();
        progress.segmentEvent = null;

        recordEvent(
                TripEventType.SEGMENT_COMPLETED,
                progress,
                progress.completedSegmentCount,
                segment
        );

        progress.completedSegmentCount++;

        SimLogger.logRun(String.format(Locale.US,
                "SEGMENT_COMPLETED %s | %s | %.2f km | %.2f HUF",
                progress.trip.getId(),
                segment.getOperator().getName(),
                segment.getDistanceKm(),
                segment.getCost()));

        if (progress.nextSegmentIndex == progress.trip.getSegments().size()) {
            progress.status = TripStatus.COMPLETED;
            completedTrips++;

            recordEvent(TripEventType.TRIP_COMPLETED, progress, -1, null);

            SimLogger.logRun("TRIP_COMPLETED " + progress.trip.getId());
        } else {
            scheduleNextSegment(progress);
        }
    }

    /**
     * Interrupts an active trip and cancels its current segment.
     * <p>
     * Completed segments remain recorded, while unfinished
     * segments are excluded from the completed cost.
     *
     * @param tripId the identifier of the trip
     * @return true if the trip was interrupted successfully
     */
    public boolean interruptTrip(String tripId) {
        TripProgress progress = trips.get(tripId);

        if (progress == null || progress.status != TripStatus.ACTIVE) {
            return false;
        }

        progress.status = TripStatus.INTERRUPTED;
        interruptedTrips++;

        if (progress.segmentEvent != null) {
            progress.segmentEvent.cancel();
            progress.segmentEvent = null;
        }

        recordEvent(TripEventType.TRIP_INTERRUPTED, progress, -1, null);

        SimLogger.logRun("TRIP_INTERRUPTED " + tripId);

        return true;
    }

    /**
     * Registers a listener for trip events.
     *
     * @param listener listener to notify when an event occurs
     */
    public void setEventListener(Consumer<TripEvent> listener) {
        this.eventListener = Objects.requireNonNull(
                listener, "Event listener is required.");
    }

    /**
     * Records a trip event at the current simulation time.
     */
    private void recordEvent(
            TripEventType type,
            TripProgress progress,
            int segmentIndex,
            TripSegment segment) {

        TripEvent event = new TripEvent(
                type,
                progress.trip.getId(),
                Timed.getFireCount(),
                segmentIndex,
                segment
        );

        events.add(event);

        if (eventListener != null) {
            eventListener.accept(event);
        }
    }

    /**
     * Returns an immutable snapshot of the recorded trip events.
     *
     * @return recorded trip events in execution order
     */
    public List<TripEvent> getEvents() {
        return List.copyOf(events);
    }

    public int getCompletedTrips() {
        return completedTrips;
    }

    public int getInterruptedTrips() {
        return interruptedTrips;
    }

    public int getCompletedSegments() {
        return completedSegments;
    }

    /**
     * Returns the total cost of completed segments.
     *
     * @return the completed segment cost in HUF
     */
    public double getCompletedCost() {
        return completedCost;
    }
}
