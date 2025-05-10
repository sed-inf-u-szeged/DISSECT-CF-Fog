package hu.u_szeged.inf.fog.simulator.distributed_ledger.communication;

import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;

/**
 * BlockMessage class represents a message containing a block from the distributed ledger.
 * It extends the StorageObject class and implements the MessageInterface.
 */
public class BlockMessage extends StorageObject implements MessageInterface {
    private final Block block;
    private static final int BLOCK_MESSAGE_OVERHEAD = 50;

    /**
     * Constructor for BlockMessage.
     *
     * @param block the block to be included in the message
     */
    public BlockMessage(Block block) {
        super(block.getId(), block.size() + BLOCK_MESSAGE_OVERHEAD, true);  // sum of the transactions plus an extra 50 overhead
        this.block = block;
    }

    /**
     * Gets the block contained in the message.
     *
     * @return the block
     */
    public Block getBlock() {
        return block;
    }
}