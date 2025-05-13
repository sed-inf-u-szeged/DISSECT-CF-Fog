package hu.u_szeged.inf.fog.simulator.distributed_ledger;

import java.util.LinkedList;

/**
 * The Mempool class represents a memory pool that stores transactions before they are added to a block.
 * It provides methods to add, retrieve, and remove transactions from the pool.
 */
public class Mempool {
    public final LinkedList<Transaction> transactions = new LinkedList<>();

    /**
     * Adds a transaction to the mempool if it is not already present.
     *
     * @param tx the transaction to add
     */
    public void addTransaction(Transaction tx) {
        if (transactions.contains(tx)) {
            return;
        }
        transactions.add(tx);
    }

    /**
     * Retrieves the transaction at the specified index.
     *
     * @param index the index of the transaction to retrieve
     * @return the transaction at the specified index
     */
    public Transaction get(int index) {
        return transactions.get(index);
    }

    /**
     * Removes the transaction at the specified index.
     *
     * @param index the index of the transaction to remove
     * @return the removed transaction
     */
    public Transaction remove(int index) {
        return transactions.remove(index);
    }

    /**
     * Removes the specified transaction from the mempool.
     *
     * @param tx the transaction to remove
     * @return {@code true} if the transaction was removed, {@code false} otherwise
     */
    public boolean remove(Transaction tx) {
        return transactions.remove(tx);
    }

    /**
     * Returns the number of transactions in the mempool.
     *
     * @return the number of transactions in the mempool
     */
    public int size() {
        return transactions.size();
    }

    /**
     * Checks if the mempool is empty.
     *
     * @return {@code true} if the mempool is empty, {@code false} otherwise
     */
    public boolean isEmpty() {
        return transactions.isEmpty();
    }

    /**
     * Removes and returns the first transaction in the mempool.
     *
     * @return the first transaction in the mempool
     */
    public Transaction removeFirst() {
        return transactions.removeFirst();
    }

    /**
     * Removes and returns the last transaction in the mempool.
     *
     * @return the last transaction in the mempool
     */
    public Transaction removeLast() {
        return transactions.removeLast();
    }
}