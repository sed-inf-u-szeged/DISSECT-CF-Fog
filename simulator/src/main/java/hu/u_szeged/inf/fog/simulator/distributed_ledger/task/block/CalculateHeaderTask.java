package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.Utils;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

/**
 * The CalculateHeaderTask class represents a task for calculating the header of a block.
 * This task is executed by a miner to compute the Merkle root and prepare the block header.
 */
public class CalculateHeaderTask implements MinerTask {

    /**
     * Determines whether this CalculateHeaderTask can execute on the given miner.
     * The task can execute if the miner has a next block assigned and the block is full.
     *
     * @param miner The {@link Miner} instance to check for task eligibility.
     * @return {@code true} if the task can execute, {@code false} otherwise.
     */
    @Override
    public boolean canExecute(Miner miner) {
        Block block = miner.getNextBlock();
        return (block != null && block.isFull());
    }

    /**
     * Executes the header calculation process for the given miner.
     * This method initiates the computation of the Merkle root.
     *
     * @param miner The {@link Miner} that owns and executes this task.
     */
    @Override
    public void execute(Miner miner) {
        miner.setState(Miner.MinerState.CALCULATING_HEADER);

        Block block = miner.getNextBlock();
        SimLogger.logRun(miner.name + " Calculating header for block with " + block.getTransactions().size() + " tx.");
        double instructions = Utils.merkleRoot(block, miner.consensusStrategy.getDigestStrategy());

        try {
            miner.localVm.newComputeTask(instructions, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
                @Override
                public void conComplete() {
                    miner.finishTask(CalculateHeaderTask.this);
                    miner.scheduleTask(new FindNonceTask());
                }
            });
        } catch (NetworkNode.NetworkException e) {
            SimLogger.logError(miner.name + " Header calc failed: " + e.getMessage());
            miner.finishTask(this);
        }
    }

    /**
     * Provides a description of this task.
     * @return a string describing the task
     */
    @Override
    public String describe() {
        return "CalculateHeaderTask";
    }
}