package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.tx;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

/**
 * The `TransactionValidationCallback` interface defines a callback for transaction validation.
 * Implementations of this interface are used to handle the result of a transaction validation process.
 */
public interface TransactionValidationCallback {
    /**
     * Called once the transaction validation completes.
     *
     * @param miner The {@link Miner} that performed the validation.
     * @param tx The {@link Transaction} that was validated.
     * @param accepted {@code true} if the transaction was considered valid, {@code false} otherwise.
     */
    void onValidationComplete(Miner miner, Transaction tx, boolean accepted);
}