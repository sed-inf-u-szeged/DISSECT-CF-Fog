package hu.u_szeged.inf.fog.simulator.distributed_ledger.communication;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.TransactionDevice;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

/**
 * TransactionPublishEvent class represents an event of publishing a transaction from a transaction device to a miner.
 * It implements the ResourceConsumption.ConsumptionEvent interface.
 */
public class TransactionPublishEvent implements ResourceConsumption.ConsumptionEvent {

    /**
     * The transaction message being published
     * */
    TransactionMessage transactionMessage;

    /**
     * The device that is sending the transaction
     */
    TransactionDevice senderDevice;

    /**
     * The miner that is receiving the transaction
     */
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
        this.senderDevice.pendingTransactions.add(transactionMessage);
    }

    /**
     * Method called when the consumption event is completed.
     * It triggers the receiver miner to receive the transaction message and de-registers the transaction message from the sender's repository.
     */
    @Override
    public void conComplete() {
        SimLogger.logRun(this.receiverMiner.name + " received NEW published transaction '" + transactionMessage.getTransaction().getId() + "' from " + senderDevice.getName());
        this.receiverMiner.receiveTransaction(transactionMessage);
        this.senderDevice.localMachine.localDisk.deregisterObject(transactionMessage);
        this.senderDevice.pendingTransactions.remove(transactionMessage);
    }

    /**
     * Method called when the consumption event is cancelled.
     * It logs an error message indicating the transfer was cancelled.
     *
     * @param problematic the resource consumption that caused the cancellation
     */
    @Override
    public void conCancelled(ResourceConsumption problematic) {
        SimLogger.logError("[TransactionPublishEvent] Transfer cancelled. '" + senderDevice.getName() + "' could not publish Tx: '" + transactionMessage.id + "' to '" + receiverMiner.getName() + "'");
    }
}