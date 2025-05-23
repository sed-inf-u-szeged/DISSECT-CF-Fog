package hu.u_szeged.inf.fog.simulator.distributed_ledger.task;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;

/**
 * The MinerTask interface defines the structure for tasks that can be executed by a miner.
 * Implementations of this interface represent specific tasks that a miner can perform.
 */
public interface MinerTask {
    /**
     * Checks if this task can be executed right now. This method should verify conditions.
     *
     * @param miner The {@link Miner} instance to check for task eligibility.
     * @return {@code true} if the task can be executed, {@code false} otherwise.
     */
    boolean canExecute(Miner miner);

    /**
     * Executes the logic of the task.
     *
     * @param miner The {@link Miner} that owns and executes this task.
     */
    void execute(Miner miner);

    /**
     * Provides a description of this task.
     * This method is used for logging and debugging purposes.
     *
     * @return A string describing the task.
     */
    String describe();
}