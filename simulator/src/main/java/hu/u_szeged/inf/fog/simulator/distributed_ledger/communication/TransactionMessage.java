package hu.u_szeged.inf.fog.simulator.distributed_ledger.communication;

import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

/**
 * TransactionMessage class represents a message containing a transaction in the distributed ledger.
 * It extends the StorageObject class and implements the MessageInterface.
 */
public class TransactionMessage extends StorageObject implements MessageInterface {

    private final Transaction transaction;
    private static final int TRANSACTION_MESSAGE_OVERHEAD = 5;

    /**
     * Constructor for TransactionMessage.
     *
     * @param transaction the transaction to be included in the message
     */
    public TransactionMessage(Transaction transaction) {
        super(transaction.getId(), transaction.getSize() + TRANSACTION_MESSAGE_OVERHEAD, true);  // sum of the transaction size plus an extra 5 overhead
        this.transaction = transaction;
    }

    /**
     * Gets the transaction contained in the message.
     *
     * @return the transaction
     */
    public Transaction getTransaction() {
        return transaction;
    }

}