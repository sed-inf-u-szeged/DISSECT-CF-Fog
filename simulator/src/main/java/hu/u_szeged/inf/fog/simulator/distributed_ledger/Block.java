package hu.u_szeged.inf.fog.simulator.distributed_ledger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.metrics.SimulationMetrics;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * The `Block` class represents a block in the distributed ledger.
 * It contains a list of transactions and other metadata such as size, timestamp, and difficulty.
 * The block is part of a distributed ledger and is used to store transactions securely.
 */
public class Block {
    private final String id;
    private final List<Transaction> transactions;
    private long size;
    private boolean nonceFound = false;
    private long timestamp;
    private long difficulty;

    private boolean technicallyFull = false;

    final DistributedLedger distributedLedger;

    /**
     * Constructs a new `Block` with the specified distributed ledger and difficulty level.
     *
     * @param distributedLedger the distributed ledger to which this block belongs
     * @param difficulty the difficulty level of the block, used for proof of work
     */
    public Block(DistributedLedger distributedLedger, long difficulty) {
        this.distributedLedger = distributedLedger;
        this.id = Utils.generateFakeHash((int) difficulty);
        this.transactions = new ArrayList<>();
        this.size = 0;
    }

    /**
     * Returns the difficulty level of the block.
     *
     * @return the difficulty level of the block
     */
    public long getDifficulty() {
        return difficulty;
    }

    /**
     * Checks if the nonce has been found for the block.
     * The nonce is a value that, when added to the block, results in a hash that meets the difficulty requirements.
     *
     * @return true if the nonce has been found, false otherwise
     */
    public boolean isNonceFound() {
        return nonceFound;
    }

    /**
     * Sets the nonce found status for the block.
     * This method is called when a valid nonce is discovered during the mining process.
     *
     * @param nonceFound the nonce found status
     */
    public void setNonceFound(boolean nonceFound) {
        this.nonceFound = nonceFound;
    }

    /**
     * Returns the timestamp of the block.
     * The timestamp indicates when the block was created or finalized.
     *
     * @return the timestamp of the block
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the difficulty level of the block.
     * The difficulty level determines how hard it is to find a valid nonce for the block.
     *
     * @param difficulty the difficulty level
     */
    public void setDifficulty(long difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * Returns the list of transactions in the block.
     * The transactions are the core data stored in the block.
     *
     * @return the list of transactions in the block
     */
    public List<Transaction> getTransactions() {
        return transactions;
    }

    /**
     * Checks if the block is technically full.
     * A block is considered technically full if there is very little space left for additional transactions.
     *
     * @return true if the block is technically full, false otherwise
     */
    public boolean isFull() {
        return this.technicallyFull;
    }

    /**
     * Returns the size of the block.
     * The size is the total size of all transactions in the block.
     *
     * @return the size of the block
     */
    public long size() {
        return size;
    }

    /**
     * Adds a transaction to the block.
     * This method updates the block's size and checks if the block is technically full.
     *
     * @param tx the transaction to be added
     * @return the list of transactions in the block
     * @throws ArithmeticException if the block size exceeds the maximum size defined in the consensus
     */
    public List<Transaction> add(Transaction tx) throws ArithmeticException {
        if (tx.getSize() + size() > distributedLedger.getConsensusStrategy().getBlockSize())
            throw new ArithmeticException("Block cannot be greater than the max size defined in the consensus");
        this.transactions.add(tx);
        this.size += tx.getSize();

        if (distributedLedger.getConsensusStrategy().getBlockSize() - size() < distributedLedger.getConsensusStrategy().getBlockSize() * 0.05) {
            this.technicallyFull = true; // when there is very little space left in the block, it should not try to find a transaction to fill this space
            // if the left space in the block is less than 5%, it is technically full
        }

        return getTransactions();
    }

    /**
     * Finalizes the block by setting its timestamp and recording metrics.
     * This method is called when the block is completed and ready to be added to the ledger.
     */
    public void finalizeBlock() {
        this.timestamp = Timed.getFireCount();
        SimulationMetrics.getInstance().markBlockCreated(this, timestamp);
        for (Transaction tx : transactions) {
            SimulationMetrics.getInstance().setTransactionConfirmationTime(tx, Timed.getFireCount());
            SimulationMetrics.getInstance().recordTransactionOnChain();
        }
    }

    /**
     * Returns the ID of the block.
     * The ID is a unique identifier for the block, typically generated as a hash.
     *
     * @return the ID of the block
     */
    public String getId() {
        return id;
    }
}