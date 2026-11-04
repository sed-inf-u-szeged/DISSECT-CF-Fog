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
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.*;

/**
 * Verifies that SO-SMART transactions are routed through
 * the Fog node currently serving a moving traveler
 * in a Proof of Stake blockchain network.
 */
public class SoSmartPoSFogRoutingSmokeTest {

    public static void main(String[] args) throws Exception {

        SimLogger.setLogging(1, true);

        GeoLocation origin = new GeoLocation(46.2500, 20.1200);
        GeoLocation destination = new GeoLocation(46.2500, 20.1240);

        GeoLocation[] fogLocations = {
                origin,
                destination,
                new GeoLocation(46.2900, 20.2100)
        };

        Map<String, Integer> sharedLatencyMap = new HashMap<>();

        // Create the PoS consensus and validator registry.
        ValidatorRegistry validatorRegistry = new ValidatorRegistry();

        ConsensusStrategy consensus = new PoSConsensusStrategy(
                25_000L,
                10_000,
                new RSAStrategy(4096),
                new SHA256Strategy(),
                validatorRegistry,
                new StakeWeightedSelectionStrategy(42L)
        );

        long[] stakes = {10, 30, 60};

        List<ComputingAppliance> fogNodes = new ArrayList<>();

        // Create three Fog nodes with registered PoS validators.
        for (int i = 0; i < fogLocations.length; i++) {

            ComputingAppliance fog = new ComputingAppliance(
                    Config.createNode(
                            "Fog" + (char) ('A' + i),
                            100,
                            100L * 1_073_741_824L,
                            100L * 1_073_741_824L,
                            30, 180, 2200,
                            125_000,
                            100,
                            sharedLatencyMap
                    ),
                    fogLocations[i],
                    "HU",
                    "Local"
            );

            fogNodes.add(fog);

            Miner validator = new Miner(
                    consensus,
                    new RandomStrategy(),
                    fog,
                    new AlwaysAcceptValidation()
            );

            validatorRegistry.register(validator, stakes[i]);
        }

        // Connect all Fog nodes.
        for (int i = 0; i < fogNodes.size(); i++) {
            for (int j = i + 1; j < fogNodes.size(); j++) {
                fogNodes.get(i).addNeighbor(fogNodes.get(j), 33);
                fogNodes.get(j).addNeighbor(fogNodes.get(i), 33);
            }
        }

        // Create a trip crossing the FogA/FogB boundary.
        Trip trip = new Trip(
                "Trip-1",
                900_100L,
                origin,
                destination,
                List.of(
                        new TripSegment(
                                Operator.BIKE_SHARING, 0.2, 800L),
                        new TripSegment(
                                Operator.PUBLIC_TRANSPORT, 0.2, 800L)
                )
        );

        TripSimulator simulator =
                new TripSimulator(List.of(trip));

        TripFogMobilityManager mobility =
                new TripFogMobilityManager(
                        List.of(trip),
                        fogNodes,
                        100L
                );

        TripEventTransactionMapper mapper =
                new TripEventTransactionMapper();

        List<Transaction> generatedTransactions =
                new ArrayList<>();

        List<ComputingAppliance> selectedFogNodes =
                new ArrayList<>();

        // Select the validator belonging to the current Fog node.
        FogAwareNodeStrategy fogStrategy =
                new FogAwareNodeStrategy(
                        () -> mobility.getServingFog(trip.getId())
                );

        // Record which Fog nodes were selected for routing.
        FindNodeStrategy monitoredStrategy = () -> {
            Miner selectedValidator = fogStrategy.findNode();

            selectedFogNodes.add(
                    selectedValidator.computingAppliance);

            System.out.println(Timed.getFireCount()
                    + " ms | TRANSACTION_ROUTED -> "
                    + selectedValidator.computingAppliance.name);

            return selectedValidator;
        };

        // Create the transaction device.
        Map<String, Integer> deviceLatencyMap = new HashMap<>();

        EnumMap<PowerTransitionGenerator.PowerStateKind,
                Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(
                        0.065, 1.475, 2.0, 1, 2
                );

        Repository repo = new Repository(
                4_294_967_296L,
                "so-smart-pos-routing-repo",
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
                repo,
                0,
                0,
                transitions.get(
                        PowerTransitionGenerator.PowerStateKind.host)
        );

        TransactionDevice device = new TransactionDevice(
                900_000L,
                1_100_000L,
                100,
                100L,
                localMachine,
                0,
                monitoredStrategy
        );

        device.setAutoGenerateTransactions(false);
        device.geoLocation = origin;

        // Update mobility and submit events as transactions.
        simulator.setEventListener(event -> {
            mobility.onTripEvent(event);

            GeoLocation currentPosition =
                    mobility.getTravelerPosition(event.getTripId());

            if (currentPosition != null) {
                device.geoLocation = currentPosition;
            }

            Transaction transaction = mapper.toTransaction(event);

            generatedTransactions.add(transaction);
            device.submitExternalTransaction(transaction);
        });

        simulator.scheduleTrips();

        // Keep the simulation active for blockchain processing.
        new DeferredEvent(3_000_000L) {
            @Override
            protected void eventAction() {
                // No additional action is required.
            }
        };

        Timed.simulateUntil(3_000_001L);

        // Count transactions received by the blockchain network.
        long receivedTransactions = generatedTransactions.stream()
                .filter(tx -> Miner.miners.values().stream()
                        .anyMatch(validator -> validator.isTxKnown(tx)))
                .count();

        // Count transactions included in local blockchains.
        long includedTransactions = generatedTransactions.stream()
                .filter(tx -> Miner.miners.values().stream()
                        .anyMatch(validator ->
                                validator.getLocalLedger()
                                        .getChain()
                                        .stream()
                                        .anyMatch(block ->
                                                block.getTransactions()
                                                        .contains(tx))))
                .count();

        boolean routedViaFogA =
                selectedFogNodes.contains(fogNodes.get(0));

        boolean routedViaFogB =
                selectedFogNodes.contains(fogNodes.get(1));

        System.out.println(
                "\n=== SO-SMART PoS FOG ROUTING SUMMARY ===");

        System.out.println("Trip events: "
                + simulator.getEvents().size());

        System.out.println("Generated transactions: "
                + mapper.getTransactionCount());

        System.out.println("Transactions received: "
                + receivedTransactions);

        System.out.println("Transactions included: "
                + includedTransactions);

        System.out.println("Completed handovers: "
                + mobility.getTotalHandovers());

        System.out.println("Routed via FogA: "
                + routedViaFogA);

        System.out.println("Routed via FogB: "
                + routedViaFogB);

        // Verify trip execution and Fog mobility.
        if (simulator.getCompletedTrips() != 1
                || simulator.getCompletedSegments() != 2
                || simulator.getEvents().size() != 4
                || mapper.getTransactionCount() != 4
                || mobility.getTotalHandovers() != 1
                || mobility.getServingFog("Trip-1") != fogNodes.get(1)) {

            throw new AssertionError(
                    "SO-SMART trip or mobility validation failed.");
        }

        // Verify routing before and after handover.
        if (!routedViaFogA || !routedViaFogB) {
            throw new AssertionError(
                    "Transactions were not routed through both Fog nodes.");
        }

        // Verify blockchain processing.
        if (receivedTransactions != 4 || includedTransactions != 4) {
            throw new AssertionError(
                    "Not all SO-SMART transactions reached the blockchain.");
        }

        System.out.println(
                "PASS | SO-SMART PoS Fog-aware transaction routing successful");

        SimulationMetrics.getInstance().printFinalStats();
    }
}
