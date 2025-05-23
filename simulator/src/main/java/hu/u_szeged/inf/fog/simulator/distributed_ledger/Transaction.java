package hu.u_szeged.inf.fog.simulator.distributed_ledger;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a transaction in the distributed ledger.
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
     * @return the data of the transaction
     */
    public String getData() {
        return data;
    }

    /**
     * @return the size of the transaction
     */
    public long getSize() {
        return this.size;
    }

    /**
     * @return the ID of the transaction
     */
    public String getId() {
        return id;
    }

    /**
     * @return a string representation of the transaction
     */
    @Override
    public String toString() {
        return "Transaction{" + "id='" + id + "', data='" + data + "'}";
    }
}
