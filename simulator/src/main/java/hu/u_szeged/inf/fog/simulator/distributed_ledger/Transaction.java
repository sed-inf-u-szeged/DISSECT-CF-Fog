package hu.u_szeged.inf.fog.simulator.distributed_ledger;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * The Transaction class represents a transaction in the distributed ledger.
 * Each transaction has a unique ID, data, and size.
 */
public class Transaction {
    public static final List<Transaction> transactions = new ArrayList<>();
    private final String id;
    private final String data;
    private final long size;

    /**
     * Constructs a Transaction with the specified data and size.
     *
     * @param data the data of the transaction
     * @param size the size of the transaction
     */
    public Transaction(String data, long size) {
        this.id = Utils.generateFakeHash(0);
        this.data = data;
        this.size = size;
        transactions.add(this);
    }

    /**
     * Returns the data of the transaction.
     *
     * @return the data of the transaction
     */
    public String getData() {
        return data;
    }

    /**
     * Returns the size of the transaction.
     *
     * @return the size of the transaction
     */
    public long getSize() {
        return this.size;
    }

    /**
     * Returns the ID of the transaction.
     *
     * @return the ID of the transaction
     */
    public String getId() {
        return id;
    }

    /**
     * Returns a string representation of the transaction.
     *
     * @return a string representation of the transaction
     */
    @Override
    public String toString() {
        return "Transaction{" + "id='" + id + "', data='" + data + "'}";
    }
}
