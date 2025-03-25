package hu.u_szeged.inf.fog.simulator.distributed_ledger.find_node_strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.BlockValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RandomNodeStrategy implements FindNodeStrategy{

    @Override
    public BlockValidator findNode() {
        List<BlockValidator> nodes = new ArrayList<>(BlockValidator.validators.values());
        if (nodes.isEmpty()) throw new IllegalStateException("No nodes registered!");
        return nodes.get(SeedSyncer.centralRnd.nextInt(nodes.size()));
    }
}
