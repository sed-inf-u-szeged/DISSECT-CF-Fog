package hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Mempool;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

import java.util.Random;

public class RandomStrategy implements TransactionSelectionStrategy{

    private static final Random RANDOM = SeedSyncer.centralRnd;

    @Override
    public Transaction selectTransaction(Mempool mempool) {
        if (mempool.isEmpty()) {
            return null;
        }

        int randomIndex = RANDOM.nextInt(mempool.size());
        return mempool.remove(randomIndex);
    }
}
