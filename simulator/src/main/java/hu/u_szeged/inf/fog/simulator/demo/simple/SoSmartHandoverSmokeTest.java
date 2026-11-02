package hu.u_szeged.inf.fog.simulator.demo.simple;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.Config;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.NomadicMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;

import java.util.ArrayDeque;

import java.util.HashMap;

/**
 * A simple SO-SMART simulation scenario demonstrating mobility
 * and handover between two fog computing nodes.
 * <p>
 * The scenario consists of two fog nodes and a single traveler.
 * The traveler moves along a predefined route using the existing
 * NomadicMobilityStrategy. When the traveler enters the region
 * of another fog node, a handover event is triggered.
 * <p>
 * This initial implementation does not include blockchain operations,
 * virtual machine processing or actual network data transfers.
 */
public class SoSmartHandoverSmokeTest {

    /**
     * The interval between consecutive mobility updates in milliseconds.
     */
    private static final long MOVE_INTERVAL = 1_000;

    /**
     * The simulated delay required to process a trip request.
     */
    private static final long PLANNING_DELAY = 200;

    /**
     * The network latency between the two fog nodes in milliseconds.
     */
    private static final int FOG_LATENCY = 33;

    /**
     * Represents the current state of a travel request.
     * The context is transferred logically between fog nodes
     * when a handover occurs.
     */
    private static class TripContext {

        ComputingAppliance servingFog;
        boolean handoverPending;

        long plannedAt;
        long finishedAt;
        long handoverDuration;

        int handovers;
        int segments;
        final String id;

        /**
         * Creates a trip context assigned to the initial fog node.
         *
         * @param id         the unique identifier of the traveler
         * @param servingFog the fog node initially responsible for the trip
         */
        TripContext(String id, ComputingAppliance servingFog) {
            this.id = id;
            this.servingFog = servingFog;
        }
    }


    /**
     * Simulates sequential processing of trip requests on a fog node.
     * <p>
     * Incoming requests are stored in a FIFO queue. Each request
     * requires a fixed amount of simulated processing time before
     * the corresponding traveler can start its journey.
     */
    private static class FogRequestProcessor {

        private final ComputingAppliance fogA;
        private final ComputingAppliance fogB;
        private final GeoLocation start;
        private final GeoLocation destination;

        private final ArrayDeque<TripContext> requestQueue = new ArrayDeque<>();

        private boolean busy;
        private int processedRequests;

        /**
         * Creates a request processor for the specified fog infrastructure.
         *
         * @param fogA        the fog node processing trip requests
         * @param fogB        the destination fog node
         * @param start       the starting geographical position
         * @param destination the destination geographical position
         */
        FogRequestProcessor(ComputingAppliance fogA, ComputingAppliance fogB,
                            GeoLocation start, GeoLocation destination) {
            this.fogA = fogA;
            this.fogB = fogB;
            this.start = start;
            this.destination = destination;
        }

        /**
         * Submits a new trip request to the fog node.
         *
         * @param trip the trip context associated with the request
         */
        void submitRequest(TripContext trip) {
            requestQueue.addLast(trip);

            System.out.println(Timed.getFireCount()
                    + " ms | TRIP_REQUEST " + trip.id
                    + " received by " + fogA.name
                    + " | queueSize=" + requestQueue.size());

            if (!busy) {
                processNext();
            }
        }

        /**
         * Processes the next request in the queue.
         * Only one request can be processed at a time.
         */
        private void processNext() {
            TripContext trip = requestQueue.pollFirst();

            if (trip == null) {
                busy = false;
                return;
            }

            busy = true;

            System.out.println(Timed.getFireCount()
                    + " ms | PROCESSING_STARTED " + trip.id);

            new DeferredEvent(PLANNING_DELAY) {

                @Override
                protected void eventAction() {
                    trip.plannedAt = Timed.getFireCount();
                    processedRequests++;

                    System.out.println(Timed.getFireCount()
                            + " ms | TRIP_PLANNED " + trip.id);

                    new Traveler(trip, fogA, fogB, start, destination)
                            .startTrip();

                    processNext();
                }
            };
        }

        /**
         * Returns the total number of processed trip requests.
         *
         * @return the number of completed request processing operations
         */
        int getProcessedRequests() {
            return processedRequests;
        }
    }


    /**
     * Represents a mobile traveler in the SO-SMART scenario.
     * <p>
     * The traveler uses the existing IoT mobility infrastructure
     * to update its geographical position and determine when
     * a handover between fog nodes is required.
     */
    private static class Traveler extends Device {

        final TripContext trip;
        final ComputingAppliance fogA;
        final ComputingAppliance fogB;
        final GeoLocation destination;

        /**
         * Creates a traveler with a predefined route and mobility strategy.
         *
         * @param trip        the associated trip context
         * @param fogA        the first fog node
         * @param fogB        the second fog node
         * @param start       the initial geographical position
         * @param destination the final geographical destination
         */
        Traveler(TripContext trip, ComputingAppliance fogA,
                 ComputingAppliance fogB, GeoLocation start,
                 GeoLocation destination) {

            this.trip = trip;
            this.fogA = fogA;
            this.fogB = fogB;
            this.destination = destination;

            this.freq = MOVE_INTERVAL;
            this.geoLocation = new GeoLocation(
                    start.latitude, start.longitude);

            // The traveler moves at 12 meters per second.
            this.mobilityStrategy = new NomadicMobilityStrategy(
                    start, 0.012, destination);
        }

