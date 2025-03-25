package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.communication;

import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.Transaction;

public class TransactionMessage extends StorageObject {

    Transaction transaction;

    public TransactionMessage(Transaction transaction) {
        super(transaction.getId(), transaction.getSize() + 10, true);
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }

}
