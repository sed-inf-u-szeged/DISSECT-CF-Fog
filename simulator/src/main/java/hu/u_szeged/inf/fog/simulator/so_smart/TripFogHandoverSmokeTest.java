package hu.u_szeged.inf.fog.simulator.so_smart;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.Config;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.util.HashMap;
import java.util.List;

/**
 * Verifies Fog handover during a SO-SMART trip.
 */
public class TripFogHandoverSmokeTest {

    public static void main(String[] args) {

        final int fogLatencyMs = 33;

        GeoLocation origin = new GeoLocation(46.2500, 20.1200);
        GeoLocation destination = new GeoLocation(46.2500, 20.1240);

        // Create two Fog nodes along the trip.
        ComputingAppliance fogA = new ComputingAppliance(
                Config.createNode(
                        "FogA", 4,
                        16L << 30, 16L << 30,
                        30, 180, 220,
                        125_000, fogLatencyMs,
                        new HashMap<>()
                ),
                origin,
                "region-A",
                "test"
        );

        ComputingAppliance fogB = new ComputingAppliance(
                Config.createNode(
                        "FogB", 4,
                        16L << 30, 16L << 30,
                        30, 180, 220,
                        125_000, fogLatencyMs,
                        new HashMap<>()
                ),
                destination,
                "region-B",
                "test"
        );

        fogA.addNeighbor(fogB, fogLatencyMs);

        // Create a trip with two travel segments.
        Trip trip = new Trip(
                "Trip-1",
                100L,
                origin,
                destination,
                List.of(
                        new TripSegment(
                                Operator.BIKE_SHARING, 0.2, 800L),
                        new TripSegment(
                                Operator.PUBLIC_TRANSPORT, 0.2, 800L)
                )
        );

        TripSimulator simulator = new TripSimulator(List.of(trip));

        TripFogMobilityManager mobility =
                new TripFogMobilityManager(
                        List.of(trip),
                        List.of(fogA, fogB),
                        100L
                );

        // Link trip execution to the mobility manager.
        simulator.setEventListener(mobility::onTripEvent);

        simulator.scheduleTrips();

        Timed.simulateUntil(2_500L);

        System.out.println("\n=== SO-SMART FOG HANDOVER SUMMARY ===");
        System.out.println("Completed trips: "
                + simulator.getCompletedTrips());
        System.out.println("Completed segments: "
                + simulator.getCompletedSegments());
        System.out.println("Completed handovers: "
                + mobility.getTotalHandovers());

        ComputingAppliance finalFog =
                mobility.getServingFog("Trip-1");

        System.out.println("Final Fog node: "
                + (finalFog == null ? "none" : finalFog.name));

        if (simulator.getCompletedTrips() != 1
                || simulator.getCompletedSegments() != 2
                || mobility.getHandoverCount("Trip-1") != 1
                || finalFog != fogB) {
            throw new AssertionError(
                    "SO-SMART Fog handover integration failed.");
        }

        System.out.println(
                "PASS | SO-SMART Fog handover successful");
    }
}
