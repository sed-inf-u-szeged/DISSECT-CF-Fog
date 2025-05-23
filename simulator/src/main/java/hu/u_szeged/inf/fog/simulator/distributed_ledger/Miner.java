package hu.u_szeged.inf.fog.simulator.distributed_ledger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.BlockMessage;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.communication.TransactionMessage;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.ConsensusStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.*;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block.BuildBlockTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.block.ValidateBlockTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.chain.SyncChainTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.task.tx.ValidateTransactionTask;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.transaction_selection_strategy.TransactionSelectionStrategy;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.validation_strategy.ValidationStrategy;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import org.eclipse.collections.impl.bimap.mutable.HashBiMap;

import java.util.*;

/**
 * Represents a Miner node in the distributed ledger simulation.
 * <p>
 * A Miner is responsible for:
 * - Receiving and validating transactions and blocks.
 * - Building blocks from validated transactions in its local mempool.
 * - Running a sequence of simulation tasks.
 * - Interacting with a virtual machine.
 * </p>
 * <p>
 * Each Miner operates based on a discrete event simulation using {@link Timed#tick(long)},
 * and queues {@link MinerTask}s for execution. Tasks are pulled and executed when the miner
 * is in an IDLE state and its local VM is RUNNING.
 * </p>
 */
public class Miner extends Timed {
    public static HashBiMap<ComputingAppliance, Miner> miners = new HashBiMap<>();
    public final ComputingAppliance computingAppliance;
    public VirtualMachine localVm;
    public final String name;
    public final Mempool mempool;
    public final ConsensusStrategy consensusStrategy;
    public final TransactionSelectionStrategy transactionSelectionStrategy;
    private final ValidationStrategy validationStrategy;

    private MinerTask activeTask = null;
    public final Deque<MinerTask> tasksQueue = new ArrayDeque<>();
    private long lastActionTime = 0L;
    private static final long MAX_IDLE_TICKS = 50_000L; //this could be also configurable

    private Block nextBlock;
    private final LocalLedger localLedger;

    private final Set<Transaction> knownTransactions = new HashSet<>();
    private MinerState state = MinerState.OFF;

    /**
     * Constructs a new Miner with the specified parameters.
     *
     * @param consensusStrategy            The consensus strategy used by this miner.
     * @param transactionSelectionStrategy The strategy for selecting transactions.
     * @param computingAppliance           The computing appliance associated with this miner.
     * @param validationStrategy           The strategy for validating transactions.
     */
    public Miner(ConsensusStrategy consensusStrategy, TransactionSelectionStrategy transactionSelectionStrategy, ComputingAppliance computingAppliance, ValidationStrategy validationStrategy) {
        this.name = "[Miner]" + miners.size();
        this.setState(MinerState.WAITING_FOR_VM);
        miners.put(computingAppliance, this);
        this.mempool = new Mempool();
        this.consensusStrategy = consensusStrategy;
        this.transactionSelectionStrategy = transactionSelectionStrategy;
        this.validationStrategy = validationStrategy;
        this.computingAppliance = computingAppliance;
        startVm();
        this.localLedger = new LocalLedger(this);
        this.scheduleTask(new SyncChainTask());
        subscribe(1000);
    }

    /**
     * Starts the virtual machine associated with this miner.
     */
    public void startVm() {
        try {
            if (this.localVm == null) {
                /** VM "image" file with deploy time of 800 instructions **/
                VirtualAppliance va = new VirtualAppliance("va", 800, 0, false, 1_073_741_824L);
                Repository repo = this.computingAppliance.iaas.repositories.get(0);
                repo.registerObject(va);
                /** VM resource requirements **/
                AlterableResourceConstraints arc = new AlterableResourceConstraints(4, 0.001, 4_294_967_296L);

                this.localVm = this.computingAppliance.iaas.requestVM(va, arc, repo, 1)[0];
            }
        } catch (Exception e) {
            SimLogger.logError("Failed to start VM: " + e.getMessage());
            throw new RuntimeException("Failed to start VM: " + e.getMessage());
        }
    }

    /**
     * Stops the virtual machine associated with this miner.
     *
     * @throws VirtualMachine.StateChangeException
     */
    public void stopVm() throws VirtualMachine.StateChangeException {
        if (this.localVm != null && this.localVm.getState().equals(VirtualMachine.State.RUNNING)) {
            this.localVm.switchoff(true);
            EnergyDataCollector ec = EnergyDataCollector.getEnergyCollector(this.computingAppliance.iaas);
            if (ec != null) {
                ec.stop();
            }
        }
    }

    /**
     * @return The name of the miner.
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return The mempool of the miner.
     */
    public Mempool getMempool() {
        return mempool;
    }

    /**
     * @return The next block to be mined.
     */
    public Block getNextBlock() {
        return nextBlock;
    }

