package hu.u_szeged.inf.fog.simulator.distributed_ledger.communication;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.metrics.SimulationMetrics;

/**
 * BlockTransferEvent class represents an event of transferring a block message
 * from a sender miner to a receiver miner.
 * It implements the ResourceConsumption.ConsumptionEvent interface.
 */
public class BlockTransferEvent implements ResourceConsumption.ConsumptionEvent {

    private final BlockMessage blockMessage;
    private final Miner sender;
    private final Miner receiver;

    /**
     * Constructor for BlockTransferEvent.
     *
     * @param msg the block message to be transferred
     * @param sender the miner sending the block message
     * @param receiver the miner receiving the block message
     */
    public BlockTransferEvent(BlockMessage msg, Miner sender, Miner receiver) {
        this.blockMessage = msg;
        this.sender = sender;
        this.receiver = receiver;
    }

    /**
     * Method called when the consumption event is completed.
     * It triggers the receiver to receive the block message and records the event in the simulation metrics.
     */
    @Override
    public void conComplete() {
        receiver.receiveBlock(blockMessage);
        SimulationMetrics.getInstance().recordNetworkSend(sender, blockMessage.size);
        SimulationMetrics.getInstance().markBlockArrived(blockMessage.getBlock(), receiver, Timed.getFireCount());
    }

    /**
     * Method called when the consumption event is cancelled.
     * It logs an error message indicating the transfer was cancelled.
     *
     * @param problematic the resource consumption that caused the cancellation
     */
    @Override
    public void conCancelled(ResourceConsumption problematic) {
        System.err.println("[BlockTransferEvent] Transfer cancelled. Could not deliver block from '" + sender.getName() + "' to '" + receiver.getName() + "'");
    }
}