package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger;

import java.util.LinkedList;
import java.util.Queue;

// Mempool to store transactions
public class Mempool {
    private final Queue<Transaction> transactions = new LinkedList<>();

    public synchronized void addTransaction(Transaction tx) {
        transactions.add(tx);
        System.out.println("[Mempool] Transaction added: " + tx);
    }

    public synchronized Transaction fetchTransaction() {
        return transactions.poll();
    }

    public synchronized boolean isEmpty() {
        return transactions.isEmpty();
    }
}
