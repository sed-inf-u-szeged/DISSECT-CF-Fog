package hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.crypto_strategy.CryptoStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.digest_strategy.DigestStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;

/**
 * The ConsensusStrategy interface defines the methods required for a consensus mechanism in a distributed ledger.
 * Implementing classes must provide the logic for retrieving the digest strategy, cryptographic strategy, and block size.
 */
public interface ConsensusStrategy {
    /**
     * Gets the digest strategy used in the consensus mechanism.
     *
     * @return the digest strategy
     */
    DigestStrategy getDigestStrategy();

    /**
     * Gets the cryptographic strategy used in the consensus mechanism.
     *
     * @return the cryptographic strategy
     */
    CryptoStrategy getCryptoStrategy();

    /**
     * Gets the block size used in the consensus mechanism.
     *
     * @return the block size
     */
    int getBlockSize();

    /**
     * Creates a new block according to the rules of the consensus mechanism.
     *
     * @param miner the miner creating the block
     * @return the newly created block
     */
    Block createBlock(Miner miner);

    /**
     * Creates the task responsible for finalizing a block according to the consensus mechanism.
     *
     * @return the block finalization task
     */
    MinerTask createBlockFinalizationTask();

    /**
     * Determines whether the given miner is allowed to create a block.
     *
     * @param miner the miner attempting to create a block
     * @return true if the miner is allowed to create a block
     */
    boolean canCreateBlock(Miner miner);

    /**
     * Checks whether the given block is valid according to the consensus mechanism.
     *
     * @param block the block to validate
     * @return true if the block is valid according to the consensus rules
     */
    boolean isConsensusValid(Block block);

    /**
     * Returns the computational cost of validating the consensus-specific
     * part of a block.
     *
     * @param block the block to validate
     * @return the consensus validation cost
     */
    double getConsensusValidationCost(Block block);
}