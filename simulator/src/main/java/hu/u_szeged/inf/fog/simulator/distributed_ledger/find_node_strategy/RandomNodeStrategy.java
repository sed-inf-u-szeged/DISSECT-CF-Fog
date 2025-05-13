package hu.u_szeged.inf.fog.simulator.distributed_ledger.find_node_strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The RandomNodeStrategy class implements the FindNodeStrategy interface.
 * It selects a random miner node from the list of registered miners.
 */
public class RandomNodeStrategy implements FindNodeStrategy {
    private static final Random RANDOM = SeedSyncer.centralRnd;

    /**
     * Finds a random miner node from the list of registered miners.
     *
     * @return a randomly selected miner node
     * @throws IllegalStateException if no nodes are registered
     */
    @Override
    public Miner findNode() {
        List<Miner> nodes = new ArrayList<>(Miner.miners.values());
        if (nodes.isEmpty()) throw new IllegalStateException("No nodes registered!");
        return nodes.get(RANDOM.nextInt(nodes.size()));
    }
}