package hu.u_szeged.inf.fog.simulator.distributed_ledger.validator.selection;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;

import java.util.Map;
import java.util.SplittableRandom;

/**
 * Selects validators randomly, weighted by their stake.
 */
public class StakeWeightedSelectionStrategy implements ValidatorSelectionStrategy {

    private final long seed;

    /**
     * Creates a stake-weighted validator selection strategy.
     *
     * @param seed the seed used for deterministic validator selection
     */
    public StakeWeightedSelectionStrategy(long seed) {
        this.seed = seed;
    }

    /**
     * Selects a validator with probability proportional to its stake.
     *
     * @param validators the validators and their corresponding stake values
     * @param slot       the current slot
     * @return the selected validator
     */
    @Override
    public Miner selectValidator(Map<Miner, Long> validators, long slot) {
        if (validators.isEmpty()) {
            throw new IllegalArgumentException("No validators are registered");
        }

        long totalStake = validators.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();

        if (totalStake <= 0) {
            throw new IllegalArgumentException("Total stake must be positive");
        }

        SplittableRandom random = new SplittableRandom(seed + slot);
        double ticket = random.nextDouble() * totalStake;

        long cumulativeStake = 0;

        for (Map.Entry<Miner, Long> entry : validators.entrySet()) {
            cumulativeStake += entry.getValue();

            if (ticket < cumulativeStake) {
                return entry.getKey();
            }
        }

        throw new IllegalStateException("Validator selection failed");
    }
}