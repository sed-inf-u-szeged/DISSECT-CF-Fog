package hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.DifficultyAdjustmentStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.CalculateHeaderTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.MinerTask;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

public class BuildBlockTask implements MinerTask {
    private static final long MAX_EMPTY_MEMPOOL_TICKS = 50_000L;
    private long mempoolEmptySince = -1;

    private enum State {NEW, IN_PROGRESS, DONE}

    private State currentState = State.NEW;
    private Block buildingBlock;

    @Override
    public boolean canExecute(Miner miner) {
        return currentState != State.DONE && (buildingBlock == miner.getNextBlock() || miner.getNextBlock() == null);
    }

    @Override
    public void execute(Miner miner) {
        miner.setState(Miner.MinerState.BUILDING_BLOCK);
        if (currentState == State.NEW) {
            DifficultyAdjustmentStrategy das = ((DifficultyAdjustmentStrategy) miner.distributedLedger.getConsensusStrategy());
            long nextDiff = das.computeNextDifficulty(miner.getLocalLedger());
            buildingBlock = new Block(miner.distributedLedger, nextDiff);
            miner.setNextBlock(buildingBlock);
            currentState = State.IN_PROGRESS;
        }

        if (currentState == State.IN_PROGRESS) {
            SimLogger.logRun(miner.getName() + " [BuildBlockTask] Building block with " + buildingBlock.getTransactions().size() + " tx.");
            addNextTransactionToBlock(miner);
        }
    }

    private void addNextTransactionToBlock(Miner miner) {
        if (buildingBlock.isFull()) {
            currentState = State.DONE;
            SimLogger.logRun(miner.getName() + " [BuildBlockTask] Done building block with " + buildingBlock.getTransactions().size() + " tx.");
            miner.getLocalLedger().addBlock(buildingBlock);
            miner.scheduleTask(new CalculateHeaderTask());
            miner.finishTask(this);
            return;
        }

        if (miner.getMempool().isEmpty()) {
            SimLogger.logRun(miner.getName() + " [BuildBlockTask] Mempool is empty, cannot add more transactions.");

            if (mempoolEmptySince == -1) {
                mempoolEmptySince = Timed.getFireCount();
            }
            if (mempoolEmptySince + MAX_EMPTY_MEMPOOL_TICKS < Timed.getFireCount()) {
                SimLogger.logRun(miner.getName() + " [BuildBlockTask] Mempool has been empty for too long, finishing task.");
                currentState = State.DONE;
                miner.getLocalLedger().addBlock(buildingBlock);
                miner.scheduleTask(new CalculateHeaderTask());
            }else{
                miner.scheduleTask(this);
            }
            miner.finishTask(this);
            return;
        }
        mempoolEmptySince = -1;

        Transaction tx = miner.transactionSelectionStrategy.selectTransaction(miner.getMempool());
        miner.getMempool().remove(tx);

        try {
            buildingBlock.add(tx);
            SimLogger.logRun(miner.getName() + " [BuildBlockTask] Added tx " + tx.getId() + " to block.");
        } catch (ArithmeticException e) {
            SimLogger.logError(miner.getName() + " [BuildBlockTask] Tx doesn't fit. Re-inserting into mempool: " + tx.getId());
            miner.getMempool().transactions.addLast(tx); //add last or add first?
        }

        // Reschedule this task for the next tick to continue
        miner.scheduleTask(this);
        miner.finishTask(this);
    }


    @Override
    public String describe() {
        return "BuildBlockTask(state=" + currentState + ")";
    }
}
