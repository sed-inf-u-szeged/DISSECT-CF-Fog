package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger;

import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class Transaction {
    public static final List<Transaction> transactions = new ArrayList<>();
    private final String id;
    private final String data;
    private long size;

    public Transaction(String data, long size) {
        this.id = Utils.generateHash(0);
        this.data = data;
        this.size = size;
        transactions.add(this);
    }


    public String getData() {
        return data;
    }

    public long getSize() {
        return this.size;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Transaction{" + "id='" + id + "', data='" + data + "'}";
    }
}
