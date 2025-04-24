package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.tx;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

/**
 * The `ValidateTransactionTask` class represents a task for validating a transaction.
 * This task is executed by a miner to verify the validity of a transaction using the consensus strategy's crypto strategy.
 */
public class ValidateTransactionTask implements MinerTask {
    private final Transaction tx;
    private final TransactionValidationCallback callback;

    /**
     * Constructs a new `ValidateTransactionTask` with the specified transaction and callback.
     *
     * @param tx       the transaction to validate
     * @param callback the callback to handle the result of the validation
     */
    public ValidateTransactionTask(Transaction tx, TransactionValidationCallback callback) {
        this.tx = tx;
        this.callback = callback;
    }

    /**
     * Determines whether this `ValidateTransactionTask` can execute on the given miner.
     * The task can execute if the transaction is not null.
     *
     * @param miner The {@link Miner} instance to check for task eligibility.
     * @return {@code true} if the task can execute, {@code false} otherwise.
     */
    @Override
    public boolean canExecute(Miner miner) {
        return tx != null;
    }

    /**
     * Executes the transaction validation process for the given miner.
     * This method initiates the validation of the transaction and handles the completion or failure of the task.
     *
     * @param miner The {@link Miner} that owns and executes this task.
     */
    @Override
    public void execute(Miner miner) {
        miner.setState(Miner.MinerState.VALIDATING_TRANSACTION);
        SimLogger.logRun("[ValidateTransactionTask] validating " + tx.getId());
        double instructions = miner.distributedLedger.getConsensusStrategy().getCryptoStrategy().verify()*100;//TODO
        try {
            miner.localVm.newComputeTask(instructions, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {
                @Override
                public void conComplete() {
                    boolean accepted = miner.getValidationStrategy().isValidTransaction(tx);
                    if (callback != null) {
                        callback.onValidationComplete(miner, tx, accepted);
                    }
                    miner.finishTask(ValidateTransactionTask.this);
                }
            });
        } catch (NetworkNode.NetworkException e) {
            SimLogger.logError("[ValidateTransactionTask] " + e.getMessage());
            if (callback != null) {
                callback.onValidationComplete(miner, tx, false);
            }
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
        return "ValidateTransactionTask(" + tx.getId() + ")";
    }
}