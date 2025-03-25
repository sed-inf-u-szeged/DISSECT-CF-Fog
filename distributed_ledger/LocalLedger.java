package hu.u_szeged.inf.fog.simulator.iot.distributed_ledger;

import java.util.ArrayList;
import java.util.List;

public class LocalLedger {
    private List<Block> blocks;

    public LocalLedger() {
        this.blocks = new ArrayList<>();
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    public void addBlock(Block block) {
        this.blocks.add(block);
    }
}
