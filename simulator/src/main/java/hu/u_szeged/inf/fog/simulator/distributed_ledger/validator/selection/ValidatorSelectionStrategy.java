package hu.u_szeged.inf.fog.simulator.distributed_ledger.validator.selection;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;

import java.util.Map;

/**
 * Defines the strategy used to select a validator for block creation.
 */
public interface ValidatorSelectionStrategy {

    /**
     * Selects a validator from the given validator set.
     *
     * @param validators the validators and their corresponding stake values
     * @param slot       the current slot
     * @return the selected validator
     */
    Miner selectValidator(Map<Miner, Long> validators, long slot);
}