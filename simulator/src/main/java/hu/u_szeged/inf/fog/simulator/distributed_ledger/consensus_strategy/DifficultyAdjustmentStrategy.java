package hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.LocalLedger;

/**
 * The `DifficultyAdjustmentStrategy` interface defines a method for computing the difficulty
 * for the next block to be mined, based on the local chain, the block height, etc.
 */
public interface DifficultyAdjustmentStrategy {
    /**
     * Computes the difficulty for the next block to be mined,
     * based on the local chain, the block height, etc.
     *
     * @param ledger the local ledger containing the chain of blocks
     * @return the computed difficulty for the next block
     */
    long computeNextDifficulty(LocalLedger ledger);
}