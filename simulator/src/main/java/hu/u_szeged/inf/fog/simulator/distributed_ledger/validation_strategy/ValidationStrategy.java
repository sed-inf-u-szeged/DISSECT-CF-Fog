package hu.u_szeged.inf.fog.simulator.distributed_ledger.validation_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

/**
 * Defines how a Miner decides if a transaction or block is valid.
 */
public interface ValidationStrategy {

    /**
     * Return true if the transaction is valid from this miner's perspective.
     */
    boolean isValidTransaction(Transaction tx);

    /**
     * Return true if the block is valid from this miner's perspective.
     */
    boolean isValidBlock(Block block);

}

