package hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Mempool;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

import java.util.Comparator;

/**
 * The SizeStrategy class represents a transaction selection strategy based on the size of transactions.
 * This strategy selects transactions from the mempool based on their size, either in increasing or decreasing order.
 */
public class SizeStrategy implements TransactionSelectionStrategy {

    /**
     * The order in which transactions are selected based on their size.
     */
    Order order;

    /**
     * Constructs a new SizeStrategy with the specified order.
     *
     * @param order The order in which transactions are selected (INCREASE or DECREASE).
     */
    public SizeStrategy(Order order) {
        this.order = order;
    }

    /**
     * Constructs a new SizeStrategy with the default order (DECREASE).
     */
    public SizeStrategy() {
        this.order = Order.DECREASE;
    }

    /**
     * Selects a transaction from the mempool based on the size strategy.
     * If the order is INCREASE, the smallest transaction is selected.
     * If the order is DECREASE, the largest transaction is selected.
     *
     * @param mempool The mempool from which to select a transaction.
     * @return The selected transaction, or {@code null} if the mempool is empty.
     */
    @Override
    public Transaction selectTransaction(Mempool mempool) {
        if (mempool.isEmpty()) return null;

        Comparator<Transaction> sizeComparator = Comparator.comparingLong(Transaction::getSize);
        if (Order.INCREASE.equals(order)) {
            return mempool.transactions.stream()
                    .min(sizeComparator)
                    .orElse(null);
        } else {
            return mempool.transactions.stream()
                    .max(sizeComparator)
                    .orElse(null);
        }
    }

    /**
     * The Order enum represents the order in which transactions are selected based on their size.
     */
    public enum Order {
        INCREASE, DECREASE
    }
}