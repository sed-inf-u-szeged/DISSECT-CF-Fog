package hu.u_szeged.inf.fog.simulator.distributed_ledger;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.consensus_strategy.ConsensusStrategy;

/**
 * The `DistributedLedger` class represents a distributed ledger that uses a consensus strategy.
 * It provides methods to access the consensus strategy used by the ledger.
 * It will be deprecated in the future, as there are no more use cases for it.
 */
public class DistributedLedger {
    public final ConsensusStrategy consensusStrategy;

    /**
     * Constructs a `DistributedLedger` with the specified consensus strategy.
     *
     * @param consensusStrategy the consensus strategy to be used by the ledger
     */
    public DistributedLedger(ConsensusStrategy consensusStrategy) {
        this.consensusStrategy = consensusStrategy;
    }

    /**
     * Returns the consensus strategy used by the ledger.
     *
     * @return the consensus strategy
     */
    public ConsensusStrategy getConsensusStrategy() {
        return consensusStrategy;
    }
}
