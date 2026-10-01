package hu.u_szeged.inf.fog.simulator.distributed_ledger.validator;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores validators and their corresponding stake values.
 */
public class ValidatorRegistry {

    private final Map<Miner, Long> validators = new LinkedHashMap<>();

    /**
     * Registers a validator with the given stake.
     *
     * @param miner the validator to register
     * @param stake the stake assigned to the validator
     */
    public void register(Miner miner, long stake) {
        if (stake <= 0) {
            throw new IllegalArgumentException("Stake must be positive");
        }

        validators.put(miner, stake);
    }

    /**
     * Returns the stake of the given validator.
     *
     * @param miner the validator
     * @return the validator's stake, or zero if it is not registered
     */
    public long getStake(Miner miner) {
        return validators.getOrDefault(miner, 0L);
    }

    /**
     * Returns the registered validators and their stakes.
     *
     * @return an unmodifiable map of validators and stakes
     */
    public Map<Miner, Long> getValidators() {
        return Collections.unmodifiableMap(validators);
    }

    /**
     * Returns the total stake of all registered validators.
     *
     * @return the total stake
     */
    public long getTotalStake() {
        return validators.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }
}