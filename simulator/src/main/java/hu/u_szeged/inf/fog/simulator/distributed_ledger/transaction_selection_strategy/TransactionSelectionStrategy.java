package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.transaction_selection_strategy;

import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.Mempool;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.Transaction;

public interface TransactionSelectionStrategy {
    Transaction selectTransaction(Mempool mempool);
}
