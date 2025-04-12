package hu.u_szeged.inf.fog.simulator.distributed_ledger;

import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.BlockMessage;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.MessageInterface;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.ArrayList;
import java.util.List;

public class LocalLedger {
    private final List<Block> chain;
    private final Miner owner;
    private final Repository repo;

    public LocalLedger(Miner owner) {
        this.chain = new ArrayList<>();
        this.owner = owner;
        this.repo = owner.getLocalRepo();
    }

    public int size() {
        return chain.size();
    }

    public List<Block> getChain() {
        return chain;
    }

    public void addBlock(Block block) {
        BlockMessage blockMessage = new BlockMessage(block);
        this.owner.getLocalRepo().registerObject(blockMessage);
        this.chain.add(block);
    }

    public void resolveReorg(List<Block> forkBlocks) {
        SimLogger.logRun(this.owner.getName() + " [LocalLedger] Resolving reorg with " + forkBlocks.size() + " blocks.");

        int rollbackDepth = forkBlocks.size();
        int originalSize = chain.size();

        for (int i = 0; i < rollbackDepth; i++) {
            int targetIndex = originalSize - rollbackDepth + i;
            replaceBlockInRepo(chain.get(targetIndex), forkBlocks.get(i));
            chain.set(targetIndex, forkBlocks.get(i));
        }
    }

    private void replaceBlockInRepo(Block oldBlock, Block newBlock) {
        BlockMessage blockMessageOld = new BlockMessage(oldBlock);
        repo.deregisterObject(blockMessageOld);

        BlockMessage blockMessageNew = new BlockMessage(newBlock);
        repo.registerObject(blockMessageNew);
    }

    public void syncChainFrom(LocalLedger otherLedger) {
        SimLogger.logRun("Syncing chain from " + otherLedger.owner.getName() + " to " + this.owner.getName());
        if (otherLedger.size() == 0) {
            return;
        }
        repo.contents().forEach(so -> {
            if (so instanceof MessageInterface) {
                repo.deregisterObject(so);
            }
        });
        this.chain.clear();
        for (Block block : otherLedger.getChain()) {
            addBlock(block);
        }
    }
}
