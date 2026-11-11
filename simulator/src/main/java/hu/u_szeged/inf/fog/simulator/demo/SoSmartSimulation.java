package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.TransactionDevice;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.ConsensusStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.PoSConsensusStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.PoWConsensusStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy.RSAStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy.SHA256Strategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.find_node_strategy.FindNodeStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.metrics.SimulationMetrics;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy.RandomStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validation_strategy.AlwaysAcceptValidation;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validator.ValidatorRegistry;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validator.selection.StakeWeightedSelectionStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.so_smart.*;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Configurable SO-SMART simulation supporting PoW and PoS.
 * <p>
 * Each traveler has an independent transaction device.
 * Trip events are converted into transactions and routed
 * through the serving Fog node at publication time.
 * <p>
 * Usage:
 * SoSmartSimulation PoW smoke
 * SoSmartSimulation PoS smoke
 * SoSmartSimulation PoW 300 42
 * SoSmartSimulation PoS 300 42
 */
public final class SoSmartSimulation {

    private static final long BASE_START_MS = 900_100L;
    private static final long DEVICE_START_MS = 900_000L;
    private static final long START_WINDOW_MS = 600_000L;
    private static final long NETWORK_DRAIN_MS = 600_000L;

    private static final int BLOCK_SIZE = 10_000;
    private static final int FOG_LATENCY_MS = 33;

    private SoSmartSimulation() {
    }

