package hu.u_szeged.inf.fog.simulator.distributed_ledger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.BlockMessage;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.TransactionMessage;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.*;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block.BuildBlockTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block.ValidateBlockTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.tx.PropagateTransactionTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.tx.TransactionValidationCallback;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.tx.ValidateTransactionTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy.TransactionSelectionStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validation_strategy.ValidationStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import org.eclipse.collections.impl.bimap.mutable.HashBiMap;

import java.util.*;

/**
 * Represents a Miner node in the distributed ledger simulation.
 * <p>
 * A Miner is responsible for:
 * - Receiving and validating transactions and blocks.
 * - Building blocks from validated transactions in its local mempool.
 * - Running a sequence of simulation tasks (e.g., building, validating, propagating).
 * - Interacting with a virtual machine and distributed ledger.
 * - Tracking known transactions to avoid redundant propagation.
 * </p>
 * <p>
 * Each Miner operates based on a discrete event simulation using {@link Timed#tick(long)},
 * and queues {@link MinerTask}s for execution. Tasks are pulled and executed when the miner
 * is in an IDLE state and its local VM is available.
 * </p>
 */
public class Miner extends Timed {
    private static final Random RANDOM = SeedSyncer.centralRnd;
    public static HashBiMap<ComputingAppliance, Miner> miners = new HashBiMap<>();
    public final ComputingAppliance computingAppliance;
    public final VirtualMachine localVm;
    public final String name;
    public final Mempool mempool;
    public final DistributedLedger distributedLedger;
    public final TransactionSelectionStrategy transactionSelectionStrategy;
    private final ValidationStrategy validationStrategy;

    private MinerTask activeTask = null;
    private final Deque<MinerTask> tasksQueue = new ArrayDeque<>();

    private Block nextBlock;
    private final LocalLedger localLedger;

    private final Set<Transaction> knownTransactions = new HashSet<>();
    private MinerState state = MinerState.OFF;

    /**
     * Constructs a new `Miner` with the specified parameters.
     *
     * @param distributedLedger            The distributed ledger instance.
     * @param transactionSelectionStrategy The strategy for selecting transactions.
     * @param computingAppliance           The computing appliance associated with this miner.
     * @param validationStrategy           The strategy for validating transactions.
     */
    public Miner(DistributedLedger distributedLedger, TransactionSelectionStrategy transactionSelectionStrategy, ComputingAppliance computingAppliance, ValidationStrategy validationStrategy) {
        this.setState(MinerState.WAITING_FOR_VM);
        this.name = "[Miner]" + miners.size();
        miners.put(computingAppliance, this);
        this.mempool = new Mempool();
        this.distributedLedger = distributedLedger;
        this.transactionSelectionStrategy = transactionSelectionStrategy;
        this.validationStrategy = validationStrategy;
        this.computingAppliance = computingAppliance;
        this.localVm = computingAppliance.broker.vm;
        this.localLedger = new LocalLedger(this);
        this.scheduleTask(new SyncChainTask());
        subscribe(1);
    }

    /**
     * Returns the name of the miner.
     *
     * @return The name of the miner.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the mempool of the miner.
     *
     * @return The mempool of the miner.
     */
    public Mempool getMempool() {
        return mempool;
    }

    /**
     * Returns the next block to be mined.
     *
     * @return The next block to be mined.
     */
    public Block getNextBlock() {
        return nextBlock;
    }

    /**
     * Returns the local repository of the miner.
     *
     * @return The local repository of the miner.
     * @throws IllegalStateException if no local repository is available.
     */
    public Repository getLocalRepo() {
        if (this.computingAppliance.iaas.repositories.isEmpty()) {
            throw new IllegalStateException("No local repository available");
        }
        return this.computingAppliance.iaas.repositories.get(0);
    }

    /**
     * Returns the local ledger of the miner.
     *
     * @return The local ledger of the miner.
     */
    public LocalLedger getLocalLedger() {
        return localLedger;
    }

    /**
     * Sets the next block to be mined.
     *
     * @param nextBlock The next block to be mined.
     */
    public void setNextBlock(Block nextBlock) {
        this.nextBlock = nextBlock;
    }

    /**
     * Checks if the virtual machine is running.
     *
     * @return {@code true} if the virtual machine is running, {@code false} otherwise.
     */
    public boolean isVmRunning() {
        return localVm.getState() == VirtualMachine.State.RUNNING;
    }

    /**
     * Checks if the transaction is known to the miner.
     *
     * @param tx The transaction to check.
     * @return {@code true} if the transaction is known, {@code false} otherwise.
     */
    public boolean isTxKnown(Transaction tx) {
        return this.knownTransactions.contains(tx);
    }

