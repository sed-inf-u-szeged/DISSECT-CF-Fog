package hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Mempool;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

/**
 * The FiFoStrategy class represents a transaction selection strategy based on the First-In-First-Out (FIFO) principle.
 * This strategy selects transactions from the mempool in the order of their arrival.
 */
public class FiFoStrategy implements TransactionSelectionStrategy {

    /**
     * Selects a transaction from the mempool based on the FIFO strategy.
     * The first transaction added to the mempool is selected first.
     *
     * @param mempool The mempool from which to select a transaction.
     * @return The selected transaction, or {@code null} if the mempool is empty.
     */
    @Override
    public Transaction selectTransaction(Mempool mempool) {
        if (mempool.isEmpty()) {
            return null;
        }
        return mempool.removeFirst();
    }
}