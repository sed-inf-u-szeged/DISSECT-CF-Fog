package hu.u_szeged.inf.fog.simulator.distributed_ledger.metrics;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.*;

/**
 * A centralized, singleton class for recording and reporting
 * various simulation metrics, such as:
 * <ul>
 *   <li>Network statistics (messages/bytes sent)</li>
 *   <li>Transaction and block validations (accepted vs. rejected)</li>
 *   <li>Transaction confirmation times</li>
 *   <li>Block/transaction propagation delays</li>
 * </ul>
 */
public class SimulationMetrics {

    /**
     * The sole instance (Singleton) of this class.
     */
    private static final SimulationMetrics INSTANCE = new SimulationMetrics();

    /**
     * Retrieve the singleton instance.
     *
     * @return the {@link SimulationMetrics} singleton
     */
    public static SimulationMetrics getInstance() {
        return INSTANCE;
    }

    /**
     * Private constructor to enforce singleton pattern.
     */
    private SimulationMetrics() {
    }

    // ----------------------------------------------------------------
    // Inner class to store each miner's statistics
    // ----------------------------------------------------------------

    /**
     * Holds various statistics for a single miner, such as
     * how many network messages/bytes they sent,
     * and how many transactions/blocks they validated or rejected.
     */
    public static class MinerStats {
        /**
         * The number of network messages this miner has sent.
         */
        public long networkMessagesSent = 0;
        /**
         * The total network bytes this miner has sent.
         */
        public long networkBytesSent = 0;

        /**
         * The number of transactions this miner accepted (validated).
         */
        public long transactionsValidated = 0;
        /**
         * The number of transactions this miner rejected.
         */
        public long transactionsRejected = 0;

        /**
         * The number of blocks this miner accepted (validated).
         */
        public long blocksValidated = 0;
        /**
         * The number of blocks this miner rejected.
         */
        public long blocksRejected = 0;
    }

    // ----------------------------------------------------------------
    // A map of Miner -> MinerStats
    // ----------------------------------------------------------------

    /**
     * Holds stats on a per-miner basis.
     */
    private final Map<Miner, MinerStats> statsPerMiner = new HashMap<>();

    /**
     * Helper to retrieve (and if needed, create) a {@link MinerStats}
     * entry for the given miner.
     *
     * @param m the miner
     * @return the stats object for that miner
     */
    private MinerStats getStatsFor(Miner m) {
        return statsPerMiner.computeIfAbsent(m, k -> new MinerStats());
    }

    // ----------------------------------------------------------------
    // Global counters (across all miners)
    // ----------------------------------------------------------------

    /**
     * Global count of all network messages sent (across every miner).
     */
    private long globalNetworkMessagesSent = 0;
    /**
     * Global sum of all network bytes sent (across every miner).
     */
    private long globalNetworkBytesSent = 0;

    /**
     * How many transactions were accepted overall.
     */
    private long globalTransactionsAccepted = 0;
    /**
     * How many transactions were rejected overall.
     */
    private long globalTransactionsRejected = 0;
    /**
     * How many blocks were accepted overall.
     */
    private long globalBlocksAccepted = 0;
    /**
     * How many blocks were rejected overall.
     */
    private long globalBlocksRejected = 0;

    /**
     * Count of how many transactions got “on-chain” (i.e., included in accepted blocks).
     */
    private long globalTransactionsOnChain = 0;


    // ----------------------------------------------------------------
    // Block and Transaction Propagation times
    // ----------------------------------------------------------------

    /**
     * For each block, we store a sub-map of (Miner -> arrival time).
     * This way we can later compute how quickly it propagated to each miner.
     */
    private final Map<Block, Map<Miner, Long>> blockArrivalTimes = new HashMap<>();


    /**
     * When each block was created (mined) by its producing miner.
     */
    private final Map<Block, Long> blockCreationTime = new HashMap<>();

    // ----------------------------------------------------------------
    // 1) Network communications
    // ----------------------------------------------------------------

    /**
     * Record that a miner sent a network message of the given size (in bytes).
     *
     * @param sender    the miner sending the message
     * @param sizeBytes how many bytes were sent
     */
    public void recordNetworkSend(Miner sender, long sizeBytes) {
        globalNetworkMessagesSent++;
        globalNetworkBytesSent += sizeBytes;

        MinerStats ms = getStatsFor(sender);
        ms.networkMessagesSent++;
        ms.networkBytesSent += sizeBytes;
    }

    // ----------------------------------------------------------------
    // 2) Throughput (globally)
    // ----------------------------------------------------------------

    /**
     * Whenever a transaction is successfully added to a block
     * and accepted on-chain. This increments the global count of
     * “transactions on chain,” used for throughput calculations.
     */
    public void recordTransactionOnChain() {
        globalTransactionsOnChain++;
    }

    // ----------------------------------------------------------------
    // 3) Result of block and transaction validations
    // ----------------------------------------------------------------

