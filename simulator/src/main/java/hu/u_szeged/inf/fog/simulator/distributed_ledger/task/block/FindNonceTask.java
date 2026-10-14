package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

/**
 * Simulates Proof of Work mining in successive batches of nonce attempts.
 * Each batch consumes CPU resources according to the configured digest strategy.
 * The simulation models the computational cost statistically without
 * calculating actual nonce values or cryptographic hashes.
 */
public class FindNonceTask implements MinerTask {

    private static final long ATTEMPTS_PER_BATCH = 100_000L;

    private ResourceConsumption miningConsumption;
    private long completedAttempts;
    private boolean cancelled;

    /**
     * Checks whether the miner has a block whose nonce has not been found.
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
     * Initializes the nonce search and starts the first batch.
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

        if (block.getDifficulty() < 0 || block.getDifficulty() > 64) {
            throw new IllegalArgumentException("Invalid PoW difficulty");
        }

        completedAttempts = 0L;
        cancelled = false;

        startNextBatch(miner, block);
    }

    /**
     * Simulates the next batch of nonce attempts.
     * The number of attempts until success follows a geometric distribution.
     * If the sampled number exceeds the batch size, another batch is scheduled.
     *
     * @param miner the miner performing the computation
     * @param block the block being mined
     */
    private void startNextBatch(Miner miner, Block block) {
        if (cancelled) {
            return;
        }

        double probability = Math.pow(16.0, -block.getDifficulty());

        double sampledAttempts = Math.floor(
                Math.log1p(-SeedSyncer.centralRnd.nextDouble()) /
                        Math.log1p(-probability)
        ) + 1.0;

        boolean successInBatch =
                sampledAttempts <= ATTEMPTS_PER_BATCH;

        long attemptsInBatch = successInBatch
                ? (long) sampledAttempts
                : ATTEMPTS_PER_BATCH;

        double work = attemptsInBatch
                * miner.consensusStrategy
                .getDigestStrategy().hash(80L);

        try {
            miningConsumption = miner.localVm.newComputeTask(
                    work,
                    ResourceConsumption.unlimitedProcessing,
                    new ConsumptionEventAdapter() {

                        @Override
                        public void conComplete() {
                            super.conComplete();

                            miningConsumption = null;
                            completedAttempts += attemptsInBatch;

                            if (successInBatch) {
                                SimLogger.logRun(miner.name
                                        + " Nonce found with difficulty "
                                        + block.getDifficulty()
                                        + " after " + completedAttempts
                                        + " attempts");

                                block.setNonceFound(true);
                                miner.getLocalLedger().addBlock(block);
                                block.finalizeBlock();

                                miner.finishTask(FindNonceTask.this);
                                miner.scheduleTask(
                                        new PropagateBlockTask(block), true
                                );
                            } else {
                                SimLogger.logRun(miner.name
                                        + " PoW progress: "
                                        + completedAttempts
                                        + " nonce attempts completed");

                                startNextBatch(miner, block);
                            }
                        }

                        @Override
                        public void conCancelled(
                                ResourceConsumption problematic) {
                            super.conCancelled(problematic);

                            miningConsumption = null;
                            cancelled = true;

                            SimLogger.logRun(miner.name
                                    + " PoW mining cancelled after "
                                    + completedAttempts
                                    + " completed attempts.");

                            miner.finishTask(FindNonceTask.this);
                        }
                    }
            );

            if (miningConsumption == null) {
                SimLogger.logError(
                        miner.name + " Could not start PoW mining batch."
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
     * Cancels the currently active batch of nonce attempts.
     */
    public void cancelMining() {
        cancelled = true;

        if (miningConsumption != null) {
            miningConsumption.cancel();
        }
    }

    @Override
    public String describe() {
        return "FindNonceTask";
    }
}
