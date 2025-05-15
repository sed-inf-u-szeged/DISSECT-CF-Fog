package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.Utils;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

/**
 * The FindNonceTask class represents a task for finding a nonce in a block.
 * This task is executed by a miner to perform the proof-of-work computation.
 */
public class FindNonceTask implements MinerTask {

    /**
     * Determines whether this FindNonceTask can execute on the given miner.
     * The task can execute if the miner has a next block assigned and the nonce has not been found yet.
     *
     * @param miner The {@link Miner} instance to check for task eligibility.
     * @return {@code true} if the task can execute, {@code false} otherwise.
     */
    @Override
    public boolean canExecute(Miner miner) {
        return (miner.getNextBlock() != null && !miner.getNextBlock().isNonceFound());
    }

    /**
     * Executes the nonce-finding process for the given miner.
     * This method initiates the proof-of-work computation and handles the completion or failure of the task.
     *
     * @param miner The {@link Miner} that owns and executes this task.
     */
    @Override
    public void execute(Miner miner) {
        miner.setState(Miner.MinerState.MINING_NONCE);
        Block block = miner.getNextBlock();
        if (block == null) {
            miner.finishTask(this);
            return;
        }
        try {
            miner.localVm.newComputeTask(Utils.instructionsPoW(block.getDifficulty()), ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
                @Override
                public void conComplete() {
                    SimLogger.logRun(miner.name + " Nonce found with difficulty " + block.getDifficulty());
                    block.setNonceFound(true);
                    miner.getLocalLedger().addBlock(block);
                    block.finalizeBlock();
                    miner.finishTask(FindNonceTask.this);
                    miner.scheduleTask(new PropagateBlockTask(block), true);
                }
            });
        } catch (NetworkNode.NetworkException e) {
            SimLogger.logError(miner.name + " PoW failed: " + e.getMessage());
            miner.finishTask(this);
        }
    }

    /**
     * Provides a description of this task.
     *
     * @return A string describing the task.
     */
    @Override
    public String describe() {
        return "FindNonceTask";
    }
}