    /**
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
        return localVm.getState().equals(VirtualMachine.State.RUNNING);
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

    public boolean isBlockKnown(Block block) {
        boolean onChain = this.localLedger.getChain().contains(block);
        boolean inQueue = this.tasksQueue.stream().anyMatch(task -> task instanceof ValidateBlockTask && ((ValidateBlockTask) task).getBlock().equals(block));
        return onChain || inQueue;
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
     * Handles the main logic for the miner's behavior during each simulation tick.
     * <p>
     * This method is called periodically to simulate the passage of time and manage
     * the miner's tasks and state transitions. It performs the following actions:
     * </p>
     * <ul>
     *   <li>Checks if the virtual machine (VM) is running. If the VM is not running,
     *       the method exits early as the miner cannot perform any tasks.</li>
     *   <li>If the miner is in the {@code WAITING_FOR_VM} state and the VM is now running,
     *       the state is transitioned to {@code IDLE} to allow task execution.</li>
     *   <li>If the miner is idle and no block is currently being built, but there are
     *       transactions in the mempool, a new {@link BuildBlockTask} is scheduled to
     *       start building a block.</li>
     *   <li>If the miner is idle and a block is already being built, but it is not yet
     *       full and there are transactions in the mempool, a {@link BuildBlockTask} is
     *       scheduled to continue adding transactions to the block.</li>
     *   <li>If the miner is idle and there are tasks in the task queue, the next task
     *       is retrieved and executed if it is ready. If the task cannot be executed
     *       yet, it is re-added to the end of the queue.</li>
     * </ul>
     * <p>
     * This method ensures that the miner operates efficiently by prioritizing tasks
     * and managing its state transitions based on the current conditions.
     * </p>
     *
     * @param fires the current simulation tick count
     */
    @Override
    public void tick(long fires) {
        if (!isVmRunning()) {
            return;
        } else if (this.state == MinerState.WAITING_FOR_VM) {
            setState(MinerState.IDLE);
        }

        if (state == MinerState.IDLE && nextBlock == null && !mempool.isEmpty() && !isBuildBlockTaskQueued()) {
            scheduleTask(new BuildBlockTask(), true);
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

        if (state == MinerState.IDLE && tasksQueue.isEmpty() && nextBlock == null && mempool.isEmpty() && (Timed.getFireCount() - lastActionTime) > MAX_IDLE_TICKS) {
            setState(MinerState.OFF);
            try {
                SimLogger.logRun(this.name + " Idle for too long, shutting down");
                stopVm();
                unsubscribe();
            } catch (VirtualMachine.StateChangeException e) {
                SimLogger.logError(this.name + " Failed to stop VM: " + e.getMessage());
            }
        }
    }

    /**
     * Checks if a BuildBlockTask is already queued in the task queue.
     *
     * @return {@code true} if a BuildBlockTask is queued, {@code false} otherwise.
     */
    private boolean isBuildBlockTaskQueued() {
        return tasksQueue.stream().anyMatch(BuildBlockTask.class::isInstance);
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
     * Schedules a new task by adding it to the task queue with priority.
     *
     * @param task  The task to schedule.
     * @param force If true, the task is added to the front of the queue.
     */
    public void scheduleTask(MinerTask task, boolean force) {
        if (force) {
            tasksQueue.addFirst(task);
            SimLogger.logRun(name + " PRIORITY Task queued: " + task.describe());
        } else {
            scheduleTask(task);
        }
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
            this.lastActionTime = Timed.getFireCount();
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
            SimLogger.logRun(name + " received NEW block: " + block.getId());
            scheduleTask(new ValidateBlockTask(block), true);
        } else {
            SimLogger.logRun(name + " received block already known: " + block.getId());
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
            addKnownTransaction(tx);
            scheduleTask(new ValidateTransactionTask(tx));
        } else {
            SimLogger.logRun(name + " received transaction " + tx.getId() + " is known");
        }
    }

    /**
     * Removes a transaction from the mempool and the task queue.
     *
     * @param transaction The transaction to remove.
     */
    public void removeTransactionFromMempool(Transaction transaction) {
        mempool.transactions.removeIf(tx -> tx.equals(transaction));
    }

    /**
     * Removes a transaction from the task queue.
     * @param transaction The transaction to remove.
     */
    public void removeTransactionFromQueue(Transaction transaction) {
        tasksQueue.removeIf(task -> task instanceof ValidateTransactionTask && ((ValidateTransactionTask) task).getTx().equals(transaction));
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
     * The MinerState enum represents the various states a miner can be in.
     */
    public enum MinerState {
        OFF, IDLE, WAITING_FOR_VM, VALIDATING_TRANSACTION, BUILDING_BLOCK,
        CALCULATING_HEADER, MINING_NONCE, PROPAGATING_BLOCK, PROPAGATING_TRANSACTION,
        VALIDATING_INCOMING_BLOCK, RESOLVE_FORK, SYNC_CHAIN
    }
}
