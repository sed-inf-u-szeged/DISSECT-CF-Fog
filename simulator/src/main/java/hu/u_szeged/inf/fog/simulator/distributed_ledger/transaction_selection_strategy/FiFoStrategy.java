package hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Mempool;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

public class FiFoStrategy implements TransactionSelectionStrategy{

    @Override
    public Transaction selectTransaction(Mempool mempool) {
        if (mempool.isEmpty()) {
            return null;
        }

        return mempool.removeFirst();
    }
}
