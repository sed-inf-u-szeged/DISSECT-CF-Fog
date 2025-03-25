package hu.u_szeged.inf.fog.simulator.distributed_ledger;

import java.util.LinkedList;

// Mempool to store transactions
public class Mempool {
    public final LinkedList<Transaction> transactions = new LinkedList<>();

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

    public Transaction removeFirst(){
        return transactions.removeFirst();
    }

    public Transaction removeLast(){
        return transactions.removeLast();
    }
}