    public static void main(String[] args) throws Exception {

        String consensusType = args.length > 0
                ? args[0].toUpperCase()
                : "POW";

        if (!consensusType.equals("POW")
                && !consensusType.equals("POS")) {
            throw new IllegalArgumentException(
                    "Consensus must be PoW or PoS.");
        }

        boolean smokeMode = args.length < 2
                || args[1].equalsIgnoreCase("smoke");

        int travelerCount = smokeMode
                ? 3
                : Integer.parseInt(args[1]);

        long seed = args.length > 2
                ? Long.parseLong(args[2])
                : 42L;

        if (travelerCount <= 0) {
            throw new IllegalArgumentException(
                    "Traveler count must be positive.");
        }

        // Print results and warnings with detailed miner logs.
        SimLogger.setLogging(1, true);

        ValidatorRegistry validatorRegistry =
                consensusType.equals("POS")
                        ? new ValidatorRegistry()
                        : null;

        ConsensusStrategy consensus;

        if (consensusType.equals("POW")) {
            consensus = new PoWConsensusStrategy(
                    5,
                    100,
                    25_000,
                    BLOCK_SIZE,
                    new RSAStrategy(4096),
                    new SHA256Strategy()
            );
        } else {
            consensus = new PoSConsensusStrategy(
                    25_000L,
                    BLOCK_SIZE,
                    new RSAStrategy(4096),
                    new SHA256Strategy(),
                    validatorRegistry,
                    new StakeWeightedSelectionStrategy(42L)
            );
        }

        GeoLocation[] locations = createFogLocations(smokeMode);

        Map<String, Integer> fogLatencyMap = new HashMap<>();
        List<ComputingAppliance> fogNodes = new ArrayList<>();

        long[] stakes = {10, 30, 60};

        // Create the Fog blockchain network.
        for (int i = 0; i < locations.length; i++) {

            ComputingAppliance fog = new ComputingAppliance(
                    Config.createNode(
                            "Fog" + (char) ('A' + i),
                            100,
                            100L * 1_073_741_824L,
                            100L * 1_073_741_824L,
                            30, 180, 2200,
                            125_000,
                            100,
                            fogLatencyMap
                    ),
                    locations[i],
                    "HU",
                    "Local"
            );

            fogNodes.add(fog);

            Miner miner = new Miner(
                    consensus,
                    new RandomStrategy(),
                    fog,
                    new AlwaysAcceptValidation()
            );

            // Keep miners active throughout the SO-SMART workload.
            miner.setAutoShutdownOnIdle(false);

            if (validatorRegistry != null) {
                validatorRegistry.register(miner, stakes[i]);
            }
        }

        // Start energy monitoring for all Fog nodes.
        List<EnergyDataCollector> energyCollectors = new ArrayList<>();

        for (ComputingAppliance fog : fogNodes) {
            energyCollectors.add(
                    new EnergyDataCollector(fog.name, fog.iaas, false)
            );
        }

        // Connect all Fog nodes bidirectionally.
        for (int i = 0; i < fogNodes.size(); i++) {
            for (int j = i + 1; j < fogNodes.size(); j++) {
                fogNodes.get(i).addNeighbor(fogNodes.get(j), FOG_LATENCY_MS);

                fogNodes.get(j).addNeighbor(fogNodes.get(i), FOG_LATENCY_MS);
            }
        }

        // Reuse the original three-traveler regression scenario,
        // or generate a reproducible synthetic workload.
        List<Trip> trips = smokeMode
                ? createSmokeTrips(locations)
                : createGeneratedTrips(travelerCount, seed);

        int expectedSegments = 0;
        double expectedCost = 0.0;
        long lastArrivalMs = 0;

        for (Trip trip : trips) {
            expectedSegments += trip.getSegments().size();
            expectedCost += trip.getTotalCost();

            long arrivalMs = Math.addExact(
                    trip.getStartTimeMs(),
                    trip.getTotalDurationMs()
            );

            lastArrivalMs = Math.max(lastArrivalMs, arrivalMs);
        }

        int expectedEvents = 2 * trips.size() + expectedSegments;

        long simulationEndMs = Math.addExact(
                lastArrivalMs, NETWORK_DRAIN_MS);

        long movementIntervalMs = smokeMode ? 100L : 5_000L;
        long deviceTickMs = smokeMode ? 100L : 1_000L;

        TripSimulator simulator = new TripSimulator(trips);

        TripFogMobilityManager mobility =
                new TripFogMobilityManager(
                        trips,
                        fogNodes,
                        movementIntervalMs
                );

        TripEventTransactionMapper mapper =
                new TripEventTransactionMapper();

        Map<String, TransactionDevice> devices = new HashMap<>();

        Map<String, List<ComputingAppliance>> routes =
                new HashMap<>();

        List<Transaction> transactions = new ArrayList<>();

        // Create one transaction device per traveler.
        for (Trip trip : trips) {

            List<ComputingAppliance> route = new ArrayList<>();
            routes.put(trip.getId(), route);

            TransactionDevice device = createTravelerDevice(
                    trip,
                    mobility,
                    route,
                    simulationEndMs,
                    deviceTickMs,
                    smokeMode
            );

            devices.put(trip.getId(), device);
        }

        // Connect trip events to mobility and blockchain routing.
        simulator.setEventListener(event -> {

            mobility.onTripEvent(event);

            TransactionDevice device =
                    devices.get(event.getTripId());

            if (device == null) {
                throw new IllegalStateException(
                        "Missing transaction device for "
                                + event.getTripId());
            }

            GeoLocation position =
                    mobility.getTravelerPosition(event.getTripId());

            if (position != null) {
                device.geoLocation = position;
            }

            Transaction transaction = mapper.toTransaction(event);

            transactions.add(transaction);
            device.submitExternalTransaction(transaction);
        });

        SimLogger.logRun("\n=== SO-SMART CONFIGURATION ===");
        SimLogger.logRun("Consensus: " + consensusType);
        SimLogger.logRun("Mode: "
                + (smokeMode ? "SMOKE" : "GENERATED"));
        SimLogger.logRun("Travelers: " + trips.size());
        SimLogger.logRun("Trip generation seed: " + seed);
        SimLogger.logRun("Fog nodes: " + fogNodes.size());
        SimLogger.logRun("Expected events: " + expectedEvents);
        SimLogger.logRun("Simulation end (ms): " + simulationEndMs);

        simulator.scheduleTrips();

        // Keep the simulation alive through the drain period.
        new DeferredEvent(simulationEndMs) {
            @Override
            protected void eventAction() {
                // Simulation horizon marker.
            }
        };

        Timed.simulateUntil(simulationEndMs + 1L);

        // Finalize energy measurements and calculate total Fog energy in kWh.
        double totalEnergyKWh = 0.0;

        for (EnergyDataCollector collector : energyCollectors) {
            collector.stop();
            collector.tick(0L);

            totalEnergyKWh += collector.energyConsumption / 3_600_000_000.0;
        }

        SimulationMetrics.getInstance().recordTotalEnergyKWh(totalEnergyKWh);

        // Generate the map after all trip and handover events have completed.
        SoSmartTripMapVisualiser.generateMap(
                trips,
                fogNodes,
                mobility.getCompletedHandovers(),
                Paths.get(ScenarioBase.resultDirectory, "so_smart_map.html")
        );

        long receivedTransactions = transactions.stream()
                .filter(tx -> Miner.miners.values().stream()
                        .anyMatch(miner -> miner.isTxKnown(tx)))
                .count();

        long includedTransactions = transactions.stream()
                .filter(tx -> Miner.miners.values().stream()
                        .anyMatch(miner ->
                                miner.getLocalLedger()
                                        .getChain()
                                        .stream()
                                        .anyMatch(block ->
                                                block.getTransactions()
                                                        .contains(tx))))
                .count();

        SimLogger.logRun("\n=== SO-SMART SIMULATION SUMMARY ===");
        SimLogger.logRun("Consensus: " + consensusType);
        SimLogger.logRun("Total trips: " + trips.size());
        SimLogger.logRun("Transaction devices: " + devices.size());
        SimLogger.logRun("Trip events: "
                + simulator.getEvents().size());
        SimLogger.logRun("Generated transactions: "
                + mapper.getTransactionCount());
        SimLogger.logRun("Transactions received: "
                + receivedTransactions);
        SimLogger.logRun("Transactions included: "
                + includedTransactions);
        SimLogger.logRun("Completed trips: "
                + simulator.getCompletedTrips());
        SimLogger.logRun("Completed segments: "
                + simulator.getCompletedSegments());
        SimLogger.logRun("Completed handovers: "
                + mobility.getTotalHandovers());
        SimLogger.logRun("Completed trip cost (HUF): "
                + simulator.getCompletedCost());

        // Retain the exact routing checks of the old smoke tests.
        if (smokeMode) {
            for (Trip trip : trips) {
                SimLogger.logRun(
                        trip.getId() + " routing: "
                                + routeNames(routes.get(trip.getId())));
            }

            assertRoute(
                    routes.get("Trip-1"),
                    fogNodes.get(0),
                    fogNodes.get(1),
                    fogNodes.get(2)
            );

            assertRoute(
                    routes.get("Trip-2"),
                    fogNodes.get(2),
                    fogNodes.get(1),
                    fogNodes.get(0)
            );

            assertRoute(
                    routes.get("Trip-3"),
                    fogNodes.get(1)
            );

            if (mobility.getHandoverCount("Trip-1") != 2
                    || mobility.getHandoverCount("Trip-2") != 2
                    || mobility.getHandoverCount("Trip-3") != 0
                    || mobility.getTotalHandovers() != 4
                    || mobility.getServingFog("Trip-1") != fogNodes.get(2)
                    || mobility.getServingFog("Trip-2") != fogNodes.get(0)
                    || mobility.getServingFog("Trip-3") != fogNodes.get(1)) {

                throw new AssertionError(
                        "Smoke test Fog mobility validation failed.");
            }
        }

        // Validate the trip simulation for both modes.
        if (simulator.getCompletedTrips() != trips.size()
                || simulator.getInterruptedTrips() != 0
                || simulator.getCompletedSegments() != expectedSegments
                || simulator.getEvents().size() != expectedEvents
                || mapper.getTransactionCount() != expectedEvents
                || transactions.size() != expectedEvents
                || devices.size() != trips.size()
                || Math.abs(
                simulator.getCompletedCost() - expectedCost)
                > 0.0001) {

            throw new AssertionError(
                    "SO-SMART trip or transaction generation failed.");
        }

        SimulationMetrics.getInstance().printFinalStats();

        // Smoke tests must achieve complete blockchain inclusion.
        if (smokeMode) {
            if (receivedTransactions != expectedEvents
                    || includedTransactions != expectedEvents) {

                throw new AssertionError(
                        "Smoke test blockchain integration failed.");
            }

            SimLogger.logRun(
                    "PASS | SO-SMART " + consensusType
                            + " smoke test successful");
        } else if (receivedTransactions == expectedEvents
                && includedTransactions == expectedEvents) {

            SimLogger.logRun(
                    "PASS | SO-SMART " + consensusType
                            + " workload fully included");
        } else {
            SimLogger.logRun(
                    "WARNING | Workload finished with "
                            + (expectedEvents - includedTransactions)
                            + " transactions not included "
                            + "in the observed local ledgers.");
        }
    }