    /**
     * Record that a miner has validated a transaction, either accepted or rejected.
     *
     * @param miner    the miner performing validation
     * @param accepted true if accepted, false if rejected
     */
    public void recordTransactionValidation(Miner miner, boolean accepted) {
        if (accepted) {
            globalTransactionsAccepted++;
            getStatsFor(miner).transactionsValidated++;
        } else {
            globalTransactionsRejected++;
            getStatsFor(miner).transactionsRejected++;
        }
    }

    /**
     * Record that a miner has validated a block, either accepted or rejected.
     *
     * @param miner    the miner performing validation
     * @param accepted true if accepted, false if rejected
     */
    public void recordBlockValidation(Miner miner, boolean accepted) {
        if (accepted) {
            globalBlocksAccepted++;
            getStatsFor(miner).blocksValidated++;
        } else {
            globalBlocksRejected++;
            getStatsFor(miner).blocksRejected++;
        }
    }

    // ----------------------------------------------------------------
    // 4) Block/Transaction Propagation Delay
    // ----------------------------------------------------------------

    /**
     * Mark the simulation time at which a block was created
     * by the miner who successfully mined it.
     * Also ensures a sub-map is created for storing arrival times.
     *
     * @param block the newly created block
     * @param time  the simulation time it was mined
     */
    public void markBlockCreated(Block block, long time) {
        blockCreationTime.putIfAbsent(block, time);
        blockArrivalTimes.putIfAbsent(block, new HashMap<>());
    }

    /**
     * Mark the simulation time at which a particular miner
     * received a block.
     *
     * @param block the block
     * @param miner the miner receiving it
     * @param time  the simulation time
     */
    public void markBlockArrived(Block block, Miner miner, long time) {
        blockArrivalTimes
                .computeIfAbsent(block, k -> new HashMap<>())
                .putIfAbsent(miner, time);
    }


    // ----------------------------------------------------------------
    // Print / finalize stats at end of simulation
    // ----------------------------------------------------------------

    /**
     * Prints all currently recorded statistics:
     * <ul>
     *   <li>Global network usage (messages/bytes sent)</li>
     *   <li>Per-miner network usage</li>
     *   <li>Throughput (transactions on chain / total time)</li>
     *   <li>Average transaction confirmation time</li>
     *   <li>Counts of accepted/rejected blocks and transactions</li>
     *   <li>Average block propagation delay</li>
     * </ul>
     * <p>
     * Call this at the end of the simulation to get a summary of results.
     */
    public void printFinalStats() {
        long simEndTime = Timed.getFireCount(); // the final simulation "tick" or time

        SimLogger.logRun("=== SIMULATION METRICS ===");

        // 1) Network communications
        SimLogger.logRun("Global messages sent: " + globalNetworkMessagesSent);
        SimLogger.logRun("Global bytes sent:    " + globalNetworkBytesSent);

        // Per-miner
        for (Map.Entry<Miner, MinerStats> e : statsPerMiner.entrySet()) {
            Miner miner = e.getKey();
            MinerStats ms = e.getValue();
            SimLogger.logRun("  Miner " + miner.getName()
                    + " -> messagesSent: " + ms.networkMessagesSent
                    + ", bytesSent: " + ms.networkBytesSent);
        }

        // 2) Throughput
        double totalSimTime = simEndTime; // Timed starts at 0, so "end time" = totalSimTime
        double tps = (double) globalTransactionsOnChain / (totalSimTime > 0 ? totalSimTime : 1.0);
        SimLogger.logRun("Transactions On-Chain: " + globalTransactionsOnChain);
        SimLogger.logRun("Throughput (TPS):      " + tps);


        // 3) Validation results
        SimLogger.logRun("Transactions accepted: " + globalTransactionsAccepted);
        SimLogger.logRun("Transactions rejected: " + globalTransactionsRejected);
        SimLogger.logRun("Blocks accepted:       " + globalBlocksAccepted);
        SimLogger.logRun("Blocks rejected:       " + globalBlocksRejected);

        // per-miner acceptance
        for (Map.Entry<Miner, MinerStats> e : statsPerMiner.entrySet()) {
            Miner m = e.getKey();
            MinerStats ms = e.getValue();
            SimLogger.logRun("  Miner " + m.getName()
                    + " acceptedTx=" + ms.transactionsValidated
                    + " rejectedTx=" + ms.transactionsRejected
                    + " acceptedBlk=" + ms.blocksValidated
                    + " rejectedBlk=" + ms.blocksRejected);
        }

        // 4) Block propagation delay
        double sumBlockPropagation = 0.0;
        int blockPropagationCount = 0;
        for (Block block : blockArrivalTimes.keySet()) {
            Long createT = blockCreationTime.get(block);
            if (createT == null) {
                continue; // skip blocks missing a creation time
            }
            Map<Miner, Long> arrivals = blockArrivalTimes.get(block);
            for (Long arrivalTime : arrivals.values()) {
                sumBlockPropagation += (arrivalTime - createT);
                blockPropagationCount++;
            }
        }
        double avgBlockPropagation = blockPropagationCount > 0
                ? sumBlockPropagation / blockPropagationCount
                : 0.0;
        SimLogger.logRun("Avg Block Propagation Delay: " + avgBlockPropagation);

        SimLogger.logRun("=== END OF METRICS ===");
    }
}

