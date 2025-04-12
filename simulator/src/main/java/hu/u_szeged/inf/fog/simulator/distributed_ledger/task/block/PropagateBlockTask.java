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

    @Override
    public boolean canExecute(Miner miner) {
        // the block should have a found nonce
        return block != null && block.isNonceFound();
    }

    @Override
    public void execute(Miner miner) {
        SimLogger.logRun(miner.name + " Propagating block with " + block.getTransactions().size() + " transactions...");
        miner.setState(Miner.MinerState.PROPAGATING_BLOCK);

        BlockMessage message = new BlockMessage(block);
        miner.getLocalRepo().registerObject(message);

        // Gossip to neighbors
        for (ComputingAppliance ca : miner.computingAppliance.neighbors) {
            Miner neighbor = Miner.miners.get(ca);
            if (neighbor == null) continue;

            BlockTransferEvent event = new BlockTransferEvent(message, miner, neighbor);
            try {
                NetworkNode.initTransfer(message.size, ResourceConsumption.unlimitedProcessing, miner.getLocalRepo(), neighbor.getLocalRepo(), event);
            } catch (NetworkNode.NetworkException e) {
                SimLogger.logError(miner.name + " Could not send block to " + neighbor.getName() + ": " + e.getMessage());
            }
        }

        // Clear the miner's nextBlock
        miner.setNextBlock(null);
        // Done
        miner.finishTask(this);
    }

    @Override
    public String describe() {
        return "PropagateBlockTask";
    }
}