    /**
     * Returns Fog locations for the regression or generated scenario.
     */
    private static GeoLocation[] createFogLocations(
            boolean smokeMode) {

        if (smokeMode) {
            return new GeoLocation[]{
                    new GeoLocation(46.2500, 20.1200),
                    new GeoLocation(46.2500, 20.1240),
                    new GeoLocation(46.2500, 20.1280)
            };
        }

        return new GeoLocation[]{
                new GeoLocation(46.2200, 20.0800),
                new GeoLocation(46.2500, 20.1200),
                new GeoLocation(46.2800, 20.1600)
        };
    }

    /**
     * Creates the original three-traveler regression scenario.
     */
    private static List<Trip> createSmokeTrips(
            GeoLocation[] locations) {

        Trip trip1 = new Trip(
                "Trip-1",
                BASE_START_MS,
                locations[0],
                locations[2],
                List.of(
                        new TripSegment(
                                Operator.BIKE_SHARING, 0.3, 1_500L),
                        new TripSegment(
                                Operator.PUBLIC_TRANSPORT, 0.3, 1_500L)
                )
        );

        Trip trip2 = new Trip(
                "Trip-2",
                BASE_START_MS + 100L,
                locations[2],
                locations[0],
                List.of(
                        new TripSegment(
                                Operator.CAR_SHARING, 0.3, 1_800L),
                        new TripSegment(
                                Operator.BIKE_SHARING, 0.3, 1_800L)
                )
        );

        Trip trip3 = new Trip(
                "Trip-3",
                BASE_START_MS + 200L,
                locations[1],
                new GeoLocation(46.2500, 20.1244),
                List.of(
                        new TripSegment(
                                Operator.PUBLIC_TRANSPORT, 0.03, 1_200L)
                )
        );

        return List.of(trip1, trip2, trip3);
    }

