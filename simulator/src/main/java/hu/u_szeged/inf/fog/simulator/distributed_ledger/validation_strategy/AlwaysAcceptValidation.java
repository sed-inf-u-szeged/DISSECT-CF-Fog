package hu.u_szeged.inf.fog.simulator.distributed_ledger.validation_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

/**
 * Trivial strategy: Always accept everything as valid.
 * This strategy is used for purposes where all transactions and blocks are considered valid.
 */
public class AlwaysAcceptValidation implements ValidationStrategy {

    /**
     * Validates a transaction.
     *
     * @param tx The transaction to validate.
     * @return {@code true} always, indicating the transaction is valid.
     */
    @Override
    public boolean isValidTransaction(Transaction tx) {
        return true;
    }

    /**
     * Validates a block.
     *
     * @param block The block to validate.
     * @return {@code true} always, indicating the block is valid.
     */
    @Override
    public boolean isValidBlock(Block block) {
        return true;
    }
}