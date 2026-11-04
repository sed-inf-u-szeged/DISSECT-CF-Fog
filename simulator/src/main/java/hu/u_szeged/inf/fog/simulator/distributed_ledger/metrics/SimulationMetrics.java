package hu.u_szeged.inf.fog.simulator.distributed_ledger.metrics;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.*;

/**
 * SimulationMetrics class is a singleton that collects and stores various statistics.
 */
public class SimulationMetrics {

    /**
     * The sole instance (Singleton) of this class.
     */
    private static final SimulationMetrics INSTANCE = new SimulationMetrics();

    /**
     * Stores the number of blocks produced by each proposer.
     */
    private final Map<String, Long> blocksProducedByProposer = new LinkedHashMap<>();

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

    /**
     * Stores the simulation time when each transaction was generated.
     */
    private final Map<String, Long> transactionCreationTimes = new HashMap<>();

    /**
     * Stores the first block inclusion time of each transaction.
     */
    private final Map<String, Long> transactionInclusionTimes = new HashMap<>();

    /**
     * Stores the pending transaction count at each change.
     */
    private final NavigableMap<Long, Integer> pendingTransactionsHistory =
            new TreeMap<>();

    /**
     * Maximum number of pending transactions observed.
     */
    private int peakPendingTransactions = 0;

    /**
     * Stores the latest active blockchain snapshot of each miner.
     */
    private final Map<Miner, Set<Block>> activeLedgerBlocks = new HashMap<>();

    /**
     * Counts local ledger reorganization operations.
     */
    private int localReorganizationCount = 0;

    /**
     * Stores the first majority acceptance time of each transaction.
     */
    private final Map<String, Long> transactionMajorityAcceptanceTimes = new HashMap<>();

    /**
     * Stores the total infrastructure energy consumption in kWh.
     */
    private double totalEnergyKWh = Double.NaN;

    // ----------------------------------------------------------------
    // Inner class to store each miner's statistics
    // ----------------------------------------------------------------

    /**
     * Statistics how many network messages/bytes they sent, and how many transactions/blocks they validated or rejected.
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
     * Helper to retrieve (and if needed, create) a {@link MinerStats} entry for the given miner.
     *
     * @param m the miner
     * @return the stats object for that miner
     */
    private MinerStats getStatsFor(Miner m) {
        return statsPerMiner.computeIfAbsent(m, k -> new MinerStats());
    }

    // ----------------------------------------------------------------
    // Global counters
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
    // 2) Throughput
    // ----------------------------------------------------------------


    /**
     * Records the creation time of a transaction.
     *
     * @param transaction the newly generated transaction
     */
    public void recordTransactionCreated(Transaction transaction) {
        if (transactionCreationTimes.putIfAbsent(
                transaction.getId(), Timed.getFireCount()) == null) {
            recordPendingTransactions();
        }
    }

    /**
     * Records the first inclusion of transactions in a produced block.
     * Each transaction is counted only once.
     *
     * @param block the produced block
     */
    public void recordBlockTransactions(Block block) {
        boolean changed = false;

        for (Transaction transaction : block.getTransactions()) {
            if (transactionInclusionTimes.putIfAbsent(
                    transaction.getId(), Timed.getFireCount()) == null) {
                globalTransactionsOnChain++;
                changed = true;
            }
        }

        if (changed) {
            recordPendingTransactions();
        }
    }

    /**
     * Calculates the average time between transaction creation
     * and its first inclusion in a block.
     *
     * @return the average inclusion delay in milliseconds
     */
    public double getAverageTransactionInclusionTime() {
        long totalDelay = 0;
        int count = 0;

        for (Map.Entry<String, Long> entry : transactionInclusionTimes.entrySet()) {
            Long createdAt = transactionCreationTimes.get(entry.getKey());

            if (createdAt != null) {
                totalDelay += entry.getValue() - createdAt;
                count++;
            }
        }

        return count == 0 ? 0.0 : (double) totalDelay / count;
    }

