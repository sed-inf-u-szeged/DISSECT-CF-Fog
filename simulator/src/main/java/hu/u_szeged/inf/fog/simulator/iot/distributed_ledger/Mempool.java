package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

// Mempool to store transactions
public class Mempool {
    private final List<Transaction> transactions = new LinkedList<>();

    public void addTransaction(Transaction tx) {
        transactions.add(tx);
        System.out.println("[Mempool] Transaction added: " + tx);
    }

    public Transaction get(int index) {
        return transactions.get(index);
    }

    public Transaction remove(int index){
        return transactions.remove(index);
    }

    public boolean remove(Transaction tx){
        return transactions.remove(tx);
    }

    public int size(){
        return transactions.size();
    }

    public boolean isEmpty() {
        return transactions.isEmpty();
    }
}
