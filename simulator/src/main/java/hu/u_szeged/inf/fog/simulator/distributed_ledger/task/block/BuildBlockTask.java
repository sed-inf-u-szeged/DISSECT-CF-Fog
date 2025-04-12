package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.BlockMessage;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.DifficultyAdjustmentStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.metrics.SimulationMetrics;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.CalculateHeaderTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.tx.TransactionValidationCallback;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.tx.ValidateTransactionTask;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

public class BuildBlockTask implements MinerTask, TransactionValidationCallback {

    private enum State {INIT, WAITING_FOR_VALIDATION, DONE}

    private State currentState = State.INIT;

    private Block buildingBlock = null;
    private Transaction currentTx = null;

    /**
     * Determines whether this BuildBlockTask is allowed to execute on the given miner at the current time.
     * <p>
     * This method returns {@code true} if:
     * <ol>
     *   <li>The task has not yet completed (i.e., {@code currentState != State.DONE}), and</li>
     *   <li>Either the miner does not currently have a next block assigned
     *       ({@code miner.getNextBlock() == null}), or the block this task is building
     *       is the same block the miner is tracking as its next block.</li>
     * </ol>
     *
     * @param miner The {@link Miner} instance to check for task eligibility.
     * @return {@code true} if the task can execute, {@code false} otherwise.
     */
    @Override
    public boolean canExecute(Miner miner) {
        return currentState != State.DONE && (buildingBlock == miner.getNextBlock() || miner.getNextBlock() == null);
    }

    /**
     * Executes one step in the block-building process for the given miner, based on this task's current state.
     * <p>
     * <ul>
     *   <li><strong>INIT:</strong> Creates a new {@link Block}, assigns it to the miner as the “next block,”
     *       and proceeds to pick and validate the first transaction.</li>
     *   <li><strong>WAITING_FOR_VALIDATION:</strong> No action taken; the task waits for a pending
     *       transaction validation to complete.</li>
     *   <li><strong>DONE:</strong> Marks the task as finished via {@code miner.finishTask(this)} to
     *       allow the miner to pick up other tasks.</li>
     * </ul>
     *
     * @param miner the {@link Miner} that owns and executes this task
     */
    @Override
    public void execute(Miner miner) {
        switch (currentState) {
            case INIT:
                miner.setState(Miner.MinerState.BUILDING_BLOCK);
                // create new block
                DifficultyAdjustmentStrategy das = ((DifficultyAdjustmentStrategy) miner.distributedLedger.getConsensusStrategy());
                long nextDiff = das.computeNextDifficulty(miner.getLocalLedger());
                buildingBlock = new Block(miner.distributedLedger, nextDiff);
                miner.setNextBlock(buildingBlock);
                pickAndValidateNextTx(miner);
                break;

            case WAITING_FOR_VALIDATION:
                // We do nothing here; we will be called again
                // after the validation task finishes.
                break;

            case DONE:
                // Shouldn't happen if canExecute() is correct, but let's be safe
                miner.finishTask(this);
                break;
        }
    }

    /**
     * Selects the next transaction to validate and, if the block is complete or no more transactions are
     * available, finalizes the block.
     * <p>
     * Specifically:
     * <ul>
     *   <li>If the current block is already full ({@code buildingBlock.isFull()}) or the miner's mempool
     *       is empty, this method logs that the block-building is done, sets the task state to
     *       {@code State.DONE}, registers the finished block in the miner's local repository (as a
     *       {@link BlockMessage}), and finishes this task.</li>
     *   <li>Otherwise, picks a candidate transaction from the mempool using the miner's
     *       {@code transactionSelectionStrategy}, schedules a {@link ValidateTransactionTask} to validate
     *       it, and transitions the task state to {@code WAITING_FOR_VALIDATION} until validation
     *       completes.</li>
     * </ul>
     *
     * @param miner The {@link Miner} orchestrating the block-building process.
     */
    private void pickAndValidateNextTx(Miner miner) {
        // If block is full or mempool is empty, we're done
        if (buildingBlock.isFull() || miner.getMempool().isEmpty()) {
            SimLogger.logRun("[BuildBlockTask] Done building block with " + buildingBlock.getTransactions().size() + " tx.");
            currentState = State.DONE;
            miner.getLocalLedger().addBlock(buildingBlock);
            miner.finishTask(this);
            miner.scheduleTask(new CalculateHeaderTask());
            return;
        }

        // pick a tx
        currentTx = miner.transactionSelectionStrategy.selectTransaction(miner.getMempool());
        SimLogger.logRun("[BuildBlockTask] Next candidate tx " + currentTx.getId());
        // schedule validate
        miner.scheduleTask(new ValidateTransactionTask(currentTx, this));
        // switch to waiting
        currentState = State.WAITING_FOR_VALIDATION;
    }

    /**
     * Invoked once the validation of the current candidate transaction completes. This method:
     * <ul>
     *   <li>Verifies the callback is for the same transaction the task is currently processing
     *       (by comparing {@code currentTx} to {@code tx}).</li>
     *   <li>If it is accepted after the validation, attempts to add the transaction to the building block (also removing it from
     *       the miner's mempool). If the transaction does not fit, it is skipped and logged.</li>
     *   <li>If rejected, removes the transaction from the miner's mempool.</li>
     *   <li>Resets the task state to {@code INIT}, finishes this task (making the miner idle),
     *       and, if the block is still not full and the mempool has more transactions, schedules
     *       a new {@link BuildBlockTask} to continue adding transactions.</li>
     *   <li>Finally, records the validation outcome via {@link SimulationMetrics#recordTransactionValidation}.</li>
     * </ul>
     *
     * @param miner    the {@link Miner} that performed the validation
     * @param tx       the transaction that was validated
     * @param accepted {@code true} if the transaction was accepted as valid, {@code false} otherwise
     */
    public void onValidationComplete(Miner miner, Transaction tx, boolean accepted) {
        // We only care if it matches the tx we are waiting on
        if (!tx.equals(currentTx)) {
            SimLogger.logError("[BuildBlockTask] Unexpected tx callback!");
            return;
        }
        if (accepted) {
            try {
                buildingBlock.add(tx);
                miner.getMempool().remove(tx);
            } catch (ArithmeticException e) {
                // block doesn't fit
                SimLogger.logError("[BuildBlockTask] Tx doesn't fit. Skipping.");
            }
        } else {
            SimLogger.logRun("[BuildBlockTask] Rejected transaction " + tx.getId());
            miner.getMempool().remove(tx);
        }
        // move on
        currentState = State.INIT;
        // By setting to INIT, the next time the miner tries to run this task and it should pickAndValidateNextTx again

        // "finish" the current BuildBlockTask so the Miner becomes IDLE,
        // and can pick something else
        miner.finishTask(this);

        // If there's still room and more transactions, schedule a new BuildBlockTask
        if (!buildingBlock.isFull() && !miner.getMempool().isEmpty()) {
            miner.scheduleTask(new BuildBlockTask());
        }

        SimulationMetrics.getInstance().recordTransactionValidation(miner, accepted);
    }

    @Override
    public String describe() {
        return "BuildBlockTask(state=" + currentState + ")";
    }
}
