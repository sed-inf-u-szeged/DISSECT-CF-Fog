package hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Mempool;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

/**
 * Interface for transaction selection strategies in a distributed ledger system.
 * This interface defines the method to select a transaction from the mempool.
 */
public interface TransactionSelectionStrategy {
    Transaction selectTransaction(Mempool mempool);
}
