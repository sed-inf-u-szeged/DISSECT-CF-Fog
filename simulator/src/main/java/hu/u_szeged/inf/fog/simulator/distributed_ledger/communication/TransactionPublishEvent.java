package hu.u_szeged.inf.fog.simulator.distributed_ledger.communication;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.TransactionDevice;

/**
 * TransactionPublishEvent class represents an event of publishing a transaction
 * from a transaction device to a miner.
 * It implements the ResourceConsumption.ConsumptionEvent interface.
 */
public class TransactionPublishEvent implements ResourceConsumption.ConsumptionEvent {

    /** The transaction message being published */
    TransactionMessage transactionMessage;

    /** The device that is sending the transaction */
    TransactionDevice senderDevice;

    /** The miner that is receiving the transaction */
    Miner receiverMiner;

    /**
     * Constructor for TransactionPublishEvent.
     *
     * @param transactionMessage the transaction message to be published
     * @param senderDevice the device sending the transaction
     * @param receiverMiner the miner receiving the transaction
     */
    public TransactionPublishEvent(TransactionMessage transactionMessage, TransactionDevice senderDevice, Miner receiverMiner) {
        this.transactionMessage = transactionMessage;
        this.senderDevice = senderDevice;
        this.receiverMiner = receiverMiner;
    }

    /**
     * Method called when the consumption event is completed.
     * It triggers the receiver miner to receive the transaction message and
     * deregisters the transaction message from the sender's repository.
     */
    @Override
    public void conComplete() {
        this.receiverMiner.receiveTransaction(transactionMessage);
        this.senderDevice.caRepository.deregisterObject(transactionMessage);
    }

    /**
     * Method called when the consumption event is cancelled.
     * It logs an error message indicating the transfer was cancelled.
     *
     * @param problematic the resource consumption that caused the cancellation
     */
    @Override
    public void conCancelled(ResourceConsumption problematic) {
        System.err.println("[TransactionPublishEvent] Transfer cancelled. '" + senderDevice.getName() + "' could not publish Tx: '" + transactionMessage.id + "' to '" + receiverMiner.getName() + "'");
    }
}