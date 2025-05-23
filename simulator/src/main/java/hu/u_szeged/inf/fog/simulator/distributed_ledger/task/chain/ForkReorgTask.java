package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.chain;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.DifficultyAdjustmentStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.Utils;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.List;

/**
 * The ForkReorgTask class represents a task for handling fork reorganization in the blockchain.
 * This task is executed by a miner to roll back a specified number of blocks and resolve the fork.
 */
public class ForkReorgTask implements MinerTask {
    private final int rollbackDepth;

    public ForkReorgTask(int rollbackDepth) {
        this.rollbackDepth = rollbackDepth;
    }

    /**
     * Determines whether this ForkReorgTask can execute on the given miner.
     * The task can execute if the miner has a local ledger and the ledger size is greater than the rollback depth.
     *
     * @param miner The {@link Miner} instance to check for task eligibility.
     * @return {@code true} if the task can execute, {@code false} otherwise.
     */
    @Override
    public boolean canExecute(Miner miner) {
        return (miner.getLocalLedger() != null && miner.getLocalLedger().getChain().size() > rollbackDepth);
    }

    /**
     * Executes the fork reorganization process for the given miner.
     * This method initiates the rollback of blocks and resolves the fork.
     *
     * @param miner The {@link Miner} that owns and executes this task.
     */
    @Override
    public void execute(Miner miner) {
        miner.setState(Miner.MinerState.RESOLVE_FORK);
        SimLogger.logRun("[ForkReorgTask] " + miner.getName() + " rolling back " + rollbackDepth + " blocks...");

        DifficultyAdjustmentStrategy das = ((DifficultyAdjustmentStrategy) miner.consensusStrategy);
        long diff = das.computeNextDifficulty(miner.getLocalLedger());
        List<Block> deltaBlocks = Utils.generateFakeBlocks(miner.consensusStrategy, rollbackDepth, diff);
        try {
            miner.localVm.newComputeTask(Utils.reorgCost(miner.consensusStrategy, deltaBlocks), ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
                @Override
                public void conComplete() {
                    miner.getLocalLedger().resolveReorg(deltaBlocks);
                    SimLogger.logRun(miner.name + " Fork reorg completed at time: " + Timed.getFireCount());
                    miner.finishTask(ForkReorgTask.this);
                }
            });
        } catch (NetworkNode.NetworkException e) {
            SimLogger.logError(miner.name + " Fork reorg failed: " + e.getMessage());
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
        return "ForkReorgTask(rollbackDepth=" + rollbackDepth + ")";
    }
}