package hu.u_szeged.inf.fog.simulator.distributed_ledger.communication;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.metrics.SimulationMetrics;

/**
 * TransactionTransferEvent class represents an event of transferring a transaction message
 * from a sender miner to a receiver miner.
 * It implements the ResourceConsumption.ConsumptionEvent interface.
 */
public class TransactionTransferEvent implements ResourceConsumption.ConsumptionEvent {

    private final TransactionMessage transactionMessage;
    private final Miner sender;
    private final Miner receiver;

    /**
     * Constructor for TransactionTransferEvent.
     *
     * @param transactionMessage the transaction message to be transferred
     * @param sender the miner sending the transaction message
     * @param receiver the miner receiving the transaction message
     */
    public TransactionTransferEvent(TransactionMessage transactionMessage, Miner sender, Miner receiver) {
        this.transactionMessage = transactionMessage;
        this.sender = sender;
        this.receiver = receiver;
    }

    /**
     * Method called when the consumption event is completed.
     * It triggers the receiver to receive the transaction message and records the event in the simulation metrics.
     */
    @Override
    public void conComplete() {
        receiver.receiveTransaction(transactionMessage);
        SimulationMetrics.getInstance().recordNetworkSend(sender, transactionMessage.size);
        SimulationMetrics.getInstance().markTransactionArrived(transactionMessage.getTransaction(), receiver, Timed.getFireCount());
    }

    /**
     * Method called when the consumption event is cancelled.
     * It logs an error message indicating the transfer was cancelled.
     *
     * @param problematic the resource consumption that caused the cancellation
     */
    @Override
    public void conCancelled(ResourceConsumption problematic) {
        System.err.println("[TransactionTransferEvent] Transfer cancelled. Could not deliver Tx from '" + sender.getName() + "' to '" + receiver.getName() + "'");
    }
}