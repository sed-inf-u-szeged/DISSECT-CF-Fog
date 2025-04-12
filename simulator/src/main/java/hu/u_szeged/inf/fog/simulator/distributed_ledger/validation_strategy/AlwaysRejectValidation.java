package hu.u_szeged.inf.fog.simulator.distributed_ledger.validation_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

/**
 * "Malicious" strategy: Always reject everything.
 * This strategy is used for purposes where all transactions and blocks are considered invalid.
 */
public class AlwaysRejectValidation implements ValidationStrategy {

    /**
     * Validates a transaction.
     *
     * @param tx The transaction to validate.
     * @return {@code false} always, indicating the transaction is invalid.
     */
    @Override
    public boolean isValidTransaction(Transaction tx) {
        return false;
    }

    /**
     * Validates a block.
     *
     * @param block The block to validate.
     * @return {@code false} always, indicating the block is invalid.
     */
    @Override
    public boolean isValidBlock(Block block) {
        return false;
    }
}