    /**
     * Returns the validation strategy of the miner.
     *
     * @return The validation strategy of the miner.
     */
    public ValidationStrategy getValidationStrategy() {
        return validationStrategy;
    }

    /**
     * Adds a known transaction to the miner.
     *
     * @param tx The transaction to add.
     */
    public void addKnownTransaction(Transaction tx) {
        this.knownTransactions.add(tx);
    }

    /**
     * The main simulation callback for this miner.
     *
     * @param fires The number of simulation ticks.
     */
    @Override
    public void tick(long fires) {
        if (!isVmRunning()) {
            return;
        }

        if (state == MinerState.IDLE && nextBlock == null && !mempool.isEmpty()) {
            scheduleTask(new BuildBlockTask());
        }

        if (state == MinerState.IDLE && !tasksQueue.isEmpty()) {
            MinerTask candidate = tasksQueue.removeFirst();
            if (candidate.canExecute(this)) {
                activeTask = candidate;
                SimLogger.logRun(name + " Starting task: " + candidate.describe());
                candidate.execute(this);
            } else {
                tasksQueue.addLast(candidate);
            }
        }
    }

    /**
     * Waits for the virtual machine to be in a running state.
     *
     * @return {@code true} if the virtual machine is not running, {@code false} otherwise.
     */
    public boolean waitForVm() {
        if (this.localVm == null) {
            return false;
        }
        return !isVmRunning();
    }

    /**
     * Schedules a new task by adding it to the task queue.
     *
     * @param task The task to schedule.
     */
    public void scheduleTask(MinerTask task) {
        tasksQueue.addLast(task);
        SimLogger.logRun(name + " Task queued: " + task.describe());
    }

    /**
     * Called by a task when it finishes, so the miner can free itself and pick up the next task from the queue.
     *
     * @param task The task that finished.
     */
    public void finishTask(MinerTask task) {
        if (this.activeTask == task) {
            SimLogger.logRun(name + " Finished: " + task.describe());
            this.activeTask = null;
            setState(MinerState.IDLE);
        }
    }

    /**
     * Receives a block message and schedules a block validation task if the block is new.
     *
     * @param blockMessage The block message to receive.
     */
    public void receiveBlock(BlockMessage blockMessage) {
        Block block = blockMessage.getBlock();
        if (!localLedger.getChain().contains(block)) {
            this.getLocalRepo().registerObject(blockMessage);
            SimLogger.logRun(name + " -> received NEW block. Scheduling validation.");
            scheduleTask(new ValidateBlockTask(block));
        } else {
            SimLogger.logRun(name + " -> block already known, ignoring.");
        }
    }

    /**
     * Receives a transaction message and schedules a transaction validation task if the transaction is new.
     *
     * @param transactionMessage The transaction message to receive.
     */
    public void receiveTransaction(TransactionMessage transactionMessage) {
        Transaction tx = transactionMessage.getTransaction();
        if (!isTxKnown(tx)) {
            this.getLocalRepo().registerObject(transactionMessage);
            SimLogger.logRun(name + " -> received NEW transaction " + tx.getId());
            addKnownTransaction(tx);
            scheduleTask(new ValidateTransactionTask(tx, new TransactionValidationCallback() {
                @Override
                public void onValidationComplete(Miner miner, Transaction tx, boolean accepted) {
                    if (accepted) {
                        mempool.addTransaction(tx);
                        scheduleTask(new PropagateTransactionTask(tx));
                    }
                }
            }));
        } else {
            SimLogger.logRun(name + " -> transaction " + tx.getId() + " is known, ignoring.");
        }
    }

    /**
     * Returns the current state of the miner.
     *
     * @return The current state of the miner.
     */
    public MinerState getState() {
        return state;
    }

    /**
     * Sets the state of the miner.
     *
     * @param newState The new state of the miner.
     */
    public void setState(MinerState newState) {
        SimLogger.logRun(name + " State changed: " + state + " -> " + newState);
        this.state = newState;
    }

    /**
     * The `MinerState` enum represents the various states a miner can be in.
     */
    public enum MinerState {
        OFF, IDLE, WAITING_FOR_VM, VALIDATING_TRANSACTION, BUILDING_BLOCK, CALCULATING_HEADER, MINING_NONCE, PROPAGATING_BLOCK, PROPAGATING_TRANSACTION, PROCESSING_INCOMING_BLOCK, RESOLVE_FORK
    }
}
