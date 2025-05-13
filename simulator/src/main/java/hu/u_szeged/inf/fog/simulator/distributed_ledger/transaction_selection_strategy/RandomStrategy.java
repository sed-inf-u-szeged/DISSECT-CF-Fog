package hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Mempool;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

import java.util.Random;

/**
 * The RandomStrategy class represents a transaction selection strategy based on random selection.
 * This strategy selects a transaction from the mempool at random.
 */
public class RandomStrategy implements TransactionSelectionStrategy {

    private static final Random RANDOM = SeedSyncer.centralRnd;

    /**
     * Selects a transaction from the mempool based on the random strategy.
     * A random transaction is selected from the mempool.
     *
     * @param mempool The mempool from which to select a transaction.
     * @return The selected transaction, or {@code null} if the mempool is empty.
     */
    @Override
    public Transaction selectTransaction(Mempool mempool) {
        if (mempool.isEmpty()) {
            return null;
        }
        int randomIndex = RANDOM.nextInt(mempool.size());
        return mempool.remove(randomIndex);
    }
}