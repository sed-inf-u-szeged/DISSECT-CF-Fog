package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger;

public class Transaction {
    private static int counter = 0;
    private final String id;
    private final String data;
    private long size;

    public Transaction(String data, long size) {
        this.id = "Tx-" + (counter++) + "-";
        this.data = data;
        this.size = size;
    }

    public static int getCounter() {
        return counter;
    }

    public String getData() {
        return data;
    }

    public long getSize() {
        return size;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Transaction{" + "id='" + id + "', data='" + data + "'}";
    }
}
