package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.communication;

import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.Block;

public class BlockMessage extends StorageObject {
    private final Block block;

    public BlockMessage(Block block) {
        super(block.getId(), block.size() + 50, true);  //TODO:sum of the transactions plus an extra 50 overhead? or simple the max size of a block?
        this.block = block;
    }

    public Block getBlock() {
        return block;
    }
}
