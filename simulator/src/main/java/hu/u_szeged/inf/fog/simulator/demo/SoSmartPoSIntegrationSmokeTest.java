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
import hu.u_szeged.inf.fog.simulator.distributed_ledger.find_node_strategy.RandomNodeStrategy;
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
 * Verifies the integration of SO-SMART trip events
 * with the existing Proof of Stake blockchain network.
 */
public class SoSmartPoSIntegrationSmokeTest {

    public static void main(String[] args) throws Exception {
        SimLogger.setLogging(2, false);

        // Create the PoS blockchain network.
        Map<String, Integer> sharedLatencyMap = new HashMap<>();

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

        for (int i = 0; i < 3; i++) {
            ComputingAppliance ca = new ComputingAppliance(
                    Config.createNode(
                            "fog" + i,
                            100,
                            100L * 1_073_741_824L,
                            100L * 1_073_741_824L,
                            30, 180, 2200,
                            125_000,
                            100,
                            sharedLatencyMap
                    ),
                    new GeoLocation(47.6, 17.9),
                    "HU",
                    "Local"
            );

            fogNodes.add(ca);

            // Accept all transactions to isolate network integration.
            Miner validator = new Miner(
                    consensus,
                    new RandomStrategy(),
                    ca,
                    new AlwaysAcceptValidation()
            );

            validatorRegistry.register(validator, stakes[i]);
        }

        // Connect all three Fog nodes.
        for (int i = 0; i < fogNodes.size(); i++) {
            for (int j = i + 1; j < fogNodes.size(); j++) {
                fogNodes.get(i).addNeighbor(fogNodes.get(j), 30);
                fogNodes.get(j).addNeighbor(fogNodes.get(i), 30);
            }
        }

        // Create a device for submitting SO-SMART transactions.
        Map<String, Integer> deviceLatencyMap = new HashMap<>();

        EnumMap<PowerTransitionGenerator.PowerStateKind,
                Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(
                        0.065, 1.475, 2.0, 1, 2
                );

        Repository repo = new Repository(
                4_294_967_296L,
                "so-smart-pos-device-repo",
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
                1_000L,
                localMachine,
                0,
                new RandomNodeStrategy()
        );

        // Only SO-SMART events should generate transactions.
        device.setAutoGenerateTransactions(false);

        // Create three SO-SMART trips.
        GeoLocation origin = new GeoLocation(46.25, 20.12);
        GeoLocation destination = new GeoLocation(46.26, 20.13);

        Trip trip1 = new Trip(
                "Trip-1",
                900_100L,
                origin,
                destination,
                List.of(
                        new TripSegment(
                                Operator.BIKE_SHARING, 2.0, 800),
                        new TripSegment(
                                Operator.CAR_SHARING, 1.0, 1_000)
                )
        );

        Trip trip2 = new Trip(
                "Trip-2",
                900_200L,
                origin,
                destination,
                List.of(
                        new TripSegment(
                                Operator.PUBLIC_TRANSPORT, 3.0, 600),
                        new TripSegment(
                                Operator.BIKE_SHARING, 1.0, 2_000)
                )
        );

        Trip trip3 = new Trip(
                "Trip-3",
                900_400L,
                origin,
                destination,
                List.of(
                        new TripSegment(
                                Operator.CAR_SHARING, 2.0, 300)
                )
        );

        TripSimulator simulator = new TripSimulator(
                List.of(trip1, trip2, trip3)
        );

        TripEventTransactionMapper mapper =
                new TripEventTransactionMapper();

        List<Transaction> generatedTransactions = new ArrayList<>();

        // Submit each trip event through the existing network.
        simulator.setEventListener(event -> {
            Transaction transaction = mapper.toTransaction(event);

            generatedTransactions.add(transaction);
            device.submitExternalTransaction(transaction);
        });

        simulator.scheduleTrips();

        // Interrupt the second trip after its first segment.
        new DeferredEvent(901_200L) {
            @Override
            protected void eventAction() {
                if (!simulator.interruptTrip("Trip-2")) {
                    throw new AssertionError(
                            "Trip interruption failed.");
                }
            }
        };

        // Keep a common simulation horizon.
        new DeferredEvent(3_000_000L) {
            @Override
            protected void eventAction() {
                // No additional action is required.
            }
        };

        Timed.simulateUntil(3_000_001L);

        // Verify trip event and transaction generation.
        int eventCount = simulator.getEvents().size();
        int transactionCount = mapper.getTransactionCount();

        if (eventCount != 10
                || transactionCount != 10
                || generatedTransactions.size() != 10) {
            throw new AssertionError(
                    "Expected 10 trip events and 10 transactions.");
        }

        // Count transactions received by at least one validator.
        long receivedTransactions = generatedTransactions.stream()
                .filter(tx -> Miner.miners.values().stream()
                        .anyMatch(miner -> miner.isTxKnown(tx)))
                .count();

        // Count transactions included in at least one local blockchain.
        long includedTransactions = generatedTransactions.stream()
                .filter(tx -> Miner.miners.values().stream()
                        .anyMatch(miner ->
                                miner.getLocalLedger()
                                        .getChain()
                                        .stream()
                                        .anyMatch(block ->
                                                block.getTransactions()
                                                        .contains(tx))))
                .count();

        System.out.println(
                "\n=== SO-SMART PoS INTEGRATION SUMMARY ===");

        System.out.println("Trip events: " + eventCount);
        System.out.println("Generated transactions: "
                + transactionCount);
        System.out.println("Transactions received by validators: "
                + receivedTransactions);
        System.out.println("Transactions present in local ledgers: "
                + includedTransactions);

        System.out.println("Completed trips: "
                + simulator.getCompletedTrips());
        System.out.println("Interrupted trips: "
                + simulator.getInterruptedTrips());
        System.out.println("Completed segments: "
                + simulator.getCompletedSegments());

        // Verify the original trip simulation results.
        if (simulator.getCompletedTrips() != 2
                || simulator.getInterruptedTrips() != 1
                || simulator.getCompletedSegments() != 4
                || Math.abs(
                simulator.getCompletedCost() - 1_150.0) > 0.001) {
            throw new AssertionError(
                    "Trip simulation results changed.");
        }

        // Verify that all transactions reached a validator.
        if (receivedTransactions != transactionCount) {
            throw new AssertionError(
                    "Not all SO-SMART transactions reached a validator.");
        }

        // Verify that blockchain processing actually occurred.
        if (includedTransactions == 0) {
            throw new AssertionError(
                    "No SO-SMART transaction was included in a blockchain.");
        }

        System.out.println(
                "PASS | SO-SMART PoS network integration successful");

        SimulationMetrics.getInstance().printFinalStats();
    }
}
