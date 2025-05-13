package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.BlockMessage;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.BlockTransferEvent;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

public class PropagateBlockTask implements MinerTask {

    private final Block block;

    public PropagateBlockTask(Block block) {
        this.block = block;
    }

    /**
     * Determines whether this PropagateBlockTask can execute on the given miner.
     * The task can execute if the block is not null and has a found nonce.
     * @param miner The {@link Miner} instance to check for task eligibility.
     * @return
     */
    @Override
    public boolean canExecute(Miner miner) {
        return block != null && block.isNonceFound();
    }

    /**
     * Executes the block propagation process for the given miner.
     * This method initiates the broadcasting of the block to the miner's neighbors.
     * @param miner The {@link Miner} that owns and executes this task.
     */
    @Override
    public void execute(Miner miner) {
        SimLogger.logRun(miner.name + " Propagating block with " + block.getTransactions().size() + " transactions...");
        miner.setState(Miner.MinerState.PROPAGATING_BLOCK);

        BlockMessage message = new BlockMessage(block);
        miner.getLocalRepo().registerObject(message);

        for (ComputingAppliance ca : miner.computingAppliance.neighbors) {
            Miner neighbor = Miner.miners.get(ca);
            if (neighbor == null) continue;

            if(neighbor.isBlockKnown(block)) {
                SimLogger.logRun(miner.name + " Block already known by " + neighbor.getName());
                continue;
            }

            BlockTransferEvent event = new BlockTransferEvent(message, miner, neighbor);
            try {
                NetworkNode.initTransfer(message.size, ResourceConsumption.unlimitedProcessing, miner.getLocalRepo(), neighbor.getLocalRepo(), event);
            } catch (NetworkNode.NetworkException e) {
                SimLogger.logError(miner.name + " Could not send block to " + neighbor.getName() + ": " + e.getMessage());
            }
        }
        if (block == miner.getNextBlock()) {
            miner.setNextBlock(null);
        }
        miner.finishTask(this);
    }

    /**
     * Provides a description of this task.
     * @return a string describing the task
     */
    @Override
    public String describe() {
        return "PropagateBlockTask";
    }
}