    /**
     * Returns the number of generated transactions that have
     * not yet been included in a produced block.
     *
     * @return the number of transactions without block inclusion
     */
    public int getUnincludedTransactionCount() {
        return transactionCreationTimes.size()
                - transactionInclusionTimes.size();
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
        Long previous = blockCreationTime.putIfAbsent(block, time);

        if (previous == null && block.getProposerId() != null) {
            blocksProducedByProposer.merge(block.getProposerId(), 1L, Long::sum);
        }

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
    // 5) Transaction Majority Acceptance
    // ----------------------------------------------------------------

    /**
     * Updates a miner's active blockchain snapshot and records
     * transactions that reach majority acceptance.
     *
     * @param miner the miner owning the ledger
     * @param chain the current active chain
     */
    public void recordLedgerSnapshot(Miner miner, List<Block> chain) {
        activeLedgerBlocks.put(miner, new HashSet<>(chain));

        int majority = Miner.miners.size() / 2 + 1;
        long currentTime = Timed.getFireCount();

        for (Block block : chain) {
            if (countActiveLedgersContaining(block) < majority) {
                continue;
            }

            for (Transaction transaction : block.getTransactions()) {
                String transactionId = transaction.getId();

                if (transactionCreationTimes.containsKey(transactionId)) {
                    transactionMajorityAcceptanceTimes.putIfAbsent(
                            transactionId, currentTime);
                }
            }
        }
    }

    /**
     * Calculates the average delay between transaction creation
     * and its first majority acceptance.
     *
     * @return the average majority acceptance delay in milliseconds
     */
    public double getAverageTransactionMajorityAcceptanceTime() {
        long totalDelay = 0;
        int count = 0;

        for (Map.Entry<String, Long> entry :
                transactionMajorityAcceptanceTimes.entrySet()) {

            Long createdAt = transactionCreationTimes.get(entry.getKey());

            if (createdAt != null) {
                totalDelay += entry.getValue() - createdAt;
                count++;
            }
        }

        return count == 0 ? 0.0 : (double) totalDelay / count;
    }

    // ----------------------------------------------------------------
    // 6) Energy Efficiency
    // ----------------------------------------------------------------

    /**
     * Records the total energy consumption of the simulation.
     *
     * @param energyKWh total energy consumption in kWh
     */
    public void recordTotalEnergyKWh(double energyKWh) {
        if (!Double.isFinite(energyKWh) || energyKWh < 0) {
            throw new IllegalArgumentException(
                    "Energy consumption must be finite and non-negative.");
        }

        this.totalEnergyKWh = energyKWh;
    }

    /**
     * Calculates the energy consumption per majority-accepted transaction.
     *
     * @return energy consumption in Wh/transaction,
     * or NaN if the value cannot be calculated
     */
    public double getEnergyPerMajorityAcceptedTransactionWh() {
        int acceptedTransactions = transactionMajorityAcceptanceTimes.size();

        if (Double.isNaN(totalEnergyKWh) || acceptedTransactions == 0) {
            return Double.NaN;
        }

        return totalEnergyKWh * 1000.0 / acceptedTransactions;
    }

    // ----------------------------------------------------------------
    // 7) Pending Transactions Over Time
    // ----------------------------------------------------------------

    /**
     * Records the current number of pending transactions.
     */
    private void recordPendingTransactions() {
        int pending = getUnincludedTransactionCount();

        pendingTransactionsHistory.put(Timed.getFireCount(), pending);
        peakPendingTransactions = Math.max(peakPendingTransactions, pending);
    }

    /**
     * Calculates the time-weighted average pending transaction count.
     *
     * @return average number of pending transactions
     */
    public double getAveragePendingTransactions() {
        long previousTime = 0;
        int previousCount = 0;
        double weightedSum = 0.0;

        for (Map.Entry<Long, Integer> entry :
                pendingTransactionsHistory.entrySet()) {

            long currentTime = entry.getKey();

            weightedSum += (currentTime - previousTime)
                    * (double) previousCount;

            previousTime = currentTime;
            previousCount = entry.getValue();
        }

        long endTime = Timed.getFireCount();

        weightedSum += Math.max(0, endTime - previousTime)
                * (double) previousCount;

        return endTime == 0 ? 0.0 : weightedSum / endTime;
    }

    /**
     * Returns the recorded pending transaction timeline.
     *
     * @return unmodifiable timeline indexed by simulation time
     */
    public NavigableMap<Long, Integer> getPendingTransactionsHistory() {
        return Collections.unmodifiableNavigableMap(
                pendingTransactionsHistory);
    }

    // ----------------------------------------------------------------
    // 8) Fork and Orphan Block Metrics
    // ----------------------------------------------------------------

    /**
     * Counts the active local ledgers containing a block.
     */
    private int countActiveLedgersContaining(Block block) {
        int count = 0;

        for (Set<Block> blocks : activeLedgerBlocks.values()) {
            if (blocks.contains(block)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Records a local blockchain reorganization.
     */
    public void recordLocalReorganization() {
        localReorganizationCount++;
    }

    /**
     * Counts produced blocks not present in any final local ledger.
     */
    public int getBlocksAbsentFromAllLedgers() {
        int count = 0;

        for (Block block : blockCreationTime.keySet()) {
            if (countActiveLedgersContaining(block) == 0) {
                count++;
            }
        }

        return count;
    }

    /**
     * Counts produced blocks that do not have majority support
     * among the final local blockchain snapshots.
     */
    public int getBlocksWithoutFinalMajority() {
        int majority = Miner.miners.size() / 2 + 1;
        int count = 0;

        for (Block block : blockCreationTime.keySet()) {
            if (countActiveLedgersContaining(block) < majority) {
                count++;
            }
        }

        return count;
    }

    /**
     * Calculates the percentage of produced blocks without
     * majority support at the end of the simulation.
     *
     * @return percentage of blocks without final majority support
     */
    public double getNonMajorityBlockRate() {
        int produced = blockCreationTime.size();

        if (produced == 0) {
            return 0.0;
        }

        return 100.0 * getBlocksWithoutFinalMajority() / produced;
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
     *   <li>Counts of accepted/rejected blocks and transactions</li>
     *   <li>Average block propagation delay</li>
     * </ul>
     * <p>
     * Call this at the end of the simulation to get a summary of results.
     */
    public void printFinalStats() {
        long simEndTime = Timed.getFireCount();

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
        double totalSimTime = simEndTime;
        double tps = (double) globalTransactionsOnChain / (totalSimTime > 0 ? totalSimTime / 1000 : 1.0);
        SimLogger.logRun("Transactions generated: " + transactionCreationTimes.size());
        SimLogger.logRun("Transactions first included: " + globalTransactionsOnChain);
        SimLogger.logRun("Transactions not included: " + getUnincludedTransactionCount());
        SimLogger.logRun("Avg Transaction Inclusion Time (ms): "
                + getAverageTransactionInclusionTime());
        SimLogger.logRun("First-Inclusion Throughput (TPS):      " + tps);


        // 3) Validation results
        SimLogger.logRun("Transactions accepted: " + globalTransactionsAccepted);
        SimLogger.logRun("Transactions rejected: " + globalTransactionsRejected);
        SimLogger.logRun("Blocks accepted:       " + globalBlocksAccepted);
        SimLogger.logRun("Blocks rejected:       " + globalBlocksRejected);
        SimLogger.logRun("Blocks produced:       " + blockCreationTime.size());
        double avgTransactionsPerBlock = blockCreationTime.isEmpty()
                ? 0.0
                : (double) globalTransactionsOnChain / blockCreationTime.size();

        SimLogger.logRun("Avg Transactions per Block: " + avgTransactionsPerBlock);
        double blocksPerMinute = simEndTime > 0
                ? blockCreationTime.size() * 60000.0 / simEndTime
                : 0.0;

        SimLogger.logRun("Blocks per Minute: " + blocksPerMinute);

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
                continue;
            }
            Map<Miner, Long> arrivals = blockArrivalTimes.get(block);
            for (Long arrivalTime : arrivals.values()) {
                sumBlockPropagation += (arrivalTime - createT);
                blockPropagationCount++;
            }
        }
        double avgBlockPropagation = blockPropagationCount > 0 ? sumBlockPropagation / blockPropagationCount : 0.0;
        SimLogger.logRun("Avg Block Propagation Delay: " + avgBlockPropagation);

        if (!blocksProducedByProposer.isEmpty()) {
            SimLogger.logRun("Blocks produced by proposer:");

            for (Map.Entry<String, Long> entry : blocksProducedByProposer.entrySet()) {
                SimLogger.logRun(
                        "  " + entry.getKey() + ": " + entry.getValue()
                );
            }
        }

        // 5) Transaction Majority Acceptance
        SimLogger.logRun("Transactions majority accepted: "
                + transactionMajorityAcceptanceTimes.size());

        SimLogger.logRun("Transactions awaiting majority acceptance: "
                + (transactionCreationTimes.size()
                - transactionMajorityAcceptanceTimes.size()));

        SimLogger.logRun("Avg Transaction Majority Acceptance Time (ms): "
                + getAverageTransactionMajorityAcceptanceTime());

        // 6) Energy efficiency
        if (!Double.isNaN(totalEnergyKWh)) {
            SimLogger.logRun("Total energy (kWh): " + totalEnergyKWh);

            double energyPerTransaction =
                    getEnergyPerMajorityAcceptedTransactionWh();

            if (Double.isNaN(energyPerTransaction)) {
                SimLogger.logRun(
                        "Energy per majority-accepted transaction (Wh/tx): N/A");
            } else {
                SimLogger.logRun(
                        "Energy per majority-accepted transaction (Wh/tx): "
                                + energyPerTransaction);
            }
        }

        // 7) Pending transactions
        SimLogger.logRun("Peak pending transactions: "
                + peakPendingTransactions);

        SimLogger.logRun("Avg pending transactions (time-weighted): "
                + getAveragePendingTransactions());

        SimLogger.logRun("Pending timeline data points: "
                + pendingTransactionsHistory.size());

        // 8) Fork and orphan block metrics
        SimLogger.logRun("Local ledger reorganizations: "
                + localReorganizationCount);

        SimLogger.logRun("Blocks absent from all final ledgers: "
                + getBlocksAbsentFromAllLedgers());

        SimLogger.logRun("Blocks without final majority: "
                + getBlocksWithoutFinalMajority());

        SimLogger.logRun("Non-majority block rate (%): "
                + getNonMajorityBlockRate());

        SimLogger.logRun("=== END OF METRICS ===");
    }
}

