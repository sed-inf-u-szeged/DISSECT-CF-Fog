package hu.u_szeged.inf.fog.simulator.distributed_ledger.communication;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.BlockValidator;

public class BlockTransferEvent implements ResourceConsumption.ConsumptionEvent {

    private final BlockMessage blockMessage;
    private final BlockValidator sender;    // the node initiating to send
    private final BlockValidator receiver;  // the neighbor that should receive it

    public BlockTransferEvent(BlockMessage msg, BlockValidator sender, BlockValidator receiver) {
        this.blockMessage = msg;
        this.sender = sender;
        this.receiver = receiver;
    }

    @Override
    public void conComplete() {
//        sender.deregisterObject(blockMessage); //shoud I deregister it? probably not as it should not delete it after the send
        receiver.receiveBlock(blockMessage.getBlock());
    }

    @Override
    public void conCancelled(ResourceConsumption problematic) {
        System.err.println("[BlockTransferEvent] Transfer cancelled. Could not deliver block from '"
                + sender.getName() + "' to '" + receiver.getName() + "'");
    }
}
