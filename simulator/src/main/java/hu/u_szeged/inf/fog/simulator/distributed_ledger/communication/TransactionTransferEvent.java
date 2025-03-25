package hu.u_szeged.inf.fog.simulator.distributed_ledger.communication;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.BlockValidator;

public class TransactionTransferEvent implements ResourceConsumption.ConsumptionEvent{
    private final TransactionMessage transactionMessage;
    private final BlockValidator sender;    // the node initiating to send
    private final BlockValidator receiver;  // the neighbor that should receive it

    public TransactionTransferEvent(TransactionMessage transactionMessage, BlockValidator sender, BlockValidator receiver) {
        this.transactionMessage = transactionMessage;
        this.sender = sender;
        this.receiver = receiver;
    }


    @Override
    public void conComplete() {
        receiver.receiveTransaction(transactionMessage);
    }

    @Override
    public void conCancelled(ResourceConsumption problematic) {
        System.err.println("[TransactionTransferEvent] Transfer cancelled. Could not deliver Tx from '"
                + sender.getName() + "' to '" + receiver.getName() + "'");
    }
}
