package hu.u_szeged.inf.fog.simulator.distributed_ledger.find_node_strategy;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;

/**
 * The FindNodeStrategy interface defines a method for finding a miner node.
 */
public interface FindNodeStrategy {

    /**
     * Finds a miner node based on the implemented strategy.
     *
     * @return the selected miner node
     */
    Miner findNode();
}