package hu.u_szeged.inf.fog.simulator.so_smart;

import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;

import java.util.List;
import java.util.Locale;

/**
 * A simple test for verifying synthetic trip generation
 * in the SO-SMART simulation.
 */
public class TripGeneratorSmokeTest {

    private static final int TRIP_COUNT = 300;
    private static final long START_WINDOW_MS = 3_600_000L;
    private static final int DISPLAY_LIMIT = 5;

    /**
     * Generates synthetic trips, validates their basic properties
     * and prints a summary of the results.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {

        TripGenerator generator = new TripGenerator(
                new GeoLocation(46.25, 20.12),
                5.0,
                42L
        );

        List<Trip> trips = generator.generateTrips(
                TRIP_COUNT, START_WINDOW_MS);

        if (trips.size() != TRIP_COUNT) {
            throw new AssertionError("Incorrect number of generated trips");
        }

        int totalSegments = 0;
        double totalCost = 0;
        long previousStartTime = -1;

        for (Trip trip : trips) {

            if (trip.getStartTimeMs() < previousStartTime
                    || trip.getStartTimeMs() >= START_WINDOW_MS) {
                throw new AssertionError("Invalid trip start time");
            }

            if (trip.getSegments().isEmpty()
                    || trip.getTotalDistanceKm() <= 0
                    || trip.getTotalDurationMs() <= 0
                    || trip.getTotalCost() <= 0) {
                throw new AssertionError("Invalid trip data");
            }

            previousStartTime = trip.getStartTimeMs();
            totalSegments += trip.getSegments().size();
            totalCost += trip.getTotalCost();
        }

        // Display the first few generated trip plans.
        for (int i = 0; i < Math.min(DISPLAY_LIMIT, trips.size()); i++) {

            Trip trip = trips.get(i);

            System.out.println("\n" + trip.getId()
                    + " | Start: " + trip.getStartTimeMs() + " ms");

            System.out.printf(Locale.US,
                    "Origin: %.5f, %.5f | Destination: %.5f, %.5f%n",
                    trip.getOrigin().latitude,
                    trip.getOrigin().longitude,
                    trip.getDestination().latitude,
                    trip.getDestination().longitude);

            for (TripSegment segment : trip.getSegments()) {
                System.out.printf(Locale.US,
                        "  %s | %.2f km | %.2f HUF%n",
                        segment.getOperator().getName(),
                        segment.getDistanceKm(),
                        segment.getCost());
            }

            System.out.printf(Locale.US,
                    "Total: %.2f km | %.2f HUF%n",
                    trip.getTotalDistanceKm(),
                    trip.getTotalCost());
        }

        System.out.println("\n=== GENERATION SUMMARY ===");
        System.out.println("Generated trips: " + trips.size());
        System.out.println("Generated segments: " + totalSegments);

        System.out.printf(Locale.US,
                "Total planned cost: %.2f HUF%n", totalCost);

        System.out.println("PASS | Trip generation successful");
    }
}
