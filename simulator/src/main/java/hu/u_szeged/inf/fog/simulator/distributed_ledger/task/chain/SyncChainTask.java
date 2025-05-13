package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.chain;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

/**
 * The SyncChainTask class represents a task for synchronizing the blockchain ledger of a miner.
 * This task is executed by a miner to bootstrap its local ledger with the longest chain available from its peers.
 */
public class SyncChainTask implements MinerTask {

    /**
     * Determines whether this SyncChainTask can execute on the given miner.
     * The task can execute if the miner's local ledger is empty or if it hasn't already synced.
     *
     * @param miner The {@link Miner} instance to check for task eligibility.
     * @return {@code true} if the task can execute, {@code false} otherwise.
     */
    @Override
    public boolean canExecute(Miner miner) {
        return miner.getLocalLedger().size() == 0;
    }

    /**
     * Executes the chain synchronization process for the given miner.
     * This method initiates the synchronization by finding a peer with the longest chain and copying its ledger.
     *
     * @param miner The {@link Miner} that owns and executes this task.
     */
    @Override
    public void execute(Miner miner) {
        miner.setState(Miner.MinerState.SYNC_CHAIN);
        SimLogger.logRun(miner.getName() + " starting chain sync...");

        Miner peerWithChain = findPeerWithLongestChain(miner);

        if (peerWithChain != null) {
            miner.getLocalLedger().syncChainFrom(peerWithChain.getLocalLedger());
            SimLogger.logRun(miner.getName() + " chain sync completed. Local ledger size: " + miner.getLocalLedger().size());
        } else {
            SimLogger.logRun(miner.getName() + " did not find a suitable peer chain. Starting from scratch.");
        }

        miner.finishTask(this);
    }

    /**
     * Finds the peer with the longest blockchain ledger.
     * This method iterates over the miner's neighbors and selects the one with the largest local ledger.
     *
     * @param miner The {@link Miner} instance looking for a peer.
     * @return The {@link Miner} instance with the longest chain, or {@code null} if no suitable peer is found.
     */
    private Miner findPeerWithLongestChain(Miner miner) {
        Miner bestPeer = null;
        int bestSize = 0;
        for (Miner possiblePeer : Miner.miners.values()) {
            if (possiblePeer == miner) continue;
            int peerSize = possiblePeer.getLocalLedger().size();
            if (peerSize > bestSize) {
                bestSize = peerSize;
                bestPeer = possiblePeer;
            }
        }
        return bestPeer;
    }

    /**
     * Provides a description of this task.
     *
     * @return A string describing the task.
     */
    @Override
    public String describe() {
        return "SyncChainTask";
    }
}