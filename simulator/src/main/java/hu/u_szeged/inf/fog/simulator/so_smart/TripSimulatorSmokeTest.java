package hu.u_szeged.inf.fog.simulator.so_smart;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;

import java.util.List;
import java.util.Locale;

/**
 * Verifies the event-based execution and interruption
 * of SO-SMART trips.
 * <p>
 * The scenario contains three trips, two of which complete
 * successfully, while one is interrupted after its first segment.
 * It also verifies live conversion of trip events into transactions.
 */
public class TripSimulatorSmokeTest {

    /**
     * Creates the test trips, schedules an interruption,
     * and validates the simulation and transaction results.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {

        GeoLocation origin = new GeoLocation(46.25, 20.12);
        GeoLocation destination = new GeoLocation(46.26, 20.13);

        // Use shortened segment durations for the smoke test.
        Trip trip1 = new Trip(
                "Trip-1", 100,
                origin, destination,
                List.of(
                        new TripSegment(Operator.BIKE_SHARING, 2.0, 800),
                        new TripSegment(Operator.CAR_SHARING, 1.0, 1_000)
                )
        );

        Trip trip2 = new Trip(
                "Trip-2", 200,
                origin, destination,
                List.of(
                        new TripSegment(Operator.PUBLIC_TRANSPORT, 3.0, 600),
                        new TripSegment(Operator.BIKE_SHARING, 1.0, 2_000)
                )
        );

        Trip trip3 = new Trip(
                "Trip-3", 400,
                origin, destination,
                List.of(
                        new TripSegment(Operator.CAR_SHARING, 2.0, 300)
                )
        );

        TripSimulator simulator = new TripSimulator(
                List.of(trip1, trip2, trip3));

        // Convert trip events into transactions when they occur.
        TripEventTransactionMapper mapper =
                new TripEventTransactionMapper();

        simulator.setEventListener(event -> {
            if (event.getTimestampMs() != Timed.getFireCount()) {
                throw new AssertionError(
                        "Transaction mapping must occur at the event timestamp.");
            }

            mapper.toTransaction(event);
        });

        simulator.scheduleTrips();

        // Interrupt Trip-2 after its first segment has completed.
        new DeferredEvent(1_200) {
            @Override
            protected void eventAction() {
                if (!simulator.interruptTrip("Trip-2")) {
                    throw new AssertionError("Trip interruption failed");
                }
            }
        };

        Timed.simulateUntil(3_000);

        // Only completed segments contribute to the total cost.
        double expectedCost = 1_150.0;

        if (simulator.getCompletedTrips() != 2
                || simulator.getInterruptedTrips() != 1
                || simulator.getCompletedSegments() != 4
                || Math.abs(simulator.getCompletedCost() - expectedCost) > 0.001) {
            throw new AssertionError("Trip simulation failed");
        }

        System.out.println("\n=== TRIP SIMULATION SUMMARY ===");
        System.out.println("Completed trips: " + simulator.getCompletedTrips());
        System.out.println("Interrupted trips: " + simulator.getInterruptedTrips());
        System.out.println("Completed segments: " + simulator.getCompletedSegments());

        System.out.printf(Locale.US,
                "Completed cost: %.2f HUF%n",
                simulator.getCompletedCost());

        List<TripEvent> events = simulator.getEvents();

        if (events.size() != 10) {
            throw new AssertionError(
                    "Expected 10 trip events, got " + events.size());
        }

        long started = events.stream()
                .filter(e -> e.getType() == TripEventType.TRIP_STARTED)
                .count();

        long segments = events.stream()
                .filter(e -> e.getType() == TripEventType.SEGMENT_COMPLETED)
                .count();

        long interrupted = events.stream()
                .filter(e -> e.getType() == TripEventType.TRIP_INTERRUPTED)
                .count();

        long completed = events.stream()
                .filter(e -> e.getType() == TripEventType.TRIP_COMPLETED)
                .count();

        if (started != 3 || segments != 4
                || interrupted != 1 || completed != 2) {
            throw new AssertionError("Unexpected trip event counts.");
        }

        System.out.println("Recorded trip events: " + events.size());
        System.out.println("PASS | Trip event recording successful");

        // Verify that each event generated exactly one transaction.
        if (mapper.getTransactionCount() != events.size()) {
            throw new AssertionError(
                    "The number of transactions does not match the event count.");
        }

        if (mapper.getTransactionCount() != 10) {
            throw new AssertionError(
                    "Expected 10 transactions, got "
                            + mapper.getTransactionCount());
        }

        System.out.println("Generated blockchain transactions: "
                + mapper.getTransactionCount());

        System.out.println(
                "PASS | Live trip event transaction generation successful");
        System.out.println("PASS | Trip simulation successful");
    }
}