    /**
     * Generates synthetic trips and shifts their start times
     * after blockchain infrastructure initialization.
     */
    private static List<Trip> createGeneratedTrips(
            int travelerCount,
            long seed) {

        TripGenerator generator = new TripGenerator(
                new GeoLocation(46.25, 20.12),
                5.0,
                seed
        );

        List<Trip> generated = generator.generateTrips(
                travelerCount, START_WINDOW_MS);

        List<Trip> scheduled = new ArrayList<>(generated.size());

        for (Trip trip : generated) {
            scheduled.add(new Trip(
                    trip.getId(),
                    BASE_START_MS + trip.getStartTimeMs(),
                    trip.getOrigin(),
                    trip.getDestination(),
                    trip.getSegments()
            ));
        }

        return scheduled;
    }

    /**
     * Creates a Fog-aware blockchain device for a traveler.
     */
    private static TransactionDevice createTravelerDevice(
            Trip trip,
            TripFogMobilityManager mobility,
            List<ComputingAppliance> selectedRoute,
            long simulationEndMs,
            long deviceTickMs,
            boolean verboseRouting) {

        FogAwareNodeStrategy fogStrategy =
                new FogAwareNodeStrategy(
                        () -> mobility.getServingFog(trip.getId())
                );

        FindNodeStrategy monitoredStrategy = () -> {

            Miner miner = fogStrategy.findNode();

            selectedRoute.add(miner.computingAppliance);

            if (verboseRouting) {
                SimLogger.logRun(trip.getId()
                        + " | TRANSACTION_ROUTED -> "
                        + miner.computingAppliance.name);
            }

            return miner;
        };

        Map<String, Integer> deviceLatencyMap = new HashMap<>();

        EnumMap<PowerTransitionGenerator.PowerStateKind,
                Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(
                        0.065, 1.475, 2.0, 1, 2
                );

        Repository repository = new Repository(
                4_294_967_296L,
                "so-smart-device-" + trip.getId(),
                3250, 3250, 3250,
                deviceLatencyMap,
                transitions.get(
                        PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(
                        PowerTransitionGenerator.PowerStateKind.network)
        );

        PhysicalMachine localMachine = new PhysicalMachine(
                2,
                0.001,
                2_147_483_648L,
                repository,
                0,
                0,
                transitions.get(
                        PowerTransitionGenerator.PowerStateKind.host)
        );

        TransactionDevice device = new TransactionDevice(
                DEVICE_START_MS,
                simulationEndMs,
                100,
                deviceTickMs,
                localMachine,
                0,
                monitoredStrategy
        );

        device.setAutoGenerateTransactions(false);
        device.geoLocation = trip.getOrigin();

        return device;
    }

    /**
     * Verifies that Fog nodes occurred in the expected order.
     */
    private static void assertRoute(
            List<ComputingAppliance> actual,
            ComputingAppliance... expected) {

        if (actual == null) {
            throw new AssertionError("Missing Fog routing history.");
        }

        int previousIndex = -1;

        for (ComputingAppliance expectedFog : expected) {

            int matchingIndex = -1;

            for (int i = previousIndex + 1;
                 i < actual.size(); i++) {

                if (actual.get(i) == expectedFog) {
                    matchingIndex = i;
                    break;
                }
            }

            if (matchingIndex < 0) {
                throw new AssertionError(
                        "Expected Fog routing sequence not found: "
                                + expectedFog.name);
            }

            previousIndex = matchingIndex;
        }
    }

    /**
     * Formats the selected Fog routing history.
     */
    private static String routeNames(
            List<ComputingAppliance> route) {

        return route.stream()
                .map(fog -> fog.name)
                .collect(Collectors.joining(" -> "));
    }
}