        /**
         * Starts the trip and subscribes to periodic mobility updates.
         */
        void startTrip() {
            System.out.println(Timed.getFireCount()
                    + " ms | TRIP_STARTED at " + trip.servingFog.name);

            subscribe(MOVE_INTERVAL);
        }

        /**
         * Updates the traveler's position and checks whether
         * a fog node handover or trip completion is required.
         *
         * @param fires the current simulation time
         */
        @Override
        public void tick(long fires) {

            MobilityEvent.changePositionEvent(
                    this, mobilityStrategy.move(this));

            ComputingAppliance nearest =
                    geoLocation.calculateDistance(fogA.geoLocation)
                            <= geoLocation.calculateDistance(fogB.geoLocation)
                            ? fogA : fogB;

            if (nearest != trip.servingFog && !trip.handoverPending) {
                handoverTo(nearest);
            }

            if (geoLocation.calculateDistance(destination) < 1.0
                    && !trip.handoverPending
                    && trip.servingFog == fogB) {

                trip.segments++;
                trip.finishedAt = Timed.getFireCount();

                System.out.println(Timed.getFireCount()
                        + " ms | TRIP_FINISHED at " + fogB.name);

                unsubscribe();
            }
        }

        /**
         * Initiates a handover to another fog node.
         * <p>
         * The handover delay is determined by the configured network
         * latency between the fog nodes. The actual transfer of trip
         * context data is not yet simulated.
         *
         * @param nextFog the fog node receiving the trip context
         */
        private void handoverTo(ComputingAppliance nextFog) {

            trip.handoverPending = true;
            trip.segments++;

            long startedAt = Timed.getFireCount();

            System.out.println(startedAt
                    + " ms | HANDOVER_STARTED: "
                    + trip.servingFog.name + " -> " + nextFog.name);

            Integer latency = trip.servingFog.iaas.repositories.get(0)
                    .getLatencies()
                    .get(nextFog.iaas.repositories.get(0).getName());

            if (latency == null) {
                throw new IllegalStateException(
                        "No connection between fog nodes");
            }

            new DeferredEvent(latency) {

                /**
                 * Completes the logical handover and updates
                 * the trip statistics.
                 */
                @Override
                protected void eventAction() {

                    trip.servingFog = nextFog;
                    trip.handovers++;

                    trip.handoverDuration +=
                            Timed.getFireCount() - startedAt;

                    trip.handoverPending = false;

                    System.out.println(Timed.getFireCount()
                            + " ms | HANDOVER_FINISHED: "
                            + nextFog.name);
                }
            };
        }
    }

    /**
     * Initializes and executes the SO-SMART handover scenario.
     * <p>
     * The scenario contains two interconnected fog nodes and
     * one traveler moving from the first region to the second.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {

        GeoLocation start = new GeoLocation(46.2500, 20.1200);
        GeoLocation destination = new GeoLocation(46.2500, 20.1240);

        // Create the two regional fog computing nodes.
        ComputingAppliance fogA = new ComputingAppliance(
                Config.createNode(
                        "FogA", 4, 16L << 30, 16L << 30,
                        30, 180, 220, 125_000,
                        FOG_LATENCY, new HashMap<>()),
                start, "region-A", "test");

        ComputingAppliance fogB = new ComputingAppliance(
                Config.createNode(
                        "FogB", 4, 16L << 30, 16L << 30,
                        30, 180, 220, 125_000,
                        FOG_LATENCY, new HashMap<>()),
                destination, "region-B", "test");

        // Establish a network connection between the fog nodes.
        fogA.addNeighbor(fogB, FOG_LATENCY);


        FogRequestProcessor processor = new FogRequestProcessor(
                fogA, fogB, start, destination);

        boolean multiUser = args.length > 0 && "multi".equals(args[0]);

        TripContext trip1 = new TripContext("Traveler-1", fogA);
        TripContext trip2 = multiUser
                ? new TripContext("Traveler-2", fogA)
                : null;

// Submit the first trip request.
        processor.submitRequest(trip1);

// Submit another request at the same simulation time.
        if (multiUser) {
            processor.submitRequest(trip2);
        }

        Timed.simulateUntil(60_000);

// Verify the first trip.
        if (trip1.finishedAt == 0
                || trip1.handovers != 1
                || trip1.segments != 2
                || trip1.servingFog != fogB) {
            throw new AssertionError("First trip failed");
        }

// Verify the second trip and FIFO processing order.
        if (multiUser) {
            if (trip2.finishedAt == 0
                    || trip2.handovers != 1
                    || trip2.segments != 2
                    || trip2.servingFog != fogB
                    || trip2.plannedAt <= trip1.plannedAt) {
                throw new AssertionError("Second trip failed");
            }
        }

        int expectedRequests = multiUser ? 2 : 1;

        if (processor.getProcessedRequests() != expectedRequests) {
            throw new AssertionError("Request processing failed");
        }

        System.out.printf(
                "PASS | trips=%d | processedRequests=%d | handovers=%d%n",
                expectedRequests,
                processor.getProcessedRequests(),
                trip1.handovers + (multiUser ? trip2.handovers : 0));

    }
}
