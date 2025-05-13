package hu.u_szeged.inf.fog.simulator.distributed_ledger.validation_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Block;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;
import java.util.Random;

/**
 * The RandomizedValidation class represents a validation strategy that accepts transactions and blocks with a certain probability.
 * This strategy is useful for simulating environments where validation is probabilistic.
 */
public class RandomizedValidation implements ValidationStrategy {

    private final Random rnd;
    private final double acceptanceProbTx;
    private final double acceptanceProbBlock;

    /**
     * Constructs a RandomizedValidation strategy with specified acceptance probabilities and a random number generator.
     *
     * @param acceptanceProbTx The probability of accepting a transaction.
     * @param acceptanceProbBlock The probability of accepting a block.
     * @param random The random number generator to use for probabilistic decisions.
     */
    public RandomizedValidation(double acceptanceProbTx, double acceptanceProbBlock, Random random) {
        this.rnd = random;
        this.acceptanceProbTx = acceptanceProbTx;
        this.acceptanceProbBlock = acceptanceProbBlock;
    }

    /**
     * Validates a transaction based on the acceptance probability.
     *
     * @param tx The transaction to validate.
     * @return {@code true} if the transaction is accepted, {@code false} otherwise.
     */
    @Override
    public boolean isValidTransaction(Transaction tx) {
        return rnd.nextDouble() < acceptanceProbTx; // e.g. accept 90% of transactions
    }

    /**
     * Validates a block based on the acceptance probability.
     *
     * @param block The block to validate.
     * @return {@code true} if the block is accepted, {@code false} otherwise.
     */
    @Override
    public boolean isValidBlock(Block block) {
        return rnd.nextDouble() < acceptanceProbBlock; // e.g. accept 70% of blocks
    }
}