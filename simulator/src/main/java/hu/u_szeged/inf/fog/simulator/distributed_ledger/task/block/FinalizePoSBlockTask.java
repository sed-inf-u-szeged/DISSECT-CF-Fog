package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;

/**
 * Finalizes a block created by a Proof of Stake validator.
 */
public class FinalizePoSBlockTask implements MinerTask {

    /**
     * Checks whether the miner has a block ready for finalization.
     *
     * @param miner the miner executing the task
     * @return true if a non-finalized block is available
     */
    @Override
    public boolean canExecute(Miner miner) {
        Block block = miner.getNextBlock();
        return block != null && !block.isFinalized();
    }

    /**
     * Finalizes the block, adds it to the local ledger and schedules its propagation.
     *
     * @param miner the miner executing the task
     */
    @Override
    public void execute(Miner miner) {
        Block block = miner.getNextBlock();

        if (block == null) {
            miner.finishTask(this);
            return;
        }

        block.finalizeBlock();
        miner.getLocalLedger().addBlock(block);

        miner.finishTask(this);
        miner.scheduleTask(new PropagateBlockTask(block), true);
    }

    /**
     * Provides a description of this task.
     *
     * @return the task description
     */
    @Override
    public String describe() {
        return "FinalizePoSBlockTask";
    }
}