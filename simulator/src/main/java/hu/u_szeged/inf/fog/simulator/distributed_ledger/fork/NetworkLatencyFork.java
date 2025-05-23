package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.fork.partition.HopBasedPartition;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.chain.ForkReorgTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.Utils;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.List;
import java.util.Random;

/**
 * The {@code NetworkLatencyFork} class simulates a fork scenario in a blockchain network,
 * triggered by artificially introduced network latency that leads to blockchain reorganizations.
 *
 * <p>This class extends the {@code ForkScenario} abstract class, implementing the
 * {@code executeScenario} method to simulate network partitions based on latency, causing
 * miners to reorganize their blockchain state up to a predefined maximum rollback depth.</p>
 *
 * <p>The scenario selects a random start node from the available computing appliances and
 * partitions the network using a hop-based strategy, thereby affecting a subset of miners.</p>
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * NetworkLatencyFork latencyFork = new NetworkLatencyFork(true, 0.5, 10000, 2000);
 * latencyFork.executeScenario(minerList);
 * }</pre>
 *
 * @see ForkScenario
 * @see HopBasedPartition
 * @see ForkReorgTask
 */
public class NetworkLatencyFork extends ForkScenario {
    private static Random RANDOM = SeedSyncer.centralRnd;
    /**
     * Maximum depth for blockchain rollback during fork reorganization.
     */
    private static final int MAX_ROLLBACK_DEPTH = 4;
    /**
     * Strategy to partition the network based on hop counts from a start node.
     */
    HopBasedPartition partitionStrategy;

    /**
     * Constructs a {@code NetworkLatencyFork} instance with specified parameters for recurrence,
     * probability, and interval timing.
     *
     * @param recurring     indicates if the scenario should recur periodically.
     * @param probability   probability of the scenario occurring.
     * @param baseInterval  base interval between scenario occurrences (in milliseconds).
     * @param variance      variance in the interval (in milliseconds).
     */
    public NetworkLatencyFork(boolean recurring, double probability, long baseInterval, long variance) {
        super("Network Latency Fork", recurring, probability, baseInterval, variance);
        this.partitionStrategy = new HopBasedPartition(1);
        int randomIndex = RANDOM.nextInt(ComputingAppliance.allComputingAppliances.size());
        ComputingAppliance startNode = ComputingAppliance.allComputingAppliances.get(randomIndex);
        partitionStrategy.setStartNode(startNode);
    }

    /**
     * Executes the network latency fork scenario by partitioning the network and causing selected miners to reorganize their blockchain states.
     *
     * <p>Miners affected by the partition will schedule a {@code ForkReorgTask}, initiating a rollback to a random depth up to {@code MAX_ROLLBACK_DEPTH} blocks.</p>

     * @param miners the list of miners participating in the blockchain network.
     */
    @Override
    public void executeScenario(List<Miner> miners) {
        List<ComputingAppliance> selectedNodes = partitionStrategy.selectNodes(Utils.miners2ComputingAppliances(miners));
        SimLogger.logRun("[Fork] Triggered: " + scenarioName + ", for " + selectedNodes.size() + " miners");
        selectedNodes.stream().map(Utils::ca2Miner).forEach((miner) -> {
            miner.scheduleTask(new ForkReorgTask(RANDOM.nextInt(MAX_ROLLBACK_DEPTH) + 1));
        });
    }
}