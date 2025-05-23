package hu.u_szeged.inf.fog.simulator.distributed_ledger;

import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.BlockMessage;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.MessageInterface;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * LocalLedger class represents a local copy of the "blockchain" for a miner.
 * It contains methods to add blocks, resolve reorganization of the chain, and synchronize the chain with another local ledger.
 */
public class LocalLedger {
    private final List<Block> chain;
    private final Miner owner;
    private final Repository repo;

    public LocalLedger(Miner owner) {
        this.chain = new ArrayList<>();
        this.owner = owner;
        this.repo = owner.getLocalRepo();
    }

    /**
     * Returns size of the local ledger.
     * @return the number of blocks in the chain
     */
    public int size() {
        return chain.size();
    }

    /**
     * Returns the chin of blocks.
     * @return the list of blocks in the chain
     */
    public List<Block> getChain() {
        return chain;
    }

    /**
     * Adds a block to the local ledger.
     * @param block the block to be added
     */
    public void addBlock(Block block) {
        BlockMessage blockMessage = new BlockMessage(block);
        this.owner.getLocalRepo().registerObject(blockMessage);
        this.chain.add(block);
        if(size() > 1) {
            long previousDifficulty = chain.get(size() - 2).getDifficulty();
            long currentDifficulty = chain.get(size() - 1).getDifficulty();
            if (previousDifficulty != currentDifficulty) {
                SimLogger.logRun(this.owner.getName() + " [LocalLedger] Difficulty changed from " + previousDifficulty + " to " + currentDifficulty);
            }
        }
    }

    /**
     * Reorganizes the local ledger by replacing the last blocks with the given fork blocks.
     * @param forkBlocks the list of blocks to replace the last blocks in the chain
     */
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

    /**
     * Replaces a block in the repository with a new block.
     * @param oldBlock the block to be replaced
     * @param newBlock the new block to be added
     */
    private void replaceBlockInRepo(Block oldBlock, Block newBlock) {
        BlockMessage blockMessageOld = new BlockMessage(oldBlock);
        repo.deregisterObject(blockMessageOld);

        BlockMessage blockMessageNew = new BlockMessage(newBlock);
        repo.registerObject(blockMessageNew);
    }

    /**
     * Synchronizes the local ledger with another local ledger.
     * @param otherLedger the other local ledger to synchronize from
     */
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
