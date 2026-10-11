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
 * Simulates the Proof of Work nonce search using a VM compute task.
 * The running computation can be cancelled when mining becomes obsolete.
 */
public class FindNonceTask implements MinerTask {

    private ResourceConsumption miningConsumption;

    /**
     * Checks whether a block is available for mining and its nonce
     * has not been found yet.
     *
     * @param miner the miner executing the task
     * @return true if mining can start
     */
    @Override
    public boolean canExecute(Miner miner) {
        return miner.getNextBlock() != null
                && !miner.getNextBlock().isNonceFound();
    }

    /**
     * Starts the simulated Proof of Work computation and stores
     * the resource consumption so it can be cancelled later.
     *
     * @param miner the miner executing the task
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
            miningConsumption = miner.localVm.newComputeTask(
                    Utils.instructionsPoW(
                            block.getDifficulty(),
                            miner.consensusStrategy.getDigestStrategy()
                    ),
                    ResourceConsumption.unlimitedProcessing,
                    new ConsumptionEventAdapter() {
                        @Override
                        public void conComplete() {
                            super.conComplete();
                            miningConsumption = null;

                            SimLogger.logRun(miner.name
                                    + " Nonce found with difficulty "
                                    + block.getDifficulty());

                            block.setNonceFound(true);
                            miner.getLocalLedger().addBlock(block);
                            block.finalizeBlock();

                            miner.finishTask(FindNonceTask.this);
                            miner.scheduleTask(
                                    new PropagateBlockTask(block), true
                            );
                        }

                        @Override
                        public void conCancelled(
                                ResourceConsumption problematic) {
                            super.conCancelled(problematic);
                            miningConsumption = null;

                            SimLogger.logRun(
                                    miner.name + " PoW mining cancelled."
                            );

                            miner.finishTask(FindNonceTask.this);
                        }
                    }
            );

            if (miningConsumption == null) {
                SimLogger.logError(
                        miner.name + " Could not start PoW mining."
                );
                miner.finishTask(this);
            }

        } catch (NetworkNode.NetworkException e) {
            SimLogger.logError(
                    miner.name + " PoW failed: " + e.getMessage()
            );
            miner.finishTask(this);
        }
    }

    /**
     * Cancels the active Proof of Work computation, if one exists.
     */
    public void cancelMining() {
        if (miningConsumption != null) {
            miningConsumption.cancel();
        }
    }

    @Override
    public String describe() {
        return "FindNonceTask";
    }
}
