package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.transaction_selection_strategy;

import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.Mempool;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.Transaction;

import java.util.Comparator;

public class SizeStrategy implements TransactionSelectionStrategy {

    Order order;

    public SizeStrategy(Order order) {
        this.order = order;
    }

    public SizeStrategy() {
        this.order = Order.DECREASE;
    }

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
                    .max(sizeComparator.reversed())
                    .orElse(null);
        }
    }

    enum Order {
        INCREASE, DECREASE
    }
}

