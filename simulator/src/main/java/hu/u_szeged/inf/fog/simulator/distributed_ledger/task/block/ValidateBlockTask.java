package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.DifficultyAdjustmentStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.metrics.SimulationMetrics;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.utils.Utils;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

public class ValidateBlockTask implements MinerTask {

    private final Block block;

    public ValidateBlockTask(Block block) {
        this.block = block;
    }

    @Override
    public boolean canExecute(Miner miner) {
        return block != null;
    }

    @Override
    public void execute(Miner miner) {
        SimLogger.logRun(miner.name + " Validating received block: " + block.getId());
        miner.setState(Miner.MinerState.PROCESSING_INCOMING_BLOCK);

        double instructions = Utils.validateBlockCost(miner.distributedLedger.getConsensusStrategy(), block);

        long expectedDifficulty = ((DifficultyAdjustmentStrategy) miner.distributedLedger.getConsensusStrategy()).computeNextDifficulty(miner.getLocalLedger());

        if (block.getDifficulty() != expectedDifficulty) {
            SimLogger.logRun(miner.getName() + " -> REJECT: block has difficulty " + block.getDifficulty() + ", expected " + expectedDifficulty);
            SimulationMetrics.getInstance().recordBlockValidation(miner, false);
            // reject
            miner.finishTask(this);
            return;
        }

        try {
            miner.localVm.newComputeTask(instructions, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
                @Override
                public void conComplete() {
                    boolean accepted = miner.getValidationStrategy().isValidBlock(block);

                    if (accepted) {
                        SimLogger.logRun(miner.name + " VALID block: " + block.getId() + " at time: " + Timed.getFireCount());
                        miner.getLocalLedger().addBlock(block);

                        // Possibly propagate if we accept
                        miner.scheduleTask(new PropagateBlockTask(block));
                    } else {
                        SimLogger.logRun(miner.name + " INVALID block: " + block.getId() + " at time: " + Timed.getFireCount() + ". Discarding.");
                        // Do not add to local ledger, do not propagate
                    }

                    miner.finishTask(ValidateBlockTask.this);
                    SimulationMetrics.getInstance().recordBlockValidation(miner, accepted);
                }
            });
        } catch (NetworkNode.NetworkException e) {
            SimLogger.logError(miner.name + " Block validation failed: " + e.getMessage());
            miner.finishTask(this);
        }
    }

    @Override
    public String describe() {
        return "ValidateBlockTask(" + (block != null ? block.getId() : "null") + ")";
    }
